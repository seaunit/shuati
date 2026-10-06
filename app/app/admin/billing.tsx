"use client";

import { useEffect, useState } from "react";
import { RefreshCw, Check, X } from "lucide-react";
import { api } from "@/lib/client-api";

interface Plan {
  code: string;
  name: string;
  price_monthly_cents: number;
  price_quarterly_cents: number;
  price_yearly_cents: number;
  monthly_points: number;
  max_banks: number | null;
  max_questions: number | null;
  retention_days: number | null;
  status: string;
  sort: number;
}

interface Rule {
  purpose: string;
  points: number;
  unit: string;
}

interface Order {
  id: string;
  user_id: string;
  kind: string;
  item_code: string;
  period: string | null;
  amount_cents: number;
  points: number;
  status: string;
  created_at: string;
}

interface BillingData {
  plans: Plan[];
  rules: Rule[];
  configs: { key: string; value: unknown; description: string | null }[];
  orders: Order[];
  stats: {
    userCount: number;
    outstandingPoints: number;
    consumedPoints: number;
    paidRevenueCents: number;
    pendingOrders: number;
  };
}

const PURPOSE_LABEL: Record<string, string> = {
  JUDGE: "简答题判分",
  EXPLAIN: "选择题解析",
  EXTRACT: "文档解析生成题库",
  TEST: "连通性测试",
};

function yuan(cents: number) {
  const v = cents / 100;
  return "¥" + (Number.isInteger(v) ? v : v.toFixed(2));
}

