import { getAdminClient } from "./supabase/admin";
import { ApiError } from "./api-error";
import type { AiPurpose } from "./ai";

export type PointUnit = "CALL" | "PER_10K_CHARS";

export interface PriceRule {
  purpose: AiPurpose;
  points: number;
  unit: PointUnit;
  description: string | null;
}

export interface Entitlements {
  planCode: string;
  planName: string;
  planExpiresAt: string | null;
  monthlyQuota: number;
  monthlyUsed: number;
  monthlyLeft: number;
  bonusBalance: number;
  available: number;
  lifetimeUsed: number;
  periodStart: string;
  periodEnd: string;
  maxBanks: number | null;
  maxQuestions: number | null;
  retentionDays: number | null;
  allowProModel: boolean;
}

export interface ConsumeResult {
  ok: boolean;
  cost: number;
  available: number;
  /** 迁移尚未执行时跳过计费，保证 AI 功能可用 */
  skipped?: boolean;
}

export class InsufficientPointsError extends ApiError {
  cost: number;
  available: number;
  constructor(cost: number, available: number) {
    super(
      402,
      `AI 点数不足：本次需要 ${cost} 点，当前可用 ${available} 点。可在「套餐与点数」升级套餐或购买加量包。`,
    );
    this.cost = cost;
    this.available = available;
  }
}

// 数据库不可用或规则缺失时的兜底价格，与迁移种子保持一致
const FALLBACK_RULES: Record<string, PriceRule> = {
  EXPLAIN: { purpose: "EXPLAIN", points: 1, unit: "CALL", description: "选择题 AI 解析" },
  JUDGE: { purpose: "JUDGE", points: 3, unit: "CALL", description: "简答题 / 伪代码 AI 判分" },
  EXTRACT: { purpose: "EXTRACT", points: 10, unit: "PER_10K_CHARS", description: "AI 解析生成题库" },
  TEST: { purpose: "TEST", points: 0, unit: "CALL", description: "连通性测试" },
};

function isMissingBilling(error: { code?: string; message?: string } | null | undefined) {
  if (!error) return false;
  const code = error.code ?? "";
  const msg = (error.message ?? "").toLowerCase();
  return (
    code === "42883" ||
    code === "42P01" ||
    code === "PGRST202" ||
    msg.includes("does not exist") ||
    msg.includes("could not find the function") ||
    msg.includes("schema cache")
  );
}

export function costOf(rule: PriceRule, chars?: number): number {
  if (!rule || rule.points <= 0) return 0;
  if (rule.unit === "PER_10K_CHARS") {
    const units = Math.max(1, Math.ceil((chars ?? 0) / 10_000));
    return rule.points * units;
  }
  return rule.points;
}

export async function getPriceRules(): Promise<Record<string, PriceRule>> {
  const admin = getAdminClient();
  const { data, error } = await admin
    .from("ai_price_rule")
    .select("purpose,points,unit,description");
  if (error || !data?.length) return FALLBACK_RULES;
  const rules: Record<string, PriceRule> = { ...FALLBACK_RULES };
  for (const row of data) {
    rules[String(row.purpose)] = {
      purpose: row.purpose as AiPurpose,
      points: Number(row.points) || 0,
      unit: (row.unit as PointUnit) ?? "CALL",
      description: (row.description as string | null) ?? null,
    };
  }
  return rules;
}

export async function getEntitlements(userId: string): Promise<Entitlements> {
  const admin = getAdminClient();

  let { data: acc, error } = await admin
    .from("point_account")
    .select(
      "plan_code,plan_expires_at,monthly_quota,monthly_used,bonus_balance,lifetime_used,period_start,period_end",
    )
    .eq("user_id", userId)
    .maybeSingle();

  if (error || !acc) {
    await admin.rpc("ensure_point_account", { p_user_id: userId });
    const retry = await admin
      .from("point_account")
      .select(
        "plan_code,plan_expires_at,monthly_quota,monthly_used,bonus_balance,lifetime_used,period_start,period_end",
      )
      .eq("user_id", userId)
      .maybeSingle();
    acc = retry.data;
  } else {
    // 周期到期时补发额度（幂等）
    await admin.rpc("renew_point_period", { p_user_id: userId });
    const refreshed = await admin
      .from("point_account")
      .select(
        "plan_code,plan_expires_at,monthly_quota,monthly_used,bonus_balance,lifetime_used,period_start,period_end",
      )
      .eq("user_id", userId)
      .maybeSingle();
    acc = refreshed.data ?? acc;
  }

  const planCode = String(acc?.plan_code ?? "free");
  const { data: plan } = await admin
    .from("plan")
    .select(
      "code,name,monthly_points,max_banks,max_questions,retention_days,allow_pro_model",
    )
    .eq("code", planCode)
    .maybeSingle();

  const monthlyQuota = Number(acc?.monthly_quota ?? plan?.monthly_points ?? 0);
  const monthlyUsed = Number(acc?.monthly_used ?? 0);
  const bonusBalance = Number(acc?.bonus_balance ?? 0);
  const monthlyLeft = Math.max(monthlyQuota - monthlyUsed, 0);

  return {
    planCode,
    planName: String(plan?.name ?? "免费版"),
    planExpiresAt: (acc?.plan_expires_at as string | null) ?? null,
    monthlyQuota,
    monthlyUsed,
    monthlyLeft,
    bonusBalance,
    available: monthlyLeft + bonusBalance,
    lifetimeUsed: Number(acc?.lifetime_used ?? 0),
    periodStart: String(acc?.period_start ?? new Date().toISOString()),
    periodEnd: String(acc?.period_end ?? new Date().toISOString()),
    maxBanks: (plan?.max_banks as number | null | undefined) ?? 1,
    maxQuestions: (plan?.max_questions as number | null | undefined) ?? 50,
    retentionDays: (plan?.retention_days as number | null | undefined) ?? 30,
    allowProModel: Boolean(plan?.allow_pro_model ?? false),
  };
}

