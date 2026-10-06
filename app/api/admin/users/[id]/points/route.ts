import { requireAdmin, ok, ApiError, handleError } from "@/lib/server";
import { getAdminClient } from "@/lib/supabase/admin";
import { getEntitlements } from "@/lib/points";

export async function GET(_req: Request, { params }: { params: Promise<{ id: string }> }) {
  try {
    await requireAdmin();
    const userId = (await params).id;
    const ent = await getEntitlements(userId);
    return ok(ent);
  } catch (e) {
    return handleError(e);
  }
}

/** 管理员手动增减点数或调整套餐 */
export async function POST(req: Request, { params }: { params: Promise<{ id: string }> }) {
  try {
    await requireAdmin();
    const admin = getAdminClient();
    const userId = (await params).id;
    const body = await req.json().catch(() => ({}));
    const action = String(body.action ?? "").toUpperCase();

    if (action === "GRANT") {
      const points = Math.round(Number(body.points));
      if (!Number.isFinite(points) || points === 0) throw new ApiError(400, "请输入非零点数");
      if (points > 0) {
        const { error } = await admin.rpc("add_bonus_points", {
          p_user_id: userId,
          p_points: points,
          p_reason: "ADMIN_ADJUST",
          p_ref_type: "admin",
          p_ref_id: null,
        });
        if (error) throw new Error("发放点数失败：" + error.message);
      } else {
        const { error } = await admin.rpc("consume_points", {
          p_user_id: userId,
          p_points: Math.abs(points),
          p_reason: "ADMIN_ADJUST",
          p_ref_type: "admin",
          p_ref_id: null,
        });
        if (error) throw new Error("扣减点数失败：" + error.message);
      }
      return ok(null);
    }

    if (action === "SET_PLAN") {
      const planCode = String(body.planCode ?? "").trim();
      if (!planCode) throw new ApiError(400, "缺少套餐编码");
      const days = Number(body.days) || 30;
      const expires = new Date(Date.now() + days * 24 * 60 * 60 * 1000).toISOString();
      const { error } = await admin.rpc("apply_plan", {
        p_user_id: userId,
        p_plan_code: planCode,
        p_expires_at: planCode === "free" ? null : expires,
      });
      if (error) throw new Error("调整套餐失败：" + error.message);
      return ok(null);
    }

    throw new ApiError(400, "不支持的操作");
  } catch (e) {
    return handleError(e);
  }
}
