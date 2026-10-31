-- Сброс: предметная реляционная схема и протокол синхронизации v3.
-- Один запуск ПОСЛЕ 001_core.sql и 002_local_first_sync.sql, в SQL Editor своего проекта.
-- Записи sync_records переносятся, ID/версии сохраняются. Пользователи Auth не удаляются.
-- Старые таблицы приложения удаляются ТОЛЬКО после переноса и проверки идентификаторов.
-- Скрипт не применяет изменения к auth.* и не требует service_role в приложении.
begin;

do $$ begin
  if current_setting('server_version_num')::int < 150000 then
    raise exception 'PostgreSQL 15+ is required for security_invoker views';
  end if;
  if to_regclass('public.sync_records') is null or to_regclass('public.profiles') is null then
    raise exception 'Apply migrations 001 and 002 first; migration 003 must not be run twice';
  end if;
end $$;

lock table public.sync_records in access exclusive mode;
drop function if exists public.sync_push(jsonb);
drop function if exists public.sync_pull(uuid,integer);
drop function if exists public.confirm_capture(uuid,jsonb);
alter table if exists public.captures rename to sbros_legacy_captures;
alter table if exists public.weekly_reviews rename to sbros_legacy_weekly_reviews;
-- Preferences are device-local, so profiles contains actual account data only.
alter table public.profiles drop column if exists theme, drop column if exists palette;
comment on table public.profiles is 'Профиль пользователя Supabase Auth; настройки оформления хранятся на устройстве.';


create table public.captures (
  user_id uuid not null references auth.users(id) on delete cascade,
  id uuid not null default pg_catalog.gen_random_uuid(),
  created_at timestamptz not null default pg_catalog.now(),
  updated_at timestamptz not null default pg_catalog.now(),
  deleted_at timestamptz,
  version bigint not null default 1 check(version>0),
  change_id uuid not null default pg_catalog.gen_random_uuid(),
  raw_text text not null check(char_length(btrim(raw_text)) between 1 and 12000),
  status text not null check(status in ('inbox','committed')),
  source text not null default 'text' check(char_length(source) between 1 and 40),
  confirmed_at timestamptz,
  analysis_commit uuid,
  analysis_model text check(char_length(analysis_model)<=256),
  source_excerpt text not null default '' check(char_length(source_excerpt)<=12000),
  parent_capture_id uuid,
  constraint captures_v3_pkey primary key(user_id,id),
  constraint captures_v3_capture_fk foreign key(user_id,parent_capture_id) references public.captures(user_id,id) deferrable initially deferred
);
comment on table public.captures is 'Исходные тексты сбросов; оригинал неизменяем.';


alter table public.captures enable row level security;
revoke all on public.captures from public,anon,authenticated;
grant select on public.captures to authenticated;
create policy captures_read_own on public.captures for select to authenticated using(user_id=(select auth.uid()));


create table public.projects (
  user_id uuid not null references auth.users(id) on delete cascade,
  id uuid not null default pg_catalog.gen_random_uuid(),
  created_at timestamptz not null default pg_catalog.now(),
  updated_at timestamptz not null default pg_catalog.now(),
  deleted_at timestamptz,
  version bigint not null default 1 check(version>0),
  change_id uuid not null default pg_catalog.gen_random_uuid(),
  title text not null check(char_length(btrim(title)) between 1 and 500),
  area text not null default 'Личное' check(char_length(area)<=80),
  outcome text not null default '' check(char_length(outcome)<=240000),
  analysis_model text check(char_length(analysis_model)<=256),
  source_excerpt text not null default '' check(char_length(source_excerpt)<=12000),
  capture_id uuid,
  constraint projects_v3_pkey primary key(user_id,id),
  constraint projects_v3_capture_fk foreign key(user_id,capture_id) references public.captures(user_id,id) deferrable initially deferred
);
comment on table public.projects is 'Проекты и желаемые результаты; шаги находятся в tasks.';


