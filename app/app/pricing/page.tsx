"use client";

import { useEffect, useState } from "react";
import { Check, Sparkles } from "lucide-react";
import { api } from "@/lib/client-api";

type Period = "MONTHLY" | "QUARTERLY" | "YEARLY";

interface Plan {
  code: string;
  name: string;
  tagline: string | null;
  description: string | null;
  price_monthly_cents: number;
  price_quarterly_cents: number;
  price_yearly_cents: number;
  monthly_points: number;
  max_banks: number | null;
  max_questions: number | null;
  retention_days: number | null;
  allow_pro_model: boolean;
  features: string[];
}

interface Pack {
  code: string;
  name: string;
  price_cents: number;
  points: number;
  bonus_points: number;
}

interface Rule {
  purpose: string;
  points: number;
  unit: string;
  description: string | null;
}

interface Entitlements {
  planCode: string;
  planName: string;
  planExpiresAt: string | null;
  monthlyQuota: number;
  monthlyUsed: number;
  monthlyLeft: number;
  bonusBalance: number;
  available: number;
  lifetimeUsed: number;
  periodEnd: string;
}

interface Ledger {
  id: number;
  delta: number;
  balance_after: number;
  reason: string;
  note: string | null;
  created_at: string;
}

interface Order {
  id: string;
  kind: string;
  item_code: string;
  period: string | null;
  amount_cents: number;
  points: number;
  status: string;
  created_at: string;
}

const PERIODS: { key: Period; label: string; suffix: string }[] = [
  { key: "MONTHLY", label: "月付", suffix: "/月" },
  { key: "QUARTERLY", label: "季付", suffix: "/季" },
  { key: "YEARLY", label: "年付", suffix: "/年" },
];

const PURPOSE_LABEL: Record<string, string> = {
  JUDGE: "简答题 / 伪代码 AI 判分",
  EXPLAIN: "选择题 AI 解析",
  EXTRACT: "AI 解析生成题库",
  TEST: "连通性测试",
};

const REASON_LABEL: Record<string, string> = {
  SIGNUP_BONUS: "注册赠送",
  MONTHLY_GRANT: "月度额度发放",
  PLAN_CHANGE: "套餐变更",
  PACK_PURCHASE: "加量包到账",
  ADMIN_ADJUST: "管理员调整",
  AI_JUDGE: "AI 判分",
  AI_EXPLAIN: "AI 解析",
  AI_EXTRACT: "AI 生成题库",
  AI_JUDGE_FAILED: "判分失败退回",
  AI_EXPLAIN_FAILED: "解析失败退回",
  AI_EXTRACT_FAILED: "生成失败退回",
  AI_EXTRACT_PARTIAL_FAILED: "部分内容失败退回",
};

function yuan(cents: number) {
  if (!cents) return "定制";
  const v = cents / 100;
  return "¥" + (Number.isInteger(v) ? v : v.toFixed(2));
}

function quantity(v: number | null, unit: string) {
  return v === null ? "不限" : `${v} ${unit}`;
}

