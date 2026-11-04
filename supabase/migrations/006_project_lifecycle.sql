-- Apply after 005. Preserve the atomic confirmation wrapper and existing data.
begin;
do $$ begin
  if to_regprocedure('public.sync_confirm_capture_v1(jsonb,jsonb)') is null then
    raise exception 'Apply migration 005 first';
  end if;
end $$;

alter table public.projects add column if not exists completed_at timestamptz;
-- NULL is reserved for records written before lifecycle metadata existed.
-- Their suggestion is derived from steps; no historical record/version is rewritten.
alter table public.projects add column if not exists completion_ready boolean;
comment on column public.projects.completed_at is 'Explicit project completion; NULL means active.';
comment on column public.projects.completion_ready is 'Completion suggestion after actually completing steps, not deleting the last unfinished step. NULL is legacy data.';

create or replace view public.sbros_sync_projection_v3 with(security_invoker=true) as
select t.user_id,t.id,'capture'::text as kind,
       jsonb_build_object('text',t.raw_text,
       'status',t.status,
       'source',t.source,
       'confirmedAt',t.confirmed_at,
       'analysisCommit',t.analysis_commit,
       'analysisModel',t.analysis_model,
       'sourceExcerpt',t.source_excerpt) as payload,
       null::uuid as project_id,t.parent_capture_id as capture_id,
       t.created_at,t.updated_at,t.deleted_at,t.version,t.change_id
from public.captures t
union all
select t.user_id,t.id,'project'::text as kind,
       jsonb_build_object('title',t.title,
       'area',t.area,
       'outcome',t.outcome,
       'completedAt',t.completed_at,
       'completionReady',coalesce(t.completion_ready,
         exists(select 1 from public.tasks a where a.user_id=t.user_id and a.project_id=t.id and a.deleted_at is null)
         and not exists(select 1 from public.tasks a where a.user_id=t.user_id and a.project_id=t.id and a.deleted_at is null and a.status<>'done')),
       'analysisModel',t.analysis_model,
       'sourceExcerpt',t.source_excerpt) as payload,
       null::uuid as project_id,t.capture_id as capture_id,
       t.created_at,t.updated_at,t.deleted_at,t.version,t.change_id
from public.projects t
union all
select t.user_id,t.id,'action'::text as kind,
       jsonb_build_object('title',t.title,
       'area',t.area,
       'energy',t.energy,
       'hint',t.hint,
       'status',t.status,
       'dueDate',to_char(t.due_date,'YYYY-MM-DD'),
       'completedAt',t.completed_at,
       'analysisModel',t.analysis_model,
       'sourceExcerpt',t.source_excerpt) as payload,
       t.project_id as project_id,t.capture_id as capture_id,
       t.created_at,t.updated_at,t.deleted_at,t.version,t.change_id
from public.tasks t
union all
select t.user_id,t.id,'thought'::text as kind,
       jsonb_build_object('title',t.title,
       'body',t.body,
       'idea',(t.thought_type='idea'),
       'time',coalesce(to_char(t.entry_time,'HH24:MI'),''),
       'favorite',t.is_favorite,
       'sourceText',t.source_text,
       'analysisModel',t.analysis_model,
       'sourceExcerpt',t.source_excerpt) as payload,
       null::uuid as project_id,t.capture_id as capture_id,
       t.created_at,t.updated_at,t.deleted_at,t.version,t.change_id
from public.thoughts t
union all
select t.user_id,t.id,'event'::text as kind,
       jsonb_build_object('title',t.title,
       'body',t.description,
       'date',to_char(t.event_date,'YYYY-MM-DD'),
       'time',coalesce(to_char(t.event_time,'HH24:MI'),''),
       'analysisModel',t.analysis_model,
       'sourceExcerpt',t.source_excerpt) as payload,
       null::uuid as project_id,t.capture_id as capture_id,
       t.created_at,t.updated_at,t.deleted_at,t.version,t.change_id
from public.events t
union all
select t.user_id,t.id,'reference'::text as kind,
       jsonb_build_object('title',t.title,
       'body',t.body,
       'analysisModel',t.analysis_model,
       'sourceExcerpt',t.source_excerpt) as payload,
       null::uuid as project_id,t.capture_id as capture_id,
       t.created_at,t.updated_at,t.deleted_at,t.version,t.change_id