alter table public.projects enable row level security;
revoke all on public.projects from public,anon,authenticated;
grant select on public.projects to authenticated;
create policy projects_read_own on public.projects for select to authenticated using(user_id=(select auth.uid()));


create table public.tasks (
  user_id uuid not null references auth.users(id) on delete cascade,
  id uuid not null default pg_catalog.gen_random_uuid(),
  created_at timestamptz not null default pg_catalog.now(),
  updated_at timestamptz not null default pg_catalog.now(),
  deleted_at timestamptz,
  version bigint not null default 1 check(version>0),
  change_id uuid not null default pg_catalog.gen_random_uuid(),
  title text not null check(char_length(btrim(title)) between 1 and 500),
  area text not null default 'Личное' check(char_length(area)<=80),
  energy smallint not null default 0 check(energy between 0 and 2),
  hint text not null default '' check(char_length(hint)<=240000),
  status text not null check(status in ('next','waiting','someday','done')),
  due_date date,
  completed_at timestamptz,
  analysis_model text check(char_length(analysis_model)<=256),
  source_excerpt text not null default '' check(char_length(source_excerpt)<=12000),
  capture_id uuid,
  project_id uuid,
  constraint tasks_v3_pkey primary key(user_id,id),
  constraint tasks_v3_capture_fk foreign key(user_id,capture_id) references public.captures(user_id,id) deferrable initially deferred,
  constraint tasks_v3_project_fk foreign key(user_id,project_id) references public.projects(user_id,id) deferrable initially deferred
);
comment on table public.tasks is 'Дела и шаги проектов. Срок не создаёт отдельного события.';


alter table public.tasks enable row level security;
revoke all on public.tasks from public,anon,authenticated;
grant select on public.tasks to authenticated;
create policy tasks_read_own on public.tasks for select to authenticated using(user_id=(select auth.uid()));


create table public.thoughts (
  user_id uuid not null references auth.users(id) on delete cascade,
  id uuid not null default pg_catalog.gen_random_uuid(),
  created_at timestamptz not null default pg_catalog.now(),
  updated_at timestamptz not null default pg_catalog.now(),
  deleted_at timestamptz,
  version bigint not null default 1 check(version>0),
  change_id uuid not null default pg_catalog.gen_random_uuid(),
  title text not null check(char_length(btrim(title)) between 1 and 500),
  body text not null default '' check(char_length(body)<=240000),
  thought_type text not null check(thought_type in ('idea','diary')),
  entry_time time,
  is_favorite boolean not null default false,
  source_text text check(char_length(source_text)<=12000),
  analysis_model text check(char_length(analysis_model)<=256),
  source_excerpt text not null default '' check(char_length(source_excerpt)<=12000),
  capture_id uuid,
  constraint thoughts_v3_pkey primary key(user_id,id),
  constraint thoughts_v3_capture_fk foreign key(user_id,capture_id) references public.captures(user_id,id) deferrable initially deferred
);
comment on table public.thoughts is 'Идеи и дневник без обязательного превращения в задачи.';


alter table public.thoughts enable row level security;
revoke all on public.thoughts from public,anon,authenticated;
grant select on public.thoughts to authenticated;
create policy thoughts_read_own on public.thoughts for select to authenticated using(user_id=(select auth.uid()));


create table public.events (
  user_id uuid not null references auth.users(id) on delete cascade,
  id uuid not null default pg_catalog.gen_random_uuid(),
  created_at timestamptz not null default pg_catalog.now(),
  updated_at timestamptz not null default pg_catalog.now(),
  deleted_at timestamptz,
  version bigint not null default 1 check(version>0),
  change_id uuid not null default pg_catalog.gen_random_uuid(),
  title text not null check(char_length(btrim(title)) between 1 and 500),
  description text not null default '' check(char_length(description)<=240000),
  event_date date not null,
  event_time time,
  analysis_model text check(char_length(analysis_model)<=256),
  source_excerpt text not null default '' check(char_length(source_excerpt)<=12000),
  capture_id uuid,
  constraint events_v3_pkey primary key(user_id,id),
  constraint events_v3_capture_fk foreign key(user_id,capture_id) references public.captures(user_id,id) deferrable initially deferred
);
comment on table public.events is 'События с датой и необязательным временем.';