export default function PricingPage() {
  const [period, setPeriod] = useState<Period>("YEARLY");
  const [plans, setPlans] = useState<Plan[]>([]);
  const [packs, setPacks] = useState<Pack[]>([]);
  const [rules, setRules] = useState<Rule[]>([]);
  const [ent, setEnt] = useState<Entitlements | null>(null);
  const [ledger, setLedger] = useState<Ledger[]>([]);
  const [orders, setOrders] = useState<Order[]>([]);
  const [loading, setLoading] = useState(true);
  const [notice, setNotice] = useState("");
  const [busy, setBusy] = useState("");

  async function load() {
    const [catalog, points, myOrders] = await Promise.all([
      api<{ plans: Plan[]; packs: Pack[]; rules: Rule[] }>("/api/plans"),
      api<{ entitlements: Entitlements; ledger: Ledger[] }>("/api/points"),
      api<Order[]>("/api/orders"),
    ]);
    setPlans(catalog.plans ?? []);
    setPacks(catalog.packs ?? []);
    setRules(catalog.rules ?? []);
    setEnt(points.entitlements);
    setLedger(points.ledger ?? []);
    setOrders(myOrders ?? []);
  }

  useEffect(() => {
    load()
      .catch(() => setNotice("加载失败，请刷新重试"))
      .finally(() => setLoading(false));
  }, []);

  async function buy(kind: "PLAN" | "PACK", itemCode: string) {
    setNotice("");
    setBusy(kind + ":" + itemCode);
    try {
      await api("/api/orders", {
        method: "POST",
        body: JSON.stringify({ kind, itemCode, period: kind === "PLAN" ? period : null }),
      });
      await load();
      setNotice("订单已创建。当前尚未接入在线支付，请联系管理员确认收款后开通。");
    } catch (e) {
      setNotice(e instanceof Error ? e.message : "下单失败");
    } finally {
      setBusy("");
    }
  }

  if (loading) {
    return <div className="mx-auto max-w-6xl p-10 text-center text-sm text-oat">加载中…</div>;
  }

  const priceOf = (p: Plan) =>
    period === "MONTHLY"
      ? p.price_monthly_cents
      : period === "QUARTERLY"
        ? p.price_quarterly_cents
        : p.price_yearly_cents;

  return (
    <div className="mx-auto max-w-6xl space-y-8">
      <header>
        <h1 className="text-2xl font-semibold tracking-tight text-ink">套餐与点数</h1>
        <p className="mt-1 text-sm text-oat">
          公共题库与选择题判分永久免费，AI 能力按点数计费
        </p>
      </header>

      {ent && (
        <section className="grid gap-4 rounded-2xl border border-mist bg-white/70 p-5 sm:grid-cols-4">
          <div>
            <p className="text-xs text-oat">当前套餐</p>
            <p className="mt-1 font-medium text-ink">{ent.planName}</p>
          </div>
          <div>
            <p className="text-xs text-oat">可用点数</p>
            <p className="mt-1 font-medium text-ink">{ent.available} 点</p>
          </div>
          <div>
            <p className="text-xs text-oat">本月剩余额度</p>
            <p className="mt-1 font-medium text-ink">
              {ent.monthlyLeft} / {ent.monthlyQuota} 点
            </p>
          </div>
          <div>
            <p className="text-xs text-oat">加量包余额</p>
            <p className="mt-1 font-medium text-ink">{ent.bonusBalance} 点</p>
          </div>
        </section>
      )}

      {notice && (
        <p className="rounded-xl bg-moss/10 px-4 py-3 text-sm text-ink">{notice}</p>
      )}

      <section>
        <div className="mb-4 flex items-center justify-between">
          <h2 className="font-medium text-ink">选择套餐</h2>
          <div className="flex rounded-xl bg-mist p-1">
            {PERIODS.map((p) => (
              <button
                key={p.key}
                onClick={() => setPeriod(p.key)}
                className={`rounded-lg px-3 py-1.5 text-sm transition ${
                  period === p.key ? "bg-white text-ink shadow-sm" : "text-oat"
                }`}
              >
                {p.label}
              </button>
            ))}
          </div>
        </div>

        <div className="grid gap-4 lg:grid-cols-4">
          {plans.map((p) => {
            const current = ent?.planCode === p.code;
            const price = priceOf(p);
            const suffix = PERIODS.find((x) => x.key === period)?.suffix ?? "";
            return (
              <div
                key={p.code}
                className={`flex flex-col rounded-2xl border bg-white/70 p-5 ${
                  current ? "border-moss" : "border-mist"
                }`}
              >
                <div className="flex items-center justify-between">
                  <h3 className="font-medium text-ink">{p.name}</h3>
                  {current && (
                    <span className="rounded-full bg-moss/15 px-2 py-0.5 text-xs text-moss">
                      当前
                    </span>
                  )}
                </div>
                <p className="mt-1 text-xs text-oat">{p.tagline ?? p.description ?? ""}</p>
                <p className="mt-4 text-2xl font-semibold text-ink">
                  {yuan(price)}
                  {price > 0 && <span className="text-sm font-normal text-oat">{suffix}</span>}
                </p>
                <ul className="mt-4 flex-1 space-y-2 text-sm text-ink/80">
                  {(p.features ?? []).map((f) => (
                    <li key={f} className="flex gap-2">
                      <Check className="mt-0.5 h-3.5 w-3.5 shrink-0 text-moss" />
                      <span>{f}</span>
                    </li>
                  ))}
                  <li className="flex gap-2 text-xs text-oat">
                    <Check className="mt-0.5 h-3.5 w-3.5 shrink-0 text-oat" />
                    <span>
                      题库上限 {quantity(p.max_banks, "个")} / 题量上限{" "}
                      {quantity(p.max_questions, "题")}
                    </span>
                  </li>
                </ul>
                <button
                  disabled={current || price <= 0 || busy !== ""}
                  onClick={() => buy("PLAN", p.code)}
                  className="mt-5 w-full rounded-xl bg-ink py-2 text-sm text-white transition hover:bg-ink/90 disabled:opacity-50"
                >
                  {current
                    ? "使用中"
                    : price <= 0
                      ? "联系管理员"
                      : busy === "PLAN:" + p.code
                        ? "提交中…"
                        : "选择该套餐"}
                </button>
              </div>
            );
          })}
        </div>
      </section>

      <section>
        <h2 className="mb-4 font-medium text-ink">加量包</h2>
        <div className="grid gap-4 sm:grid-cols-3">
          {packs.map((p) => (
            <div
              key={p.code}
              className="flex items-center justify-between rounded-2xl border border-mist bg-white/70 p-5"
            >
              <div>
                <p className="font-medium text-ink">{p.name}</p>
                <p className="mt-1 text-sm text-oat">
                  {p.points + p.bonus_points} 点
                  {p.bonus_points > 0 && `（含赠送 ${p.bonus_points} 点）`}
                </p>
                <p className="mt-0.5 text-xs text-oat">点数长期有效，不随周期重置</p>
              </div>
              <button
                disabled={busy !== ""}
                onClick={() => buy("PACK", p.code)}
                className="shrink-0 rounded-xl border border-ink px-3 py-1.5 text-sm text-ink transition hover:bg-ink hover:text-white disabled:opacity-50"
              >
                {yuan(p.price_cents)}
              </button>
            </div>
          ))}
        </div>
      </section>

      <section>
        <h2 className="mb-4 font-medium text-ink">AI 点数消耗</h2>
        <div className="overflow-hidden rounded-2xl border border-mist bg-white/70">
          <table className="w-full text-sm">
            <thead className="bg-mist/60 text-left text-xs text-oat">
              <tr>
                <th className="px-4 py-2.5 font-normal">功能</th>
                <th className="px-4 py-2.5 font-normal">消耗</th>
              </tr>
            </thead>
            <tbody>
              {rules.map((r) => (
                <tr key={r.purpose} className="border-t border-mist">
                  <td className="px-4 py-2.5 text-ink">
                    {PURPOSE_LABEL[r.purpose] ?? r.purpose}
                  </td>
                  <td className="px-4 py-2.5 text-ink/80">
                    <span className="inline-flex items-center gap-1.5">
                      <Sparkles className="h-3.5 w-3.5 text-moss" />
                      {r.unit === "PER_10K_CHARS"
                        ? `${r.points} 点 / 1 万字`
                        : `${r.points} 点 / 次`}
                    </span>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        <p className="mt-2 text-xs text-oat">
          选择题判分在本地完成，不消耗点数；AI 调用失败会自动退回点数。
        </p>
      </section>

      <section className="grid gap-6 lg:grid-cols-2">
        <div>
          <h2 className="mb-4 font-medium text-ink">我的订单</h2>
          {orders.length === 0 ? (
            <p className="rounded-2xl border border-mist bg-white/70 px-4 py-6 text-center text-sm text-oat">
              暂无订单
            </p>
          ) : (
            <div className="space-y-2">
              {orders.map((o) => (
                <div
                  key={o.id}
                  className="flex items-center justify-between rounded-xl border border-mist bg-white/70 px-4 py-3 text-sm"
                >
                  <div>
                    <p className="text-ink">
                      {o.kind === "PACK" ? "加量包" : "套餐"} · {o.item_code}
                      {o.period ? ` · ${o.period}` : ""}
                    </p>
                    <p className="mt-0.5 text-xs text-oat">
                      {new Date(o.created_at).toLocaleString("zh-CN")}
                    </p>
                  </div>
                  <div className="text-right">
                    <p className="text-ink">{yuan(o.amount_cents)}</p>
                    <p className="mt-0.5 text-xs text-oat">
                      {o.status === "PAID"
                        ? "已开通"
                        : o.status === "PENDING"
                          ? "待确认收款"
                          : o.status === "CANCELLED"
                            ? "已取消"
                            : "已退款"}
                    </p>
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>

        <div>
          <h2 className="mb-4 font-medium text-ink">点数明细</h2>
          {ledger.length === 0 ? (
            <p className="rounded-2xl border border-mist bg-white/70 px-4 py-6 text-center text-sm text-oat">
              暂无记录
            </p>
          ) : (
            <div className="max-h-80 space-y-2 overflow-y-auto pr-1">
              {ledger.map((l) => (
                <div
                  key={l.id}
                  className="flex items-center justify-between rounded-xl border border-mist bg-white/70 px-4 py-2.5 text-sm"
                >
                  <div>
                    <p className="text-ink">{REASON_LABEL[l.reason] ?? l.reason}</p>
                    <p className="mt-0.5 text-xs text-oat">
                      {new Date(l.created_at).toLocaleString("zh-CN")}
                    </p>
                  </div>
                  <div className="text-right">
                    <p className={l.delta >= 0 ? "text-moss" : "text-ink"}>
                      {l.delta >= 0 ? "+" : ""}
                      {l.delta}
                    </p>
                    <p className="mt-0.5 text-xs text-oat">余额 {l.balance_after}</p>
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      </section>
    </div>
  );
}
