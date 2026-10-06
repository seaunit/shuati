import { requireAdmin, ok, ApiError, handleError } from "@/lib/server";
import { getAdminClient } from "@/lib/supabase/admin";

export async function GET() {
  try {
    await requireAdmin();
    const admin = getAdminClient();
    const [plans, packs, rules, configs, orders, accounts] = await Promise.all([
      admin.from("plan").select("*").order("sort", { ascending: true }),
      admin.from("point_pack").select("*").order("sort", { ascending: true }),
      admin.from("ai_price_rule").select("*").order("purpose", { ascending: true }),
      admin.from("billing_config").select("*").order("key", { ascending: true }),
      admin
        .from("subscription_order")
        .select("id,user_id,kind,item_code,period,amount_cents,points,status,created_at,paid_at")
        .order("created_at", { ascending: false })
        .limit(100),
      admin.from("point_account").select("monthly_quota,monthly_used,bonus_balance,lifetime_used"),
    ]);

    const accRows = accounts.data ?? [];
    const orderRows = orders.data ?? [];
    const outs = accRows.reduce(
      (s, r) =>
        s +
        Math.max(Number(r.monthly_quota) - Number(r.monthly_used), 0) +
        Number(r.bonus_balance),
      0,
    );
    const used = accRows.reduce((s, r) => s + Number(r.lifetime_used), 0);
    const revenue = orderRows
      .filter((o) => o.status === "PAID")
      .reduce((s, o) => s + Number(o.amount_cents), 0);

    return ok({
      plans: plans.data ?? [],
      packs: packs.data ?? [],
      rules: rules.data ?? [],
      configs: configs.data ?? [],
      orders: orderRows,
      stats: {
        userCount: accRows.length,
        outstandingPoints: outs,
        consumedPoints: used,
        paidRevenueCents: revenue,
        pendingOrders: orderRows.filter((o) => o.status === "PENDING").length,
      },
    });
  } catch (e) {
    return handleError(e);
  }
}

const PLAN_FIELDS = [
  "name",
  "tagline",
  "description",
  "price_monthly_cents",
  "price_quarterly_cents",
  "price_yearly_cents",
  "monthly_points",
  "max_banks",
  "max_questions",
  "retention_days",
  "allow_pro_model",
  "show_on_pricing",
  "status",
  "sort",
] as const;

const PACK_FIELDS = ["name", "price_cents", "points", "bonus_points", "sort", "status"] as const;

export async function PATCH(req: Request) {
  try {
    await requireAdmin();
    const admin = getAdminClient();
    const body = await req.json().catch(() => ({}));
    const type = String(body.type ?? "").toUpperCase();

    if (type === "PLAN") {
      const code = String(body.code ?? "").trim();
      if (!code) throw new ApiError(400, "缺少套餐编码");
      const patch: Record<string, unknown> = {};
      for (const f of PLAN_FIELDS) if (body[f] !== undefined) patch[f] = body[f];
      if (body.features !== undefined && Array.isArray(body.features)) patch.features = body.features;
      if (!Object.keys(patch).length) throw new ApiError(400, "没有需要更新的字段");
      const { error } = await admin.from("plan").update(patch as never).eq("code", code);
      if (error) throw new Error("更新套餐失败：" + error.message);
      return ok(null);
    }

    if (type === "PACK") {
      const code = String(body.code ?? "").trim();
      if (!code) throw new ApiError(400, "缺少加量包编码");
      const patch: Record<string, unknown> = {};
      for (const f of PACK_FIELDS) if (body[f] !== undefined) patch[f] = body[f];
      if (!Object.keys(patch).length) throw new ApiError(400, "没有需要更新的字段");
      const { error } = await admin.from("point_pack").update(patch as never).eq("code", code);
      if (error) throw new Error("更新加量包失败：" + error.message);
      return ok(null);
    }

    if (type === "RULE") {
      const purpose = String(body.purpose ?? "").toUpperCase();
      if (!purpose) throw new ApiError(400, "缺少计费用途");
      const points = Number(body.points);
      if (!Number.isFinite(points) || points < 0) throw new ApiError(400, "点数必须是非负整数");
      const { error } = await admin
        .from("ai_price_rule")
        .update({ points: Math.round(points) })
        .eq("purpose", purpose);
      if (error) throw new Error("更新计费规则失败：" + error.message);
      return ok(null);
    }

    if (type === "CONFIG") {
      const key = String(body.key ?? "").trim();
      if (!key) throw new ApiError(400, "缺少配置项");
      const { error } = await admin
        .from("billing_config")
        .update({ value: body.value })
        .eq("key", key);
      if (error) throw new Error("更新配置失败：" + error.message);
      return ok(null);
    }

    throw new ApiError(400, "不支持的更新类型");
  } catch (e) {
    return handleError(e);
  }
}
