-- ============================================================
-- v1.0 商业化：套餐 / 点数账户 / 点数流水 / 订单 / 计费规则
-- 计费口径：AI 判题 3 点、AI 解析 1 点、文档解析每 1 万字 10 点
-- 点数分两个桶：MONTHLY（订阅周期额度，到期重置）
--               BONUS（点数包/赠送，长期有效）
-- ============================================================

-- ============ 1. 套餐 ============

create table public.plan (
  code text primary key,
  name text not null,
  tagline text,
  description text,
  -- 价格单位：分，0 表示免费/需定制
  price_monthly_cents int not null default 0 check (price_monthly_cents >= 0),
  price_quarterly_cents int not null default 0 check (price_quarterly_cents >= 0),
  price_yearly_cents int not null default 0 check (price_yearly_cents >= 0),
  monthly_points int not null default 0 check (monthly_points >= 0),
  -- null = 不限
  max_banks int check (max_banks is null or max_banks >= 0),
  max_questions int check (max_questions is null or max_questions >= 0),
  -- null = 永久保留练习记录
  retention_days int check (retention_days is null or retention_days > 0),
  allow_pro_model boolean not null default false,
  show_on_pricing boolean not null default true,
  features jsonb not null default '[]'::jsonb,
  sort int not null default 0,
  status text not null default 'ENABLED' check (status in ('ENABLED', 'DISABLED')),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create trigger touch_plan before update on public.plan
  for each row execute function extensions.moddatetime (updated_at);

-- ============ 2. 点数包（加量包，长期有效） ============

create table public.point_pack (
  code text primary key,
  name text not null,
  price_cents int not null check (price_cents > 0),
  points int not null check (points > 0),
  bonus_points int not null default 0 check (bonus_points >= 0),
  sort int not null default 0,
  status text not null default 'ENABLED' check (status in ('ENABLED', 'DISABLED')),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create trigger touch_point_pack before update on public.point_pack
  for each row execute function extensions.moddatetime (updated_at);

-- ============ 3. 计费规则（可按用途调整单价） ============

create table public.ai_price_rule (
  purpose text primary key check (purpose in ('JUDGE', 'EXPLAIN', 'EXTRACT', 'TEST')),
  points int not null check (points >= 0),
  unit text not null default 'CALL' check (unit in ('CALL', 'PER_10K_CHARS')),
  description text,
  updated_at timestamptz not null default now()
);

create trigger touch_ai_price_rule before update on public.ai_price_rule
  for each row execute function extensions.moddatetime (updated_at);

-- ============ 4. 全局计费配置 ============

create table public.billing_config (
  key text primary key,
  value jsonb not null,
  description text,
  updated_at timestamptz not null default now()
);

create trigger touch_billing_config before update on public.billing_config
  for each row execute function extensions.moddatetime (updated_at);

-- ============ 5. 点数账户 ============

create table public.point_account (
  user_id uuid primary key references public.profiles (id) on delete cascade,
  plan_code text not null default 'free' references public.plan (code),
  plan_expires_at timestamptz,
  -- MONTHLY 桶：订阅周期额度，到期后 quota 重置、used 归零
  monthly_quota int not null default 0 check (monthly_quota >= 0),
  monthly_used int not null default 0 check (monthly_used >= 0),
  -- BONUS 桶：点数包与赠送，不随周期重置
  bonus_balance int not null default 0 check (bonus_balance >= 0),
  period_start timestamptz not null default now(),
  period_end timestamptz not null default (now() + interval '1 month'),
  lifetime_granted int not null default 0,
  lifetime_used int not null default 0,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);
create index point_account_plan_code_idx on public.point_account (plan_code);

create trigger touch_point_account before update on public.point_account
  for each row execute function extensions.moddatetime (updated_at);

-- ============ 6. 点数流水 ============

create table public.point_ledger (
  id bigint generated always as identity primary key,
  user_id uuid not null references public.profiles (id) on delete cascade,
  delta int not null,
  bucket text not null check (bucket in ('MONTHLY', 'BONUS')),
  balance_after int not null,
  reason text not null,
  ref_type text,
  ref_id text,
  note text,
  created_at timestamptz not null default now()
);
create index point_ledger_user_created_idx on public.point_ledger (user_id, created_at desc);
create index point_ledger_reason_idx on public.point_ledger (reason);

-- ============ 7. 订阅 / 点数包订单（支付接入占位） ============

create table public.subscription_order (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references public.profiles (id) on delete cascade,
  kind text not null check (kind in ('PLAN', 'PACK')),
  item_code text not null,
  period text check (period in ('MONTHLY', 'QUARTERLY', 'YEARLY')),
  amount_cents int not null check (amount_cents >= 0),
  points int not null default 0,
  status text not null default 'PENDING' check (status in ('PENDING', 'PAID', 'CANCELLED', 'REFUNDED')),
  provider text,
  provider_order_id text,
  paid_at timestamptz,
  note text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);
create index subscription_order_user_created_idx on public.subscription_order (user_id, created_at desc);
create index subscription_order_status_idx on public.subscription_order (status);

create trigger touch_subscription_order before update on public.subscription_order
  for each row execute function extensions.moddatetime (updated_at);

-- ============ 8. 账务函数 ============
-- 说明：这些函数放在 public 以便 PostgREST RPC 调用，
-- 但已 revoke 掉 public/anon/authenticated 的 EXECUTE，
-- 仅 service_role（Next.js 服务端）与 app_service 可执行。

-- 8.1 首次创建点数账户
create or replace function public.ensure_point_account(p_user_id uuid)
returns void language plpgsql security definer set search_path = '' as $$
declare
  v_free public.plan%rowtype;
  v_bonus int := 0;
  v_cfg jsonb;
begin
  if exists (select 1 from public.point_account where user_id = p_user_id) then
    return;
  end if;

  select * into v_free from public.plan where code = 'free';
  select value into v_cfg from public.billing_config where key = 'signup_bonus_points';
  if v_cfg is not null then
    v_bonus := greatest(coalesce((v_cfg #>> '{}')::int, 0), 0);
  end if;

  insert into public.point_account
    (user_id, plan_code, monthly_quota, bonus_balance, period_start, period_end,
     lifetime_granted)
  values
    (p_user_id, 'free', coalesce(v_free.monthly_points, 0), v_bonus, now(),
     now() + interval '1 month', coalesce(v_free.monthly_points, 0) + v_bonus)
  on conflict (user_id) do nothing;

  if v_bonus > 0 then
    insert into public.point_ledger (user_id, delta, bucket, balance_after, reason, note)
    values (p_user_id, v_bonus, 'BONUS', v_bonus, 'SIGNUP_BONUS', '新用户注册赠送');
  end if;
end;
$$;

-- 8.2 周期额度续期（幂等：到期才重置）
create or replace function public.renew_point_period(p_user_id uuid)
returns void language plpgsql security definer set search_path = '' as $$
declare
  v_acc public.point_account%rowtype;
  v_plan public.plan%rowtype;
  v_delta int;
begin
  select * into v_acc from public.point_account where user_id = p_user_id for update;
  if not found then
    perform public.ensure_point_account(p_user_id);
    select * into v_acc from public.point_account where user_id = p_user_id for update;
  end if;

  -- 订阅到期则回落免费套餐
  if v_acc.plan_expires_at is not null and v_acc.plan_expires_at <= now()
     and v_acc.plan_code <> 'free' then
    update public.point_account set plan_code = 'free', plan_expires_at = null
     where user_id = p_user_id returning * into v_acc;
  end if;

  if v_acc.period_end > now() then
    return;
  end if;

  select * into v_plan from public.plan where code = v_acc.plan_code;
  v_delta := greatest(coalesce(v_plan.monthly_points, 0) - v_acc.monthly_quota, 0);

  update public.point_account
     set monthly_quota = coalesce(v_plan.monthly_points, 0),
         monthly_used = 0,
         period_start = now(),
         period_end = now() + interval '1 month',
         lifetime_granted = lifetime_granted + v_delta
   where user_id = p_user_id
   returning * into v_acc;

  if v_delta > 0 then
    insert into public.point_ledger (user_id, delta, bucket, balance_after, reason, note)
    values (p_user_id, v_delta, 'MONTHLY',
            greatest(v_acc.monthly_quota - v_acc.monthly_used, 0) + v_acc.bonus_balance,
            'MONTHLY_GRANT', '周期额度重置');
  end if;
end;
$$;

-- 8.3 扣点（先扣 MONTHLY，再扣 BONUS），余额不足返回 ok=false 而不报错
create or replace function public.consume_points(
  p_user_id uuid,
  p_points int,
  p_reason text,
  p_ref_type text default null,
  p_ref_id text default null
) returns jsonb language plpgsql security definer set search_path = '' as $$
declare
  v_acc public.point_account%rowtype;
  v_monthly_left int;
  v_from_monthly int;
  v_from_bonus int;
  v_bucket text;
begin
  if p_points is null or p_points < 0 then
    raise exception 'invalid points';
  end if;

  perform public.ensure_point_account(p_user_id);
  perform public.renew_point_period(p_user_id);

  select * into v_acc from public.point_account where user_id = p_user_id for update;
  v_monthly_left := greatest(v_acc.monthly_quota - v_acc.monthly_used, 0);

  if p_points > v_monthly_left + v_acc.bonus_balance then
    return jsonb_build_object(
      'ok', false,
      'cost', p_points,
      'available', v_monthly_left + v_acc.bonus_balance
    );
  end if;

  v_from_monthly := least(v_monthly_left, p_points);
  v_from_bonus := p_points - v_from_monthly;
  v_bucket := case when v_from_monthly = p_points then 'MONTHLY' else 'BONUS' end;

  update public.point_account
     set monthly_used = monthly_used + v_from_monthly,
         bonus_balance = bonus_balance - v_from_bonus,
         lifetime_used = lifetime_used + p_points
   where user_id = p_user_id
   returning * into v_acc;

  if p_points > 0 then
    insert into public.point_ledger (user_id, delta, bucket, balance_after, reason, ref_type, ref_id)
    values (p_user_id, -p_points, v_bucket,
            greatest(v_acc.monthly_quota - v_acc.monthly_used, 0) + v_acc.bonus_balance,
            p_reason, p_ref_type, p_ref_id);
  end if;

  return jsonb_build_object(
    'ok', true,
    'cost', p_points,
    'available', greatest(v_acc.monthly_quota - v_acc.monthly_used, 0) + v_acc.bonus_balance
  );
end;
$$;

-- 8.4 退点（AI 调用失败时补偿），优先退回 MONTHLY
create or replace function public.refund_points(
  p_user_id uuid,
  p_points int,
  p_reason text,
  p_ref_type text default null,
  p_ref_id text default null
) returns void language plpgsql security definer set search_path = '' as $$
declare
  v_acc public.point_account%rowtype;
  v_back_monthly int;
begin
  if p_points is null or p_points <= 0 then
    return;
  end if;

  select * into v_acc from public.point_account where user_id = p_user_id for update;
  if not found then
    return;
  end if;

  v_back_monthly := least(greatest(v_acc.monthly_used, 0), p_points);

  update public.point_account
     set monthly_used = monthly_used - v_back_monthly,
         bonus_balance = bonus_balance + (p_points - v_back_monthly),
         lifetime_used = greatest(lifetime_used - p_points, 0)
   where user_id = p_user_id
   returning * into v_acc;

  insert into public.point_ledger (user_id, delta, bucket, balance_after, reason, ref_type, ref_id)
  values (p_user_id, p_points,
          case when v_back_monthly = p_points then 'MONTHLY' else 'BONUS' end,
          greatest(v_acc.monthly_quota - v_acc.monthly_used, 0) + v_acc.bonus_balance,
          p_reason, p_ref_type, p_ref_id);
end;
$$;

-- 8.5 入账赠送 / 购买点数
create or replace function public.add_bonus_points(
  p_user_id uuid,
  p_points int,
  p_reason text,
  p_ref_type text default null,
  p_ref_id text default null
) returns void language plpgsql security definer set search_path = '' as $$
declare
  v_acc public.point_account%rowtype;
begin
  if p_points is null or p_points <= 0 then
    return;
  end if;

  perform public.ensure_point_account(p_user_id);

  update public.point_account
     set bonus_balance = bonus_balance + p_points,
         lifetime_granted = lifetime_granted + p_points
   where user_id = p_user_id
   returning * into v_acc;

  insert into public.point_ledger (user_id, delta, bucket, balance_after, reason, ref_type, ref_id)
  values (p_user_id, p_points, 'BONUS',
          greatest(v_acc.monthly_quota - v_acc.monthly_used, 0) + v_acc.bonus_balance,
          p_reason, p_ref_type, p_ref_id);
end;
$$;

-- 8.6 应用套餐变更
create or replace function public.apply_plan(
  p_user_id uuid,
  p_plan_code text,
  p_expires_at timestamptz
) returns void language plpgsql security definer set search_path = '' as $$
declare
  v_plan public.plan%rowtype;
  v_acc public.point_account%rowtype;
  v_new_quota int;
  v_delta int;
begin
  select * into v_plan from public.plan where code = p_plan_code;
  if not found then
    raise exception 'plan not found: %', p_plan_code;
  end if;

  perform public.ensure_point_account(p_user_id);
  v_new_quota := coalesce(v_plan.monthly_points, 0);

  select * into v_acc from public.point_account where user_id = p_user_id for update;
  v_delta := greatest(v_new_quota - v_acc.monthly_quota, 0);

  update public.point_account
     set plan_code = p_plan_code,
         plan_expires_at = p_expires_at,
         monthly_quota = v_new_quota,
         monthly_used = least(v_acc.monthly_used, v_new_quota),
         period_start = now(),
         period_end = least(coalesce(p_expires_at, now() + interval '1 month'),
                            now() + interval '1 month'),
         lifetime_granted = lifetime_granted + v_delta
   where user_id = p_user_id
   returning * into v_acc;

  if v_delta > 0 then
    insert into public.point_ledger (user_id, delta, bucket, balance_after, reason, note)
    values (p_user_id, v_delta, 'MONTHLY',
            greatest(v_acc.monthly_quota - v_acc.monthly_used, 0) + v_acc.bonus_balance,
            'PLAN_CHANGE', '切换套餐：' || p_plan_code);
  end if;
end;
$$;

-- ============ 9. 新用户自动开账户 ============

create or replace function private.on_profile_created()
returns trigger language plpgsql security definer set search_path = '' as $$
begin
  perform public.ensure_point_account(new.id);
  return new;
end;
$$;

create trigger on_profile_created after insert on public.profiles
  for each row execute function private.on_profile_created();

-- 函数执行权：端用户一律禁止，仅服务端角色可调用
revoke execute on function public.ensure_point_account(uuid) from public, anon, authenticated;
revoke execute on function public.renew_point_period(uuid) from public, anon, authenticated;
revoke execute on function public.consume_points(uuid, int, text, text, text) from public, anon, authenticated;
revoke execute on function public.refund_points(uuid, int, text, text, text) from public, anon, authenticated;
revoke execute on function public.add_bonus_points(uuid, int, text, text, text) from public, anon, authenticated;
revoke execute on function public.apply_plan(uuid, text, timestamptz) from public, anon, authenticated;
grant execute on function public.ensure_point_account(uuid) to service_role, app_service;
grant execute on function public.renew_point_period(uuid) to service_role, app_service;
grant execute on function public.consume_points(uuid, int, text, text, text) to service_role, app_service;
grant execute on function public.refund_points(uuid, int, text, text, text) to service_role, app_service;
grant execute on function public.add_bonus_points(uuid, int, text, text, text) to service_role, app_service;
grant execute on function public.apply_plan(uuid, text, timestamptz) to service_role, app_service;

-- ============ 10. RLS ============

alter table public.plan enable row level security;
alter table public.plan force row level security;
alter table public.point_pack enable row level security;
alter table public.point_pack force row level security;
alter table public.ai_price_rule enable row level security;
alter table public.ai_price_rule force row level security;
alter table public.billing_config enable row level security;
alter table public.billing_config force row level security;
alter table public.point_account enable row level security;
alter table public.point_account force row level security;
alter table public.point_ledger enable row level security;
alter table public.point_ledger force row level security;
alter table public.subscription_order enable row level security;
alter table public.subscription_order force row level security;

-- 目录类数据：登录用户只读可见项；管理员全量
create policy plan_select on public.plan for select to authenticated
  using (status = 'ENABLED' or (select private.is_admin()));
create policy point_pack_select on public.point_pack for select to authenticated
  using (status = 'ENABLED' or (select private.is_admin()));
create policy ai_price_rule_select on public.ai_price_rule for select to authenticated
  using (true);

-- billing_config 仅管理员可读，普通用户通过接口暴露必要字段
create policy billing_config_select on public.billing_config for select to authenticated
  using ((select private.is_admin()));

-- 用户自己的账务
create policy point_account_select on public.point_account for select to authenticated
  using (user_id = (select auth.uid()) or (select private.is_admin()));
create policy point_ledger_select on public.point_ledger for select to authenticated
  using (user_id = (select auth.uid()) or (select private.is_admin()));
create policy subscription_order_select on public.subscription_order for select to authenticated
  using (user_id = (select auth.uid()) or (select private.is_admin()));

-- ============ 11. 授权 ============

revoke all on public.plan, public.point_pack, public.ai_price_rule,
  public.billing_config, public.point_account, public.point_ledger,
  public.subscription_order from anon, authenticated;

grant select on public.plan to authenticated;
grant select on public.point_pack to authenticated;
grant select on public.ai_price_rule to authenticated;
grant select on public.point_account to authenticated;
grant select on public.point_ledger to authenticated;
grant select on public.subscription_order to authenticated;

grant select, insert, update, delete on public.plan to app_service;
grant select, insert, update, delete on public.point_pack to app_service;
grant select, insert, update, delete on public.ai_price_rule to app_service;
grant select, insert, update, delete on public.billing_config to app_service;
grant select, insert, update, delete on public.point_account to app_service;
grant select, insert, update, delete on public.point_ledger to app_service;
grant select, insert, update, delete on public.subscription_order to app_service;
grant usage on sequence public.point_ledger_id_seq to app_service;

-- ============ 12. 种子数据 ============

insert into public.plan
  (code, name, tagline, description, price_monthly_cents, price_quarterly_cents,
   price_yearly_cents, monthly_points, max_banks, max_questions, retention_days,
   allow_pro_model, show_on_pricing, features, sort)
values
  ('free', '免费版', '先用起来', '公共题库随便刷，AI 能力有月度额度',
   0, 0, 0, 50, 1, 50, 30, false, true,
   '["全部公共题库无限刷","选择题本地判分不限次","每月 50 点 AI 额度","自建题库 1 个 / 50 题","练习记录保留 30 天"]'::jsonb, 10),
  ('plus', 'Plus', '备考主力', 'AI 批改与题库生成的主力档位',
   1900, 4900, 15800, 1000, 20, 2000, null, false, true,
   '["免费版全部权益","每月 1000 点 AI 额度","自建题库 20 个 / 2000 题","练习记录与错题本永久保留","导出错题与笔记"]'::jsonb, 20),
  ('pro', 'Pro', '冲刺提速', '高频刷题与大规模文档导入',
   3900, 9900, 32800, 3000, null, null, null, true, true,
   '["Plus 全部权益","每月 3000 点 AI 额度","自建题库与题量不限","可选 deepseek-v4-pro 模型","详细掌握度报告"]'::jsonb, 30),
  ('team', '机构版', '班级与机构', '培训机构、学校、企业内训批量使用',
   0, 0, 0, 0, null, null, null, true, true,
   '["Pro 全部权益","批量账号与班级管理","机构题库共享给学员","班级数据看板","专属支持"]'::jsonb, 40)
on conflict (code) do nothing;

insert into public.point_pack (code, name, price_cents, points, bonus_points, sort)
values
  ('pack_9', '轻量加量包', 900, 400, 0, 10),
  ('pack_29', '常用加量包', 2900, 1400, 0, 20),
  ('pack_99', '超值加量包', 9900, 5000, 0, 30)
on conflict (code) do nothing;

insert into public.ai_price_rule (purpose, points, unit, description)
values
  ('EXPLAIN', 1, 'CALL', '选择题 AI 解析，每次 1 点'),
  ('JUDGE', 3, 'CALL', '简答题 / 伪代码 AI 判分，每次 3 点'),
  ('EXTRACT', 10, 'PER_10K_CHARS', '文档 / 网页 / 正文 AI 解析成题库，每 1 万字 10 点'),
  ('TEST', 0, 'CALL', '后台连通性测试不扣点')
on conflict (purpose) do nothing;

insert into public.billing_config (key, value, description)
values ('signup_bonus_points', '100'::jsonb, '新用户注册赠送点数')
on conflict (key) do nothing;

-- 为已有用户补齐点数账户
do $$
declare
  r record;
begin
  for r in select id from public.profiles loop
    perform public.ensure_point_account(r.id);
  end loop;
end;
$$;