alter table public.events enable row level security;
revoke all on public.events from public,anon,authenticated;
grant select on public.events to authenticated;
create policy events_read_own on public.events for select to authenticated using(user_id=(select auth.uid()));


create table public.materials (
  user_id uuid not null references auth.users(id) on delete cascade,
  id uuid not null default pg_catalog.gen_random_uuid(),
  created_at timestamptz not null default pg_catalog.now(),
  updated_at timestamptz not null default pg_catalog.now(),
  deleted_at timestamptz,
  version bigint not null default 1 check(version>0),
  change_id uuid not null default pg_catalog.gen_random_uuid(),
  title text not null check(char_length(btrim(title)) between 1 and 500),
  body text not null default '' check(char_length(body)<=240000),
  analysis_model text check(char_length(analysis_model)<=256),
  source_excerpt text not null default '' check(char_length(source_excerpt)<=12000),
  capture_id uuid,
  constraint materials_v3_pkey primary key(user_id,id),
  constraint materials_v3_capture_fk foreign key(user_id,capture_id) references public.captures(user_id,id) deferrable initially deferred
);
comment on table public.materials is 'Справочные материалы.';


alter table public.materials enable row level security;
revoke all on public.materials from public,anon,authenticated;
grant select on public.materials to authenticated;
create policy materials_read_own on public.materials for select to authenticated using(user_id=(select auth.uid()));


create table public.weekly_reviews (
  user_id uuid not null references auth.users(id) on delete cascade,
  id uuid not null default pg_catalog.gen_random_uuid(),
  created_at timestamptz not null default pg_catalog.now(),
  updated_at timestamptz not null default pg_catalog.now(),
  deleted_at timestamptz,
  version bigint not null default 1 check(version>0),
  change_id uuid not null default pg_catalog.gen_random_uuid(),
  review_date date not null,
  checklist jsonb not null default '{}'::jsonb check(jsonb_typeof(checklist)='object'),
  constraint weekly_reviews_v3_pkey primary key(user_id,id)
);
comment on table public.weekly_reviews is 'Завершённые недельные обзоры.';


alter table public.weekly_reviews enable row level security;
revoke all on public.weekly_reviews from public,anon,authenticated;
grant select on public.weekly_reviews to authenticated;
create policy weekly_reviews_read_own on public.weekly_reviews for select to authenticated using(user_id=(select auth.uid()));


create table public.analysis_runs (
  user_id uuid not null references auth.users(id) on delete cascade,
  id uuid not null default pg_catalog.gen_random_uuid(),
  created_at timestamptz not null default pg_catalog.now(),
  updated_at timestamptz not null default pg_catalog.now(),
  deleted_at timestamptz,
  version bigint not null default 1 check(version>0),
  change_id uuid not null default pg_catalog.gen_random_uuid(),
  model text not null check(char_length(model) between 1 and 256),
  status text not null check(status in ('succeeded','failed','cancelled')),
  finished_at timestamptz not null,
  proposal_count smallint not null default 0 check(proposal_count between 0 and 40),
  error_code text check(error_code in ('provider_error','invalid_response','storage_error','cancelled')),
  capture_id uuid,
  constraint analysis_runs_v3_pkey primary key(user_id,id),
  constraint analysis_runs_v3_capture_fk foreign key(user_id,capture_id) references public.captures(user_id,id) deferrable initially deferred
);
comment on table public.analysis_runs is 'Журнал завершённых попыток ИИ; без ключей, промптов и сырых ответов.';


alter table public.analysis_runs enable row level security;
revoke all on public.analysis_runs from public,anon,authenticated;
grant select on public.analysis_runs to authenticated;
create policy analysis_runs_read_own on public.analysis_runs for select to authenticated using(user_id=(select auth.uid()));


