import { requireAdmin, ok, ApiError, handleError } from "@/lib/server";
import { getAdminClient } from "@/lib/supabase/admin";

function expiryFor(period: string | null, from = new Date()): string {
  const d = new Date(from);
  if (period === "YEARLY") d.setFullYear(d.getFullYear() + 1);
  else if (period === "QUARTERLY") d.setMonth(d.getMonth() + 3);
  else d.setMonth(d.getMonth() + 1);
  return d.toISOString();
}

/** 人工确认收款后结算订单：发放点数或开通套餐 */
export async function POST(req: Request, { params }: { params: Promise<{ id: string }> }) {
  try {
    await requireAdmin();
    const admin = getAdminClient();
    const orderId = (await params).id;
    const body = await req.json().catch(() => ({}));
    const action = String(body.action ?? "PAID").toUpperCase();

    const { data: order } = await admin
      .from("subscription_order")
      .select("*")
      .eq("id", orderId)
      .maybeSingle();
    if (!order) throw new ApiError(404, "订单不存在");
    if (order.status === "PAID") throw new ApiError(400, "订单已结算，请勿重复操作");

    if (action === "CANCELLED") {
      const { error } = await admin
        .from("subscription_order")
        .update({ status: "CANCELLED" })
        .eq("id", orderId);
      if (error) throw new Error("更新订单失败：" + error.message);
      return ok(null);
    }

    if (action !== "PAID") throw new ApiError(400, "不支持的结算操作");

    if (order.kind === "PACK") {
      const points = Number(order.points);
      if (points > 0) {
        const { error } = await admin.rpc("add_bonus_points", {
          p_user_id: order.user_id,
          p_points: points,
          p_reason: "PACK_PURCHASE",
          p_ref_type: "order",
          p_ref_id: orderId,
        });
        if (error) throw new Error("发放点数失败：" + error.message);
      }
    } else {
      const expiresAt = expiryFor(order.period, new Date());
      const { error } = await admin.rpc("apply_plan", {
        p_user_id: order.user_id,
        p_plan_code: order.item_code,
        p_expires_at: expiresAt,
      });
      if (error) throw new Error("开通套餐失败：" + error.message);
    }

    const { error: updErr } = await admin
      .from("subscription_order")
      .update({ status: "PAID", paid_at: new Date().toISOString(), provider: order.provider ?? "manual" })
      .eq("id", orderId);
    if (updErr) throw new Error("更新订单失败：" + updErr.message);

    return ok(null);
  } catch (e) {
    return handleError(e);
  }
}
