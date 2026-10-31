-- «Сброс»: начальная схема Supabase (PostgreSQL).
-- Обязательны применение в ТЕСТОВОМ проекте, RLS-тесты и проверка актуальной версии Supabase.
-- Серверный Token Harbor API key НЕ принадлежит базе и НЕ хранится в этих таблицах.

create table if not exists public.profiles (
  user_id uuid primary key references auth.users(id) on delete cascade,
  display_name text,
  theme text not null default 'light' check (theme in ('light','dark')),
  palette text not null default 'lavender' check (palette in ('lavender','sage','clay','ocean')),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table if not exists public.captures (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id) on delete cascade,
  parent_capture_id uuid references public.captures(id) on delete set null,
  raw_text text not null check (char_length(btrim(raw_text)) between 1 and 12000),
  source text not null default 'text' check (source in ('text','speech','derived')),
  status text not null default 'inbox' check (status in ('inbox','committed')),
  created_at timestamptz not null default now(),
  confirmed_at timestamptz
);

create table if not exists public.items (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id) on delete cascade,
  capture_id uuid references public.captures(id) on delete set null,
  kind text not null check (kind in ('action','project','thought','event','reference')),
  title text not null check (char_length(btrim(title)) between 1 and 500),
  body text,
  status text check (status in ('next','waiting','someday','done')),
  thought_type text check (thought_type in ('idea','reflection')),
  energy text check (energy in ('easy','medium','hard')),
  area text,
  project_id uuid,
  event_date date,
  event_time time without time zone,
  is_favorite boolean not null default false,
  completed_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique (id, user_id),
  constraint items_project_same_user_fk foreign key (project_id, user_id)
    references public.items(id, user_id),
  constraint items_action_status_chk check (
    (kind = 'action' and status is not null and thought_type is null)
    or (kind <> 'action' and status is null)
  ),
  constraint items_thought_type_chk check (
    (kind = 'thought' and thought_type is not null)
    or (kind <> 'thought' and thought_type is null)
  ),
  constraint items_event_date_chk check (kind <> 'event' or event_date is not null)
);

create table if not exists public.weekly_reviews (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id) on delete cascade,
  completed_at timestamptz not null default now(),
  checklist jsonb not null default '{}'::jsonb
);

create index if not exists captures_user_status_idx on public.captures(user_id, status, created_at desc);
create index if not exists captures_parent_idx on public.captures(parent_capture_id);
create index if not exists items_user_kind_idx on public.items(user_id, kind, created_at desc);
create index if not exists items_user_status_idx on public.items(user_id, status, created_at desc);
create index if not exists items_user_event_idx on public.items(user_id, event_date) where kind = 'event';
create index if not exists items_project_idx on public.items(project_id);
create index if not exists weekly_reviews_user_idx on public.weekly_reviews(user_id, completed_at desc);

-- Таблицы должны быть закрыты для anonymous и доступны только своему пользователю.
alter table public.profiles enable row level security;
alter table public.captures enable row level security;
alter table public.items enable row level security;
alter table public.weekly_reviews enable row level security;

revoke all on table public.profiles, public.captures, public.items, public.weekly_reviews from PUBLIC, anon, authenticated;
grant select, insert, update, delete on table public.profiles, public.captures, public.items, public.weekly_reviews to authenticated;

create policy "profiles_select_own" on public.profiles for select to authenticated
using (user_id = (select auth.uid()));
create policy "profiles_insert_own" on public.profiles for insert to authenticated
with check (user_id = (select auth.uid()));
create policy "profiles_update_own" on public.profiles for update to authenticated
using (user_id = (select auth.uid())) with check (user_id = (select auth.uid()));
create policy "profiles_delete_own" on public.profiles for delete to authenticated
using (user_id = (select auth.uid()));

create policy "captures_select_own" on public.captures for select to authenticated
using (user_id = (select auth.uid()));
create policy "captures_insert_own" on public.captures for insert to authenticated
with check (user_id = (select auth.uid()));
create policy "captures_update_own" on public.captures for update to authenticated
using (user_id = (select auth.uid())) with check (user_id = (select auth.uid()));
create policy "captures_delete_own" on public.captures for delete to authenticated
using (user_id = (select auth.uid()));

create policy "items_select_own" on public.items for select to authenticated
using (user_id = (select auth.uid()));
create policy "items_insert_own" on public.items for insert to authenticated
with check (user_id = (select auth.uid()));
create policy "items_update_own" on public.items for update to authenticated
using (user_id = (select auth.uid())) with check (user_id = (select auth.uid()));
create policy "items_delete_own" on public.items for delete to authenticated
using (user_id = (select auth.uid()));