export default function BillingTab() {
  const [data, setData] = useState<BillingData | null>(null);
  const [draft, setDraft] = useState<Record<string, Partial<Plan>>>({});
  const [ruleDraft, setRuleDraft] = useState<Record<string, number>>({});
  const [bonusDraft, setBonusDraft] = useState<string>("");
  const [error, setError] = useState("");
  const [msg, setMsg] = useState("");
  const [busy, setBusy] = useState("");

  function load() {
    api<BillingData>("/api/admin/billing")
      .then((d) => {
        setData(d);
        const b = d.configs.find((c) => c.key === "signup_bonus_points");
        setBonusDraft(String(b?.value ?? 100));
      })
      .catch((e) => setError((e as Error).message));
  }

  useEffect(load, []);

  function editPlan(code: string, field: keyof Plan, value: string) {
    const numeric = field !== "status";
    setDraft((d) => ({
      ...d,
      [code]: { ...d[code], [field]: numeric ? (value === "" ? null : Number(value)) : value },
    }));
  }

  function planValue(p: Plan, field: keyof Plan) {
    const d = draft[p.code];
    return d && d[field] !== undefined ? (d[field] as never) : (p[field] as never);
  }

  async function savePlan(p: Plan) {
    const patch = draft[p.code];
    if (!patch || !Object.keys(patch).length) return;
    setBusy("plan:" + p.code);
    setError("");
    setMsg("");
    try {
      await api("/api/admin/billing", {
        method: "PATCH",
        body: JSON.stringify({ type: "PLAN", code: p.code, ...patch }),
      });
      setDraft((d) => ({ ...d, [p.code]: {} }));
      setMsg(`套餐「${p.name}」已更新`);
      load();
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy("");
    }
  }

  async function saveRule(purpose: string) {
    const points = ruleDraft[purpose];
    if (points === undefined) return;
    setBusy("rule:" + purpose);
    setError("");
    try {
      await api("/api/admin/billing", {
        method: "PATCH",
        body: JSON.stringify({ type: "RULE", purpose, points }),
      });
      setRuleDraft((d) => {
        const next = { ...d };
        delete next[purpose];
        return next;
      });
      setMsg("计费规则已更新");
      load();
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy("");
    }
  }

  async function saveBonus() {
    setBusy("bonus");
    setError("");
    try {
      await api("/api/admin/billing", {
        method: "PATCH",
        body: JSON.stringify({
          type: "CONFIG",
          key: "signup_bonus_points",
          value: Number(bonusDraft) || 0,
        }),
      });
      setMsg("注册赠送点数已更新");
      load();
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy("");
    }
  }

  async function settle(o: Order, action: "PAID" | "CANCELLED") {
    if (action === "PAID" && !confirm(`确认已收到 ${yuan(o.amount_cents)} 并开通？`)) return;
    setBusy("order:" + o.id);
    setError("");
    try {
      await api(`/api/admin/orders/${o.id}/settle`, {
        method: "POST",
        body: JSON.stringify({ action }),
      });
      setMsg(action === "PAID" ? "订单已结算并开通" : "订单已取消");
      load();
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy("");
    }
  }

  if (!data) {
    return <div className="p-10 text-center text-sm text-oat">加载中…</div>;
  }

  return (
    <div className="space-y-6">
      {error && <p className="rounded-xl bg-rose/10 px-4 py-2.5 text-sm text-rose">{error}</p>}
      {msg && <p className="rounded-xl bg-moss/10 px-4 py-2.5 text-sm text-ink">{msg}</p>}

      <div className="grid gap-3 sm:grid-cols-5">
        {[
          { label: "计费用户", value: data.stats.userCount },
          { label: "未使用点数", value: data.stats.outstandingPoints },
          { label: "累计消耗点数", value: data.stats.consumedPoints },
          { label: "已收款", value: yuan(data.stats.paidRevenueCents) },
          { label: "待处理订单", value: data.stats.pendingOrders },
        ].map((s) => (
          <div key={s.label} className="rounded-2xl border border-mist bg-white/70 px-4 py-3">
            <p className="text-xs text-oat">{s.label}</p>
            <p className="mt-1 text-lg font-medium text-ink">{s.value}</p>
          </div>
        ))}
      </div>

      <section>
        <div className="mb-3 flex items-center justify-between">
          <h3 className="font-medium text-ink">套餐配置</h3>
          <button
            onClick={load}
            className="inline-flex items-center gap-1 rounded-lg border border-mist px-3 py-1.5 text-xs text-ink/70 hover:border-moss"
          >
            <RefreshCw className="h-3.5 w-3.5" /> 刷新
          </button>
        </div>
        <div className="overflow-x-auto rounded-2xl border border-mist bg-white/70">
          <table className="w-full min-w-[900px] text-sm">
            <thead className="bg-mist/60 text-left text-xs text-oat">
              <tr>
                <th className="px-3 py-2.5 font-normal">套餐</th>
                <th className="px-3 py-2.5 font-normal">月 / 季 / 年（分）</th>
                <th className="px-3 py-2.5 font-normal">月点数</th>
                <th className="px-3 py-2.5 font-normal">题库上限</th>
                <th className="px-3 py-2.5 font-normal">题量上限</th>
                <th className="px-3 py-2.5 font-normal">状态</th>
                <th className="px-3 py-2.5 font-normal"></th>
              </tr>
            </thead>
            <tbody>
              {data.plans.map((p) => {
                const dirty = Object.keys(draft[p.code] ?? {}).length > 0;
                const cell =
                  "w-20 rounded-lg border border-mist bg-paper px-2 py-1 text-sm outline-none focus:border-moss";
                return (
                  <tr key={p.code} className="border-t border-mist">
                    <td className="px-3 py-2.5">
                      <p className="font-medium text-ink">{p.name}</p>
                      <p className="text-xs text-oat">{p.code}</p>
                    </td>
                    <td className="px-3 py-2.5">
                      <div className="flex gap-1.5">
                        <input
                          className={cell}
                          value={String(planValue(p, "price_monthly_cents") ?? "")}
                          onChange={(e) => editPlan(p.code, "price_monthly_cents", e.target.value)}
                        />
                        <input
                          className={cell}
                          value={String(planValue(p, "price_quarterly_cents") ?? "")}
                          onChange={(e) =>
                            editPlan(p.code, "price_quarterly_cents", e.target.value)
                          }
                        />
                        <input
                          className={cell}
                          value={String(planValue(p, "price_yearly_cents") ?? "")}
                          onChange={(e) => editPlan(p.code, "price_yearly_cents", e.target.value)}
                        />
                      </div>
                    </td>
                    <td className="px-3 py-2.5">
                      <input
                        className={cell}
                        value={String(planValue(p, "monthly_points") ?? "")}
                        onChange={(e) => editPlan(p.code, "monthly_points", e.target.value)}
                      />
                    </td>
                    <td className="px-3 py-2.5">
                      <input
                        className={cell}
                        placeholder="不限"
                        value={String(planValue(p, "max_banks") ?? "")}
                        onChange={(e) => editPlan(p.code, "max_banks", e.target.value)}
                      />
                    </td>
                    <td className="px-3 py-2.5">
                      <input
                        className={cell}
                        placeholder="不限"
                        value={String(planValue(p, "max_questions") ?? "")}
                        onChange={(e) => editPlan(p.code, "max_questions", e.target.value)}
                      />
                    </td>
                    <td className="px-3 py-2.5">
                      <select
                        className="rounded-lg border border-mist bg-paper px-2 py-1 text-sm outline-none focus:border-moss"
                        value={String(planValue(p, "status") ?? "ENABLED")}
                        onChange={(e) => editPlan(p.code, "status", e.target.value)}
                      >
                        <option value="ENABLED">启用</option>
                        <option value="DISABLED">停用</option>
                      </select>
                    </td>
                    <td className="px-3 py-2.5">
                      <button
                        disabled={!dirty || busy !== ""}
                        onClick={() => savePlan(p)}
                        className="rounded-lg bg-ink px-3 py-1.5 text-xs text-white disabled:opacity-40"
                      >
                        {busy === "plan:" + p.code ? "保存中…" : "保存"}
                      </button>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
        <p className="mt-2 text-xs text-oat">
          价格单位为「分」，填 0 表示需线下定制；题库与题量留空表示不限。
        </p>
      </section>

      <section className="grid gap-6 lg:grid-cols-2">
        <div>
          <h3 className="mb-3 font-medium text-ink">AI 计费规则</h3>
          <div className="space-y-2">
            {data.rules.map((r) => (
              <div
                key={r.purpose}
                className="flex items-center justify-between rounded-xl border border-mist bg-white/70 px-4 py-2.5"
              >
                <div>
                  <p className="text-sm text-ink">{PURPOSE_LABEL[r.purpose] ?? r.purpose}</p>
                  <p className="text-xs text-oat">
                    {r.unit === "PER_10K_CHARS" ? "按每 1 万字计费" : "按次计费"}
                  </p>
                </div>
                <div className="flex items-center gap-2">
                  <input
                    className="w-20 rounded-lg border border-mist bg-paper px-2 py-1 text-sm outline-none focus:border-moss"
                    value={String(ruleDraft[r.purpose] ?? r.points)}
                    onChange={(e) =>
                      setRuleDraft((d) => ({ ...d, [r.purpose]: Number(e.target.value) }))
                    }
                  />
                  <button
                    disabled={ruleDraft[r.purpose] === undefined || busy !== ""}
                    onClick={() => saveRule(r.purpose)}
                    className="rounded-lg border border-mist px-3 py-1 text-xs text-ink/70 hover:border-moss disabled:opacity-40"
                  >
                    保存
                  </button>
                </div>
              </div>
            ))}
          </div>

          <h3 className="mb-3 mt-6 font-medium text-ink">新用户注册赠送</h3>
          <div className="flex items-center gap-2 rounded-xl border border-mist bg-white/70 px-4 py-3">
            <input
              className="w-24 rounded-lg border border-mist bg-paper px-2 py-1 text-sm outline-none focus:border-moss"
              value={bonusDraft}
              onChange={(e) => setBonusDraft(e.target.value)}
            />
            <span className="text-sm text-oat">点</span>
            <button
              disabled={busy !== ""}
              onClick={saveBonus}
              className="ml-auto rounded-lg border border-mist px-3 py-1 text-xs text-ink/70 hover:border-moss disabled:opacity-40"
            >
              保存
            </button>
          </div>
        </div>

        <div>
          <h3 className="mb-3 font-medium text-ink">订单</h3>
          {data.orders.length === 0 ? (
            <p className="rounded-2xl border border-mist bg-white/70 px-4 py-6 text-center text-sm text-oat">
              暂无订单
            </p>
          ) : (
            <div className="max-h-96 space-y-2 overflow-y-auto pr-1">
              {data.orders.map((o) => (
                <div
                  key={o.id}
                  className="flex items-center justify-between rounded-xl border border-mist bg-white/70 px-4 py-2.5"
                >
                  <div className="min-w-0">
                    <p className="truncate text-sm text-ink">
                      {o.kind === "PACK" ? "加量包" : "套餐"} · {o.item_code}
                      {o.period ? ` · ${o.period}` : ""}
                    </p>
                    <p className="mt-0.5 text-xs text-oat">
                      {yuan(o.amount_cents)} · {o.points} 点 ·{" "}
                      {new Date(o.created_at).toLocaleString("zh-CN")}
                    </p>
                  </div>
                  {o.status === "PENDING" ? (
                    <div className="flex shrink-0 gap-1.5">
                      <button
                        disabled={busy !== ""}
                        onClick={() => settle(o, "PAID")}
                        className="inline-flex items-center gap-1 rounded-lg bg-moss px-2.5 py-1 text-xs text-white disabled:opacity-40"
                      >
                        <Check className="h-3.5 w-3.5" /> 已收款
                      </button>
                      <button
                        disabled={busy !== ""}
                        onClick={() => settle(o, "CANCELLED")}
                        className="inline-flex items-center gap-1 rounded-lg border border-mist px-2.5 py-1 text-xs text-ink/70 disabled:opacity-40"
                      >
                        <X className="h-3.5 w-3.5" /> 取消
                      </button>
                    </div>
                  ) : (
                    <span className="shrink-0 text-xs text-oat">
                      {o.status === "PAID" ? "已开通" : o.status === "CANCELLED" ? "已取消" : "已退款"}
                    </span>
                  )}
                </div>
              ))}
            </div>
          )}
        </div>
      </section>
    </div>
  );
}