-- Indexes match the lists actually displayed by the application.
create index sbros_v3_captures_inbox on public.captures(user_id,status,created_at desc) where deleted_at is null;
create index sbros_v3_projects_list on public.projects(user_id,updated_at desc) where deleted_at is null;
create index sbros_v3_tasks_status_due on public.tasks(user_id,status,due_date) where deleted_at is null;
create index sbros_v3_tasks_project on public.tasks(user_id,project_id) where deleted_at is null;
create index sbros_v3_thoughts_type on public.thoughts(user_id,thought_type,created_at desc) where deleted_at is null;
create index sbros_v3_thoughts_favorite on public.thoughts(user_id,created_at desc) where is_favorite and deleted_at is null;
create index sbros_v3_events_day on public.events(user_id,event_date) where deleted_at is null;
create index sbros_v3_materials_list on public.materials(user_id,created_at desc) where deleted_at is null;
create index sbros_v3_weekly_reviews_date on public.weekly_reviews(user_id,review_date desc) where deleted_at is null;
create index sbros_v3_analysis_runs_capture on public.analysis_runs(user_id,capture_id,created_at desc) where deleted_at is null;


insert into public.captures (user_id,id,created_at,updated_at,deleted_at,version,change_id,raw_text,status,source,confirmed_at,analysis_commit,analysis_model,source_excerpt,parent_capture_id)
select s.user_id,
       s.id,
       s.created_at,
       s.updated_at,
       s.deleted_at,
       s.version,
       s.change_id,
       coalesce(s.payload->>'text',''),
       coalesce(s.payload->>'status','inbox'),
       coalesce(s.payload->>'source','text'),
       nullif(s.payload->>'confirmedAt','')::timestamptz,
       nullif(s.payload->>'analysisCommit','')::uuid,
       nullif(s.payload->>'analysisModel',''),
       coalesce(s.payload->>'sourceExcerpt',''),
       s.capture_id
from public.sync_records s where s.kind='capture';


insert into public.projects (user_id,id,created_at,updated_at,deleted_at,version,change_id,title,area,outcome,analysis_model,source_excerpt,capture_id)
select s.user_id,
       s.id,
       s.created_at,
       s.updated_at,
       s.deleted_at,
       s.version,
       s.change_id,
       coalesce(s.payload->>'title',''),
       coalesce(s.payload->>'area','Личное'),
       coalesce(s.payload->>'outcome',''),
       nullif(s.payload->>'analysisModel',''),
       coalesce(s.payload->>'sourceExcerpt',''),
       s.capture_id
from public.sync_records s where s.kind='project';


insert into public.tasks (user_id,id,created_at,updated_at,deleted_at,version,change_id,title,area,energy,hint,status,due_date,completed_at,analysis_model,source_excerpt,capture_id,project_id)
select s.user_id,
       s.id,
       s.created_at,
       s.updated_at,
       s.deleted_at,
       s.version,
       s.change_id,
       coalesce(s.payload->>'title',''),
       coalesce(s.payload->>'area','Личное'),
       coalesce((s.payload->>'energy')::smallint,0),
       coalesce(s.payload->>'hint',''),
       coalesce(s.payload->>'status',''),
       nullif(s.payload->>'dueDate','')::date,
       nullif(s.payload->>'completedAt','')::timestamptz,
       nullif(s.payload->>'analysisModel',''),
       coalesce(s.payload->>'sourceExcerpt',''),
       s.capture_id,
       s.project_id
from public.sync_records s where s.kind='action';


insert into public.thoughts (user_id,id,created_at,updated_at,deleted_at,version,change_id,title,body,thought_type,entry_time,is_favorite,source_text,analysis_model,source_excerpt,capture_id)
select s.user_id,
       s.id,
       s.created_at,
       s.updated_at,
       s.deleted_at,
       s.version,
       s.change_id,
       coalesce(s.payload->>'title',''),
       coalesce(s.payload->>'body',''),
       case when coalesce((s.payload->>'idea')::boolean,false) then 'idea' else 'diary' end,
       nullif(s.payload->>'time','')::time,
       coalesce((s.payload->>'favorite')::boolean,false),
       nullif(s.payload->>'sourceText',''),
       nullif(s.payload->>'analysisModel',''),
       coalesce(s.payload->>'sourceExcerpt',''),
       s.capture_id
