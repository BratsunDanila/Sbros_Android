-- Apply AFTER 001_core.sql in a TEST Supabase project.
-- Existing tables and records are preserved. New clients use one versioned sync protocol.
begin;

create table public.sync_records (
  user_id uuid not null references auth.users(id) on delete cascade,
  id uuid not null,
  kind text not null check(kind in ('capture','project','action','thought','event','reference','review')),
  payload jsonb not null check(jsonb_typeof(payload)='object' and octet_length(payload::text)<=262144),
  project_id uuid,
  capture_id uuid,
  created_at timestamptz not null,
  updated_at timestamptz not null,
  deleted_at timestamptz,
  version bigint not null default 1 check(version>0),
  change_id uuid not null,
  primary key(user_id,id),
  foreign key(user_id,project_id) references public.sync_records(user_id,id) deferrable initially deferred,
  foreign key(user_id,capture_id) references public.sync_records(user_id,id) deferrable initially deferred
);
alter table public.sync_records enable row level security;
revoke all on public.sync_records from public,anon,authenticated;
grant select on public.sync_records to authenticated;
create policy sync_records_read_own on public.sync_records for select to authenticated using(user_id=(select auth.uid()));

-- One-time lossless projection of prepared stage-1 cloud tables. IDs/dates/references survive.
insert into public.sync_records(user_id,id,kind,payload,capture_id,created_at,updated_at,change_id)
select user_id,id,'capture',jsonb_build_object('text',raw_text,'status',status,'confirmedAt',confirmed_at,'source',source),parent_capture_id,created_at,coalesce(confirmed_at,created_at),gen_random_uuid()
from public.captures;
insert into public.sync_records(user_id,id,kind,payload,project_id,capture_id,created_at,updated_at,change_id)
select user_id,id,kind,
  case kind
    when 'action' then jsonb_build_object('title',title,'area',coalesce(area,'Личное'),'energy',case energy when 'medium' then 1 when 'hard' then 2 else 0 end,'hint',coalesce(body,''),'status',status,'completedAt',completed_at)
    when 'project' then jsonb_build_object('title',title,'area',coalesce(area,'Личное'),'outcome',coalesce(body,''))
    when 'thought' then jsonb_build_object('title',title,'body',coalesce(body,''),'idea',thought_type='idea','time',to_char(created_at at time zone 'UTC','HH24:MI'),'favorite',is_favorite,'sourceText',(select c.raw_text from public.captures c where c.id=i.capture_id and c.user_id=i.user_id))
    when 'event' then jsonb_build_object('title',title,'date',event_date,'time',coalesce(to_char(event_time,'HH24:MI'),''))
    else jsonb_build_object('title',title,'body',coalesce(body,''))
  end,project_id,capture_id,created_at,updated_at,gen_random_uuid()
from public.items i;
insert into public.sync_records(user_id,id,kind,payload,created_at,updated_at,change_id)
select user_id,id,'review',jsonb_build_object('date',to_char(completed_at at time zone 'UTC','YYYY-MM-DD'),'checklist',checklist),completed_at,completed_at,gen_random_uuid() from public.weekly_reviews;

-- Legacy API becomes read-only; prevents two independent writable stores.
revoke insert,update,delete on public.captures,public.items,public.weekly_reviews from authenticated;
revoke execute on function public.confirm_capture(uuid,jsonb) from authenticated;

create function public.sync_push(p_changes jsonb) returns jsonb
language plpgsql security definer set search_path='' as $$
declare
  u uuid:=auth.uid(); p jsonb; r public.sync_records%rowtype;
  rid uuid; kind text; body jsonb; base bigint; mutation uuid;
  project uuid; capture uuid; created timestamptz; updated timestamptz; deleted timestamptz;
  output jsonb:='[]'::jsonb;