from public.materials t
union all
select t.user_id,t.id,'analysis_run'::text as kind,
       jsonb_build_object('model',t.model,
       'status',t.status,
       'finishedAt',t.finished_at,
       'proposalCount',t.proposal_count,
       'errorCode',t.error_code) as payload,
       null::uuid as project_id,t.capture_id as capture_id,
       t.created_at,t.updated_at,t.deleted_at,t.version,t.change_id
from public.analysis_runs t;

create or replace function public.sbros_sync_push_records_v3(p_changes jsonb) returns jsonb
language plpgsql security definer set search_path='' as $$
declare
  v_user uuid:=auth.uid(); v_item jsonb; v_body jsonb; v_existing record;
  v_id uuid; v_kind text; v_base bigint; v_change uuid; v_project uuid; v_capture uuid;
  v_created timestamptz; v_updated timestamptz; v_deleted timestamptz; v_version bigint;
  v_result jsonb:='[]'::jsonb; v_record jsonb;
begin
  if v_user is null then raise exception 'not_authenticated'; end if;
  if jsonb_typeof(p_changes) is distinct from 'array' then raise exception 'invalid_batch'; end if;
  if jsonb_array_length(p_changes) not between 1 and 100 or octet_length(p_changes::text)>1048576 then raise exception 'invalid_batch'; end if;
  if (select count(distinct x->>'id') from jsonb_array_elements(p_changes) x)<>jsonb_array_length(p_changes) then raise exception 'duplicate_or_missing_id'; end if;
  -- Serialise all inserts/updates for this owner, including first-time UUIDs across tables.
  perform pg_advisory_xact_lock(hashtextextended(v_user::text,0));
  for v_item in select value from jsonb_array_elements(p_changes) loop
    v_id:=(v_item->>'id')::uuid;v_kind:=v_item->>'kind';v_body:=v_item->'payload';v_base:=(v_item->>'version')::bigint;v_change:=(v_item->>'change_id')::uuid;
    v_project:=nullif(v_item->>'project_id','')::uuid;v_capture:=nullif(v_item->>'capture_id','')::uuid;
    v_created:=(v_item->>'created_at')::timestamptz;v_updated:=(v_item->>'updated_at')::timestamptz;v_deleted:=nullif(v_item->>'deleted_at','')::timestamptz;
    if v_id is null or v_change is null or v_created is null or v_updated is null or v_base is null or v_base<0
       or v_kind is null or v_kind not in ('capture','project','action','thought','event','reference','analysis_run')
       or jsonb_typeof(v_body) is distinct from 'object' or octet_length(v_body::text)>262144 then raise exception 'invalid_record'; end if;
    if v_kind<>'action' and v_project is not null then raise exception 'unexpected_project_link'; end if;
    if v_capture=v_id or v_project=v_id then raise exception 'self_reference'; end if;
    if v_kind in ('project','action','thought','event','reference') and jsonb_typeof(v_body->'title') is distinct from 'string' then raise exception 'invalid_title'; end if;
    if v_kind='capture' and jsonb_typeof(v_body->'text') is distinct from 'string' then raise exception 'invalid_capture'; end if;
    if v_kind='thought' and (jsonb_typeof(v_body->'body') is distinct from 'string' or jsonb_typeof(v_body->'idea') is distinct from 'boolean') then raise exception 'invalid_thought'; end if;
    if v_kind='action' and (jsonb_typeof(v_body->'energy') is distinct from 'number' or (v_body->>'energy') not in ('0','1','2')) then raise exception 'invalid_energy'; end if;
    if v_kind='analysis_run' and v_capture is null then raise exception 'missing_analysis_source'; end if;
    select * into v_existing from public.sbros_sync_projection_v3 where user_id=v_user and id=v_id;
    if found then
      if v_existing.kind<>v_kind then raise exception 'immutable_kind'; end if;
      if v_existing.change_id=v_change then
        v_result:=v_result||jsonb_build_array(jsonb_build_object('status','ok','record',to_jsonb(v_existing)-'user_id'));continue;
      end if;
      if v_existing.version<>v_base then
        v_result:=v_result||jsonb_build_array(jsonb_build_object('status','conflict','record',to_jsonb(v_existing)-'user_id'));continue;
      end if;
      if v_existing.created_at<>v_created then raise exception 'immutable_created_at'; end if;
      if v_kind='capture' and v_existing.payload->>'text' is distinct from v_body->>'text' then raise exception 'immutable_original'; end if;
      v_version:=v_existing.version+1;
    else
      if v_base<>0 then raise exception 'missing_base_record'; end if;
      v_version:=1;
    end if;
    case v_kind


    when 'capture' then
      insert into public.captures (user_id,id,created_at,updated_at,deleted_at,version,change_id,raw_text,status,source,confirmed_at,analysis_commit,analysis_model,source_excerpt,parent_capture_id)
      values (v_user,
              v_id,
              v_created,
              v_updated,
              v_deleted,
              v_version,
              v_change,
              coalesce(v_body->>'text',''),
              coalesce(v_body->>'status','inbox'),
              coalesce(v_body->>'source','text'),
              nullif(v_body->>'confirmedAt','')::timestamptz,
              nullif(v_body->>'analysisCommit','')::uuid,
              nullif(v_body->>'analysisModel',''),
              coalesce(v_body->>'sourceExcerpt',''),
              v_capture)
      on conflict(user_id,id) do update set
        updated_at=excluded.updated_at,
        deleted_at=excluded.deleted_at,
        version=excluded.version,
        change_id=excluded.change_id,
        raw_text=excluded.raw_text,
        status=excluded.status,
        source=excluded.source,
        confirmed_at=excluded.confirmed_at,
        analysis_commit=excluded.analysis_commit,
        analysis_model=excluded.analysis_model,
        source_excerpt=excluded.source_excerpt,
        parent_capture_id=excluded.parent_capture_id;

    when 'project' then
      insert into public.projects (user_id,id,created_at,updated_at,deleted_at,version,change_id,title,area,outcome,analysis_model,source_excerpt,capture_id,completed_at,completion_ready)
      values (v_user,
              v_id,
              v_created,
              v_updated,
              v_deleted,
              v_version,
              v_change,
              coalesce(v_body->>'title',''),
              coalesce(v_body->>'area','Личное'),
              coalesce(v_body->>'outcome',''),
              nullif(v_body->>'analysisModel',''),
              coalesce(v_body->>'sourceExcerpt',''),
              v_capture,
              nullif(v_body->>'completedAt','')::timestamptz,
              (v_body->>'completionReady')::boolean)
      on conflict(user_id,id) do update set
        updated_at=excluded.updated_at,
        deleted_at=excluded.deleted_at,
        version=excluded.version,
        change_id=excluded.change_id,
        title=excluded.title,
        area=excluded.area,
        outcome=excluded.outcome,
        analysis_model=excluded.analysis_model,
        source_excerpt=excluded.source_excerpt,
        capture_id=excluded.capture_id,
        completed_at=case when v_body ? 'completedAt' then excluded.completed_at else projects.completed_at end,
        completion_ready=case when v_body ? 'completionReady' then excluded.completion_ready else projects.completion_ready end;

    when 'action' then
      insert into public.tasks (user_id,id,created_at,updated_at,deleted_at,version,change_id,title,area,energy,hint,status,due_date,completed_at,analysis_model,source_excerpt,capture_id,project_id)
      values (v_user,
              v_id,
              v_created,
              v_updated,
              v_deleted,
              v_version,
              v_change,
              coalesce(v_body->>'title',''),
              coalesce(v_body->>'area','Личное'),
              coalesce((v_body->>'energy')::smallint,0),
              coalesce(v_body->>'hint',''),
              coalesce(v_body->>'status',''),
              nullif(v_body->>'dueDate','')::date,
              nullif(v_body->>'completedAt','')::timestamptz,
              nullif(v_body->>'analysisModel',''),
              coalesce(v_body->>'sourceExcerpt',''),
              v_capture,
              v_project)
      on conflict(user_id,id) do update set
        updated_at=excluded.updated_at,
        deleted_at=excluded.deleted_at,
        version=excluded.version,
        change_id=excluded.change_id,
        title=excluded.title,
        area=excluded.area,
        energy=excluded.energy,
        hint=excluded.hint,
        status=excluded.status,
        due_date=excluded.due_date,
        completed_at=excluded.completed_at,
        analysis_model=excluded.analysis_model,
        source_excerpt=excluded.source_excerpt,
        capture_id=excluded.capture_id,
        project_id=excluded.project_id;

    when 'thought' then
      insert into public.thoughts (user_id,id,created_at,updated_at,deleted_at,version,change_id,title,body,thought_type,entry_time,is_favorite,source_text,analysis_model,source_excerpt,capture_id)
      values (v_user,
              v_id,
              v_created,
              v_updated,
              v_deleted,
              v_version,
              v_change,
              coalesce(v_body->>'title',''),
              coalesce(v_body->>'body',''),
              case when coalesce((v_body->>'idea')::boolean,false) then 'idea' else 'diary' end,
              nullif(v_body->>'time','')::time,
              coalesce((v_body->>'favorite')::boolean,false),
              nullif(v_body->>'sourceText',''),
              nullif(v_body->>'analysisModel',''),
              coalesce(v_body->>'sourceExcerpt',''),
              v_capture)
      on conflict(user_id,id) do update set
        updated_at=excluded.updated_at,
        deleted_at=excluded.deleted_at,
        version=excluded.version,
        change_id=excluded.change_id,
        title=excluded.title,
        body=excluded.body,
        thought_type=excluded.thought_type,
        entry_time=excluded.entry_time,
        is_favorite=excluded.is_favorite,
        source_text=excluded.source_text,
        analysis_model=excluded.analysis_model,
        source_excerpt=excluded.source_excerpt,
        capture_id=excluded.capture_id;

    when 'event' then
      insert into public.events (user_id,id,created_at,updated_at,deleted_at,version,change_id,title,description,event_date,event_time,analysis_model,source_excerpt,capture_id)
      values (v_user,
              v_id,
              v_created,
              v_updated,
              v_deleted,
              v_version,
              v_change,
              coalesce(v_body->>'title',''),
              coalesce(v_body->>'body',''),
              nullif(v_body->>'date','')::date,
              nullif(v_body->>'time','')::time,
              nullif(v_body->>'analysisModel',''),
              coalesce(v_body->>'sourceExcerpt',''),
              v_capture)
      on conflict(user_id,id) do update set
        updated_at=excluded.updated_at,
        deleted_at=excluded.deleted_at,
        version=excluded.version,
        change_id=excluded.change_id,
        title=excluded.title,
        description=excluded.description,
        event_date=excluded.event_date,
        event_time=excluded.event_time,
        analysis_model=excluded.analysis_model,
        source_excerpt=excluded.source_excerpt,
        capture_id=excluded.capture_id;

    when 'reference' then
      insert into public.materials (user_id,id,created_at,updated_at,deleted_at,version,change_id,title,body,analysis_model,source_excerpt,capture_id)
      values (v_user,
              v_id,
              v_created,
              v_updated,
              v_deleted,
              v_version,
              v_change,
              coalesce(v_body->>'title',''),
              coalesce(v_body->>'body',''),
              nullif(v_body->>'analysisModel',''),
              coalesce(v_body->>'sourceExcerpt',''),
              v_capture)
      on conflict(user_id,id) do update set
        updated_at=excluded.updated_at,
        deleted_at=excluded.deleted_at,
        version=excluded.version,
        change_id=excluded.change_id,
        title=excluded.title,
        body=excluded.body,
        analysis_model=excluded.analysis_model,
        source_excerpt=excluded.source_excerpt,
        capture_id=excluded.capture_id;

    when 'analysis_run' then
      insert into public.analysis_runs (user_id,id,created_at,updated_at,deleted_at,version,change_id,model,status,finished_at,proposal_count,error_code,capture_id)
      values (v_user,
              v_id,
              v_created,
              v_updated,
              v_deleted,
              v_version,
              v_change,
              coalesce(v_body->>'model',''),
              coalesce(v_body->>'status',''),
              nullif(v_body->>'finishedAt','')::timestamptz,
              coalesce((v_body->>'proposalCount')::smallint,0),
              nullif(v_body->>'errorCode',''),
              v_capture)
      on conflict(user_id,id) do update set
        updated_at=excluded.updated_at,
        deleted_at=excluded.deleted_at,
        version=excluded.version,
        change_id=excluded.change_id,
        model=excluded.model,
        status=excluded.status,
        finished_at=excluded.finished_at,
        proposal_count=excluded.proposal_count,
        error_code=excluded.error_code,
        capture_id=excluded.capture_id;


    end case;
    select to_jsonb(r)-'user_id' into v_record from public.sbros_sync_projection_v3 r where r.user_id=v_user and r.id=v_id;
    v_result:=v_result||jsonb_build_array(jsonb_build_object('status','ok','record',v_record));
  end loop;
  return v_result;
end $$;
revoke all on function public.sbros_sync_push_records_v3(jsonb) from public,anon,authenticated;
-- The public v3 wrapper and sync_confirm_capture_v1 from migration 005 delegate here.
create or replace function public.sync_schema_version() returns integer
language sql stable security invoker set search_path='' as $$ select 6; $$;
revoke all on function public.sync_schema_version() from public,anon,authenticated;
grant execute on function public.sync_schema_version() to authenticated;
notify pgrst, 'reload schema';
commit;