from public.sync_records s where s.kind='thought';


insert into public.events (user_id,id,created_at,updated_at,deleted_at,version,change_id,title,description,event_date,event_time,analysis_model,source_excerpt,capture_id)
select s.user_id,
       s.id,
       s.created_at,
       s.updated_at,
       s.deleted_at,
       s.version,
       s.change_id,
       coalesce(s.payload->>'title',''),
       coalesce(s.payload->>'body',''),
       nullif(s.payload->>'date','')::date,
       nullif(s.payload->>'time','')::time,
       nullif(s.payload->>'analysisModel',''),
       coalesce(s.payload->>'sourceExcerpt',''),
       s.capture_id
from public.sync_records s where s.kind='event';


insert into public.materials (user_id,id,created_at,updated_at,deleted_at,version,change_id,title,body,analysis_model,source_excerpt,capture_id)
select s.user_id,
       s.id,
       s.created_at,
       s.updated_at,
       s.deleted_at,
       s.version,
       s.change_id,
       coalesce(s.payload->>'title',''),
       coalesce(s.payload->>'body',''),
       nullif(s.payload->>'analysisModel',''),
       coalesce(s.payload->>'sourceExcerpt',''),
       s.capture_id
from public.sync_records s where s.kind='reference';


insert into public.weekly_reviews (user_id,id,created_at,updated_at,deleted_at,version,change_id,review_date,checklist)
select s.user_id,
       s.id,
       s.created_at,
       s.updated_at,
       s.deleted_at,
       s.version,
       s.change_id,
       nullif(s.payload->>'date','')::date,
       coalesce(s.payload->'checklist','{}'::jsonb)
from public.sync_records s where s.kind='review';


-- JSON exists only at the protocol boundary; the underlying data is in typed columns.
create view public.sbros_sync_projection_v3 with(security_invoker=true) as
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
select t.user_id,t.id,'review'::text as kind,
       jsonb_build_object('date',to_char(t.review_date,'YYYY-MM-DD'),
       'checklist',t.checklist) as payload,
       null::uuid as project_id,null::uuid as capture_id,
       t.created_at,t.updated_at,t.deleted_at,t.version,t.change_id
from public.weekly_reviews t
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
revoke all on public.sbros_sync_projection_v3 from public,anon,authenticated;
grant select on public.sbros_sync_projection_v3 to authenticated;


-- Ensure identifiers, versions and mutation IDs survived before removing the old store.
do $$ begin
  if (select count(*) from public.sync_records)<>(select count(*) from public.sbros_sync_projection_v3)
    or exists(select user_id,id,kind,version,change_id from public.sync_records
              except select user_id,id,kind,version,change_id from public.sbros_sync_projection_v3) then
    raise exception 'Relational backfill mismatch; the transaction is rolled back';
  end if;
end $$;

-- An event and a task deadline share the calendar without duplicating records.
create view public.calendar_entries with(security_invoker=true) as
select user_id,id,'event'::text as entry_type,title,event_date as entry_date,event_time as entry_time,
       false as completed,null::uuid as project_id,capture_id
from public.events where deleted_at is null
union all
select user_id,id,'task'::text,title,due_date,null::time,status='done',project_id,capture_id
from public.tasks where deleted_at is null and due_date is not null;
revoke all on public.calendar_entries from public,anon,authenticated;
grant select on public.calendar_entries to authenticated;