begin
  if u is null then raise exception 'not_authenticated'; end if;
  if jsonb_typeof(p_changes) is distinct from 'array' then raise exception 'invalid_batch'; end if;
  if jsonb_array_length(p_changes) not between 1 and 100 or octet_length(p_changes::text)>1048576 then raise exception 'invalid_batch'; end if;
  -- Serialises writes per user, including absent rows, so concurrent first inserts are deterministic.
  perform pg_advisory_xact_lock(hashtextextended(u::text,0));
  for p in select value from jsonb_array_elements(p_changes) loop
    rid:=(p->>'id')::uuid; kind:=p->>'kind'; body:=p->'payload';base:=(p->>'version')::bigint;mutation:=(p->>'change_id')::uuid;
    project:=nullif(p->>'project_id','')::uuid;capture:=nullif(p->>'capture_id','')::uuid;
    created:=(p->>'created_at')::timestamptz;updated:=(p->>'updated_at')::timestamptz;deleted:=nullif(p->>'deleted_at','')::timestamptz;
    if rid is null or mutation is null or created is null or updated is null or base is null or base<0 or kind is null
       or kind not in ('capture','project','action','thought','event','reference','review') or jsonb_typeof(body) is distinct from 'object' then raise exception 'invalid_record'; end if;
    if kind='capture' then
      if jsonb_typeof(body->'text') is distinct from 'string' or char_length(btrim(body->>'text')) not between 1 and 12000 or coalesce(body->>'status','') not in ('inbox','committed') then raise exception 'invalid_capture'; end if;
    elsif kind='review' then
      perform (body->>'date')::date;
      if nullif(body->>'date','') is null then raise exception 'invalid_review'; end if;
    else
      if jsonb_typeof(body->'title') is distinct from 'string' or char_length(btrim(body->>'title')) not between 1 and 500 then raise exception 'invalid_title'; end if;
      if kind='action' and (coalesce(body->>'status','') not in ('next','waiting','someday','done') or jsonb_typeof(body->'energy') is distinct from 'number') then raise exception 'invalid_action'; end if;
      if kind='thought' and (jsonb_typeof(body->'body') is distinct from 'string' or jsonb_typeof(body->'idea') is distinct from 'boolean') then raise exception 'invalid_thought'; end if;
      if kind='event' then
        if nullif(body->>'date','') is null then raise exception 'invalid_event'; end if;
        perform (body->>'date')::date;
        if nullif(body->>'time','') is not null then perform (body->>'time')::time; end if;
      end if;
    end if;
    select * into r from public.sync_records where user_id=u and id=rid for update;
    if found then
      if r.kind<>kind then raise exception 'immutable_kind'; end if;
      if r.change_id=mutation then output:=output||jsonb_build_array(jsonb_build_object('status','ok','record',to_jsonb(r)-'user_id'));continue; end if;
      if r.version<>base then output:=output||jsonb_build_array(jsonb_build_object('status','conflict','record',to_jsonb(r)-'user_id'));continue; end if;
      if kind='capture' and r.payload->>'text' is distinct from body->>'text' then raise exception 'immutable_original'; end if;
      update public.sync_records set payload=body,project_id=project,capture_id=capture,updated_at=updated,deleted_at=deleted,version=version+1,change_id=mutation
        where user_id=u and id=rid returning * into r;
    else
      if base<>0 then raise exception 'missing_base_record'; end if;
      insert into public.sync_records(user_id,id,kind,payload,project_id,capture_id,created_at,updated_at,deleted_at,change_id)
        values(u,rid,kind,body,project,capture,created,updated,deleted,mutation) returning * into r;
    end if;
    output:=output||jsonb_build_array(jsonb_build_object('status','ok','record',to_jsonb(r)-'user_id'));
  end loop;
  if exists(select 1 from public.sync_records a join public.sync_records b on b.user_id=a.user_id and b.id=a.project_id where a.user_id=u and b.kind<>'project')
     or exists(select 1 from public.sync_records a join public.sync_records b on b.user_id=a.user_id and b.id=a.capture_id where a.user_id=u and b.kind<>'capture') then raise exception 'invalid_reference_kind'; end if;
  return output;
end $$;
revoke all on function public.sync_push(jsonb) from public,anon;
grant execute on function public.sync_push(jsonb) to authenticated;

create function public.sync_pull(p_after uuid default null,p_limit integer default 200) returns jsonb
language sql stable security invoker set search_path='' as $$
  select coalesce(jsonb_agg(to_jsonb(r)-'user_id' order by r.id),'[]'::jsonb) from (
    select * from public.sync_records where user_id=auth.uid() and (p_after is null or id>p_after) order by id limit least(greatest(p_limit,1),200)
  ) r
$$;
revoke all on function public.sync_pull(uuid,integer) from public,anon;
grant execute on function public.sync_pull(uuid,integer) to authenticated;

create function public.create_profile_for_auth_user() returns trigger
language plpgsql security definer set search_path='' as $$
begin
  insert into public.profiles(user_id,display_name) values(new.id,left(coalesce(new.raw_user_meta_data->>'display_name',''),120)) on conflict(user_id) do nothing;
  return new;
end $$;
revoke all on function public.create_profile_for_auth_user() from public,anon,authenticated;
create trigger sbros_profile_after_signup after insert on auth.users for each row execute function public.create_profile_for_auth_user();
insert into public.profiles(user_id,display_name) select id,left(coalesce(raw_user_meta_data->>'display_name',''),120) from auth.users on conflict(user_id) do nothing;
commit;