create policy "reviews_select_own" on public.weekly_reviews for select to authenticated
using (user_id = (select auth.uid()));
create policy "reviews_insert_own" on public.weekly_reviews for insert to authenticated
with check (user_id = (select auth.uid()));
create policy "reviews_update_own" on public.weekly_reviews for update to authenticated
using (user_id = (select auth.uid())) with check (user_id = (select auth.uid()));
create policy "reviews_delete_own" on public.weekly_reviews for delete to authenticated
using (user_id = (select auth.uid()));

-- ВАЖНО: серверный подтверждающий RPC (для атомарности, идемпотентности).
-- Вызов выполняется только после явного нажатия «Подтвердить» пользователем.
-- Не использует service role, работает с RLS в контексте вошедшего пользователя.
create or replace function public.confirm_capture(p_capture_id uuid, p_proposals jsonb)
returns jsonb
language plpgsql
security invoker
set search_path = ''
as $$
declare
  v_user uuid := (select auth.uid());
  v_capture public.captures%rowtype;
  v_prop jsonb;
  v_kind text;
  v_title text;
  v_new_kind text;
  v_action_status text;
  v_thought_type text;
  v_event_date date;
  v_event_time time;
  v_energy text;
  v_count integer := 0;
  v_total integer;
begin
  if v_user is null then
    raise exception 'not_authenticated';
  end if;

  if jsonb_typeof(p_proposals) is distinct from 'array' then
    raise exception 'proposals_must_be_array';
  end if;
  v_total := jsonb_array_length(p_proposals);
  if v_total < 1 or v_total > 25 then
    raise exception 'invalid_proposal_count';
  end if;

  select * into v_capture from public.captures
  where id = p_capture_id and user_id = v_user for update;

  if not found then
    raise exception 'capture_not_found';
  end if;

  if v_capture.status = 'committed' then
    return jsonb_build_object('status', 'already_committed');
  end if;

  for v_prop in select value from jsonb_array_elements(p_proposals)
  loop
    if jsonb_typeof(v_prop) is distinct from 'object' then
      raise exception 'proposal_must_be_object';
    end if;

    v_kind := v_prop ->> 'kind';
    v_title := btrim(coalesce(v_prop ->> 'title',''));
    if char_length(v_title) < 1 or char_length(v_title) > 500 then
      raise exception 'invalid_title';
    end if;
    if v_kind not in ('next','reflection','idea','waiting','projects','cal','someday','notes','inbox') then
      raise exception 'invalid_kind';
    end if;

    if v_kind = 'inbox' then
      insert into public.captures(user_id, parent_capture_id, raw_text, source, status)
      values (v_user, p_capture_id, v_title, 'derived', 'inbox');
      v_count := v_count + 1;
      continue;
    end if;

    v_new_kind := case
      when v_kind in ('next','waiting','someday') then 'action'
      when v_kind in ('idea','reflection') then 'thought'
      when v_kind = 'projects' then 'project'
      when v_kind = 'cal' then 'event'
      when v_kind = 'notes' then 'reference'
    end;

    v_action_status := case when v_kind = 'next' then 'next'
                            when v_kind = 'waiting' then 'waiting'
                            when v_kind = 'someday' then 'someday' end;
    v_thought_type := case when v_kind in ('idea','reflection') then v_kind end;
    v_energy := nullif(v_prop ->> 'energy','');
    if v_new_kind = 'action' then
      if v_energy is null then v_energy := 'easy'; end if;
      if v_energy not in ('easy','medium','hard') then raise exception 'invalid_energy'; end if;
    else
      v_energy := null;
    end if;

    v_event_date := null;
    v_event_time := null;
    if v_kind = 'cal' then
      if nullif(v_prop ->> 'date','') is null then
        raise exception 'calendar_date_required';
      end if;
      v_event_date := (v_prop ->> 'date')::date;
      if nullif(v_prop ->> 'time','') is not null then
        v_event_time := (v_prop ->> 'time')::time;
      end if;
    end if;

    insert into public.items(
      user_id, capture_id, kind, title, body, status, thought_type,
      energy, area, event_date, event_time
    ) values (
      v_user, p_capture_id, v_new_kind, v_title, nullif(v_prop ->> 'body',''),
      v_action_status, v_thought_type, v_energy,
      nullif(v_prop ->> 'area',''), v_event_date, v_event_time
    );
    v_count := v_count + 1;
  end loop;

  update public.captures
  set status = 'committed', confirmed_at = now()
  where id = p_capture_id and user_id = v_user;

  return jsonb_build_object('status', 'committed', 'created', v_count);
end;
$$;

revoke all on function public.confirm_capture(uuid, jsonb) from PUBLIC, anon;
grant execute on function public.confirm_capture(uuid, jsonb) to authenticated;

-- ВНИМАНИЕ: для большей строгости production-версии агенту нужно отдельно
-- ограничить изменение captures.status напрямую в PostgREST; протестировать повторный
-- вызов RPC, конкурентные запросы и права authenticated/anon.