create function public.sbros_guard_record_identity_v3() returns trigger
language plpgsql security invoker set search_path='' as $$
begin
  if new.id<>old.id or new.user_id<>old.user_id or new.created_at<>old.created_at then
    raise exception 'immutable_record_identity';
  end if;
  if tg_table_name='captures' and (to_jsonb(new)->>'raw_text') is distinct from (to_jsonb(old)->>'raw_text') then
    raise exception 'immutable_capture_original';
  end if;
  -- Keep administrative edits compatible with versioned synchronisation too.
  if new.version<=old.version then new.version:=old.version+1; end if;
  if new.change_id=old.change_id then new.change_id:=pg_catalog.gen_random_uuid(); end if;
  if new.updated_at=old.updated_at then new.updated_at:=pg_catalog.now(); end if;
  return new;
end $$;
revoke all on function public.sbros_guard_record_identity_v3() from public,anon,authenticated;


create trigger captures_identity_v3 before update on public.captures for each row execute function public.sbros_guard_record_identity_v3();

create trigger projects_identity_v3 before update on public.projects for each row execute function public.sbros_guard_record_identity_v3();

create trigger tasks_identity_v3 before update on public.tasks for each row execute function public.sbros_guard_record_identity_v3();

create trigger thoughts_identity_v3 before update on public.thoughts for each row execute function public.sbros_guard_record_identity_v3();

create trigger events_identity_v3 before update on public.events for each row execute function public.sbros_guard_record_identity_v3();

create trigger materials_identity_v3 before update on public.materials for each row execute function public.sbros_guard_record_identity_v3();

create trigger weekly_reviews_identity_v3 before update on public.weekly_reviews for each row execute function public.sbros_guard_record_identity_v3();

create trigger analysis_runs_identity_v3 before update on public.analysis_runs for each row execute function public.sbros_guard_record_identity_v3();


-- No direct client INSERT/UPDATE/DELETE: every mutation checks ownership and base version.
create function public.sync_push_v3(p_changes jsonb) returns jsonb
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
       or v_kind is null or v_kind not in ('capture','project','action','thought','event','reference','review','analysis_run')
       or jsonb_typeof(v_body) is distinct from 'object' or octet_length(v_body::text)>262144 then raise exception 'invalid_record'; end if;
    if v_kind<>'action' and v_project is not null then raise exception 'unexpected_project_link'; end if;
    if v_kind='review' and v_capture is not null then raise exception 'unexpected_capture_link'; end if;
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
      insert into public.projects (user_id,id,created_at,updated_at,deleted_at,version,change_id,title,area,outcome,analysis_model,source_excerpt,capture_id)
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
              v_capture)
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
        capture_id=excluded.capture_id;

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

    when 'review' then
      insert into public.weekly_reviews (user_id,id,created_at,updated_at,deleted_at,version,change_id,review_date,checklist)
      values (v_user,
              v_id,
              v_created,
              v_updated,
              v_deleted,
              v_version,
              v_change,
              nullif(v_body->>'date','')::date,
              coalesce(v_body->'checklist','{}'::jsonb))
      on conflict(user_id,id) do update set
        updated_at=excluded.updated_at,
        deleted_at=excluded.deleted_at,
        version=excluded.version,
        change_id=excluded.change_id,
        review_date=excluded.review_date,
        checklist=excluded.checklist;

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
revoke all on function public.sync_push_v3(jsonb) from public,anon,authenticated;
grant execute on function public.sync_push_v3(jsonb) to authenticated;

create function public.sync_pull_v3(p_after uuid default null,p_limit integer default 200) returns jsonb
language sql stable security invoker set search_path='' as $$
  select coalesce(jsonb_agg(to_jsonb(r)-'user_id' order by r.id),'[]'::jsonb) from (
    select * from public.sbros_sync_projection_v3
    where user_id=auth.uid() and (p_after is null or id>p_after)
    order by id limit least(greatest(p_limit,1),200)
  ) r
$$;
revoke all on function public.sync_pull_v3(uuid,integer) from public,anon,authenticated;
grant execute on function public.sync_pull_v3(uuid,integer) to authenticated;

-- Remove only the superseded application objects, never the public/auth schemas.
drop table if exists public.items;
drop table if exists public.sbros_legacy_captures;
drop table if exists public.sbros_legacy_weekly_reviews;
drop table public.sync_records;
notify pgrst, 'reload schema';
commit;
