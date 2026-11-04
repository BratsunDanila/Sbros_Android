-- Apply after 004. Existing records and accounts are preserved.
-- One confirmed capture and all its new results are one transaction.
begin;

do $$ begin
  if to_regprocedure('public.sbros_sync_push_records_v3(jsonb)') is null then
    alter function public.sync_push_v3(jsonb) rename to sbros_sync_push_records_v3;
  end if;
end $$;
revoke all on function public.sbros_sync_push_records_v3(jsonb) from public,anon,authenticated;

-- Ordinary edits keep the v3 contract. First confirmation must use the atomic RPC.
create or replace function public.sync_push_v3(p_changes jsonb) returns jsonb
language plpgsql security definer set search_path='' as $$
declare v_user uuid:=auth.uid(); v_item jsonb; v_commit uuid;
begin
  if v_user is null then raise exception 'not_authenticated'; end if;
  if jsonb_typeof(p_changes) is distinct from 'array'
     or jsonb_array_length(p_changes) not between 1 and 100
     or octet_length(p_changes::text)>1048576 then raise exception 'invalid_batch'; end if;
  perform pg_advisory_xact_lock(hashtextextended(v_user::text,0));
  for v_item in select value from jsonb_array_elements(p_changes) loop
    if v_item->>'kind'='capture' and v_item->'payload'->>'status'='committed'
       and nullif(v_item->'payload'->>'analysisCommit','') is not null then
      select analysis_commit into v_commit from public.captures
        where user_id=v_user and id=(v_item->>'id')::uuid;
      if v_commit is distinct from (v_item->'payload'->>'analysisCommit')::uuid then
        raise exception 'analysis_confirmation_requires_atomic_rpc';
      end if;
    end if;
    if coalesce((v_item->>'version')::bigint,0)=0
       and v_item->>'kind' in ('capture','project','action','thought','event','reference')
       and nullif(v_item->>'capture_id','') is not null
       and nullif(v_item->'payload'->>'analysisModel','') is not null then
      raise exception 'analysis_results_require_atomic_rpc';
    end if;
  end loop;
  return public.sbros_sync_push_records_v3(p_changes);
end $$;
revoke all on function public.sync_push_v3(jsonb) from public,anon,authenticated;
grant execute on function public.sync_push_v3(jsonb) to authenticated;

create or replace function public.sync_confirm_capture_v1(p_capture jsonb,p_results jsonb) returns jsonb
language plpgsql security definer set search_path='' as $$
declare
  v_user uuid:=auth.uid(); v_id uuid; v_commit uuid; v_existing record;
  v_item jsonb; v_ack jsonb; v_records jsonb:='[]'::jsonb;
begin
  if v_user is null then raise exception 'not_authenticated'; end if;
  if jsonb_typeof(p_capture) is distinct from 'object'
     or p_capture->>'kind' is distinct from 'capture'
     or p_capture->'payload'->>'status' is distinct from 'committed'
     or jsonb_typeof(p_results) is distinct from 'array'
     or jsonb_array_length(p_results)>40
     or octet_length(p_capture::text)+octet_length(p_results::text)>2097152 then
    raise exception 'invalid_analysis_group';
  end if;
  v_id:=(p_capture->>'id')::uuid;
  v_commit:=nullif(p_capture->'payload'->>'analysisCommit','')::uuid;
  if v_id is null or v_commit is null then raise exception 'missing_analysis_identity'; end if;
  if (select count(distinct x->>'id') from jsonb_array_elements(p_results) x)<>jsonb_array_length(p_results) then
    raise exception 'duplicate_or_missing_id';
  end if;
  perform pg_advisory_xact_lock(hashtextextended(v_user::text,0));
  select * into v_existing from public.sbros_sync_projection_v3 where user_id=v_user and id=v_id;
  if found then
    if v_existing.kind<>'capture' then raise exception 'immutable_kind'; end if;
    if (v_existing.payload->>'status'='committed'
        and nullif(v_existing.payload->>'analysisCommit','') is not null
        and (v_existing.payload->>'analysisCommit')::uuid<>v_commit)
       or (v_existing.change_id<>(p_capture->>'change_id')::uuid
           and v_existing.version<>(p_capture->>'version')::bigint) then
      return jsonb_build_object('status','conflict','record',to_jsonb(v_existing)-'user_id');
    end if;
  end if;
  v_ack:=public.sbros_sync_push_records_v3(jsonb_build_array(p_capture));
  if v_ack->0->>'status'<>'ok' then raise exception 'analysis_source_conflict'; end if;
  v_records:=v_records||v_ack;
  for v_item in select value from jsonb_array_elements(p_results) loop
    if v_item->>'kind' not in ('capture','project','action','thought','event','reference')
       or (v_item->>'capture_id')::uuid is distinct from v_id
       or (v_item->>'id')::uuid=v_id
       or (v_item->>'version')::bigint is distinct from 0
       or nullif(v_item->'payload'->>'analysisModel','') is null then
      raise exception 'invalid_analysis_result';
    end if;
    v_ack:=public.sbros_sync_push_records_v3(jsonb_build_array(v_item));
    -- Raising rolls back the source and every preceding result as well.
    if v_ack->0->>'status'<>'ok' then raise exception 'analysis_result_conflict'; end if;
    v_records:=v_records||v_ack;
  end loop;
  return jsonb_build_object('status','ok','records',v_records);
end $$;
revoke all on function public.sync_confirm_capture_v1(jsonb,jsonb) from public,anon,authenticated;
grant execute on function public.sync_confirm_capture_v1(jsonb,jsonb) to authenticated;
notify pgrst, 'reload schema';
commit;