export async function consumePoints(
  userId: string,
  purpose: AiPurpose,
  opts: { chars?: number; refType?: string; refId?: string; reason?: string } = {},
): Promise<ConsumeResult> {
  const rules = await getPriceRules();
  const rule = rules[purpose];
  const cost = costOf(rule, opts.chars);
  if (cost <= 0) return { ok: true, cost: 0, available: -1 };

  const admin = getAdminClient();
  const { data, error } = await admin.rpc("consume_points", {
    p_user_id: userId,
    p_points: cost,
    p_reason: opts.reason ?? `AI_${purpose}`,
    p_ref_type: opts.refType ?? null,
    p_ref_id: opts.refId ?? null,
  });

  if (error) {
    if (isMissingBilling(error)) {
      console.warn("[points] billing schema not installed, skip charging:", error.message);
      return { ok: true, cost: 0, available: -1, skipped: true };
    }
    throw new Error("点数结算失败：" + error.message);
  }

  const result = (data ?? {}) as { ok?: boolean; cost?: number; available?: number };
  return {
    ok: Boolean(result.ok),
    cost: Number(result.cost ?? cost),
    available: Number(result.available ?? 0),
  };
}

/** 扣点后调用失败时补偿，失败不影响主流程 */
export async function refundPoints(
  userId: string,
  cost: number,
  reason: string,
  refType?: string,
  refId?: string,
) {
  if (!cost || cost <= 0) return;
  try {
    const admin = getAdminClient();
    const { error } = await admin.rpc("refund_points", {
      p_user_id: userId,
      p_points: cost,
      p_reason: reason,
      p_ref_type: refType ?? null,
      p_ref_id: refId ?? null,
    });
    if (error && !isMissingBilling(error)) {
      console.error("[points] refund failed:", error.message);
    }
  } catch (e) {
    console.error("[points] refund error:", e instanceof Error ? e.message : String(e));
  }
}

export interface LedgerRow {
  id: number;
  delta: number;
  bucket: string;
  balance_after: number;
  reason: string;
  note: string | null;
  created_at: string;
}

export async function getLedger(userId: string, limit = 50): Promise<LedgerRow[]> {
  const admin = getAdminClient();
  const { data, error } = await admin
    .from("point_ledger")
    .select("id,delta,bucket,balance_after,reason,note,created_at")
    .eq("user_id", userId)
    .order("created_at", { ascending: false })
    .limit(limit);
  if (error) return [];
  return (data ?? []) as LedgerRow[];
}

/** 业务额度校验：超出自建题库 / 题量上限时抛错 */
export async function assertBankQuota(userId: string, ent: Entitlements) {
  if (ent.maxBanks === null) return;
  const admin = getAdminClient();
  const { count } = await admin
    .from("bank")
    .select("id", { count: "exact", head: true })
    .eq("owner_id", userId);
  if ((count ?? 0) >= ent.maxBanks) {
    throw new ApiError(
      402,
      `当前套餐最多创建 ${ent.maxBanks} 个自建题库，已用 ${count} 个。升级套餐可提升上限。`,
    );
  }
}

export async function assertQuestionQuota(
  userId: string,
  ent: Entitlements,
  incoming: number,
) {
  if (ent.maxQuestions === null) return;
  const admin = getAdminClient();
  const { data: banks } = await admin.from("bank").select("id").eq("owner_id", userId);
  const bankIds = (banks ?? []).map((b) => b.id as number);
  if (!bankIds.length) return;
  const { data: units } = await admin.from("unit").select("id").in("bank_id", bankIds);
  const unitIds = (units ?? []).map((u) => u.id as number);
  if (!unitIds.length) return;
  const { count } = await admin
    .from("question")
    .select("id", { count: "exact", head: true })
    .in("unit_id", unitIds);
  const used = count ?? 0;
  if (used + incoming > ent.maxQuestions) {
    throw new ApiError(
      402,
      `当前套餐自建题量上限 ${ent.maxQuestions} 题，已用 ${used} 题，本次将新增 ${incoming} 题。升级套餐可提升上限。`,
    );
  }
}
