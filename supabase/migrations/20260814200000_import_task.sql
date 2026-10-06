-- ============================================================
-- v1.0 导入后台任务表：文档 / 正文 / 网页 AI 解析进度与结果
-- 用于 Netlify 后台函数轮询式长解析
-- ============================================================

create table public.import_task (
  id uuid primary key default gen_random_uuid(),
  user_id uuid references public.profiles (id) on delete cascade,
  kind text not null check (kind in ('doc', 'text', 'url')),
  phase text not null default 'PARSE' check (phase in ('PARSE', 'READY', 'IMPORT', 'DONE')),
  status text not null default 'PENDING' check (status in ('PENDING', 'RUNNING', 'COMPLETED', 'FAILED')),
  progress int not null default 0,
  total_chunks int not null default 0,
  done_chunks int not null default 0,
  bank_name text not null,
  bank_description text,
  source_text text,
  source_name text,
  items jsonb,
  selected_indexes jsonb,
  bank_id bigint references public.bank (id) on delete set null,
  error text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);
create index import_task_user_id_idx on public.import_task (user_id, created_at desc);

create trigger touch_import_task before update on public.import_task
  for each row execute function extensions.moddatetime (updated_at);

alter table public.import_task enable row level security;
alter table public.import_task force row level security;
create policy import_task_select on public.import_task for select to authenticated
  using (user_id = (select auth.uid()) or (select private.is_admin()));

revoke all on public.import_task from anon, authenticated;
grant select on public.import_task to authenticated;
grant select, insert, update, delete on public.import_task to app_service;
grant usage on schema public to app_service;