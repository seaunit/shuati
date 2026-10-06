import { requireUser, ok, ApiError, handleError } from "@/lib/server";
import { getAdminClient } from "@/lib/supabase/admin";

export async function GET() {
  try {
    const { user } = await requireUser();
    const admin = getAdminClient();
    const { data } = await admin
      .from("subscription_order")
      .select("*")
      .eq("user_id", user.id)
      .order("created_at", { ascending: false })
      .limit(50);
    return ok(data ?? []);
  } catch (e) {
    return handleError(e);
  }
}

/**
 * 创建订单（待支付）。
 * 金额与点数一律以数据库目录为准，不接受客户端传入价格。
 */
export async function POST(req: Request) {
  try {
    const { user } = await requireUser();
    const admin = getAdminClient();
    const body = await req.json().catch(() => ({}));
    const kind = String(body.kind ?? "").toUpperCase();
    const itemCode = String(body.itemCode ?? "").trim();
    const period = body.period ? String(body.period).toUpperCase() : null;

    if (kind !== "PLAN" && kind !== "PACK") throw new ApiError(400, "订单类型不正确");
    if (!itemCode) throw new ApiError(400, "缺少商品编码");

    let amountCents = 0;
    let points = 0;

    if (kind === "PACK") {
      const { data: pack } = await admin
        .from("point_pack")
        .select("code,price_cents,points,bonus_points,status")
        .eq("code", itemCode)
        .maybeSingle();
      if (!pack || pack.status !== "ENABLED") throw new ApiError(404, "加量包不存在或已下架");
      amountCents = Number(pack.price_cents);
      points = Number(pack.points) + Number(pack.bonus_points ?? 0);
    } else {
      if (!period || !["MONTHLY", "QUARTERLY", "YEARLY"].includes(period)) {
        throw new ApiError(400, "请选择订阅周期");
      }
      const { data: plan } = await admin
        .from("plan")
        .select("code,name,status,price_monthly_cents,price_quarterly_cents,price_yearly_cents")
        .eq("code", itemCode)
        .maybeSingle();
      if (!plan || plan.status !== "ENABLED") throw new ApiError(404, "套餐不存在或已下架");
      const priceMap: Record<string, number> = {
        MONTHLY: Number(plan.price_monthly_cents),
        QUARTERLY: Number(plan.price_quarterly_cents),
        YEARLY: Number(plan.price_yearly_cents),
      };
      amountCents = priceMap[period];
      if (amountCents <= 0) throw new ApiError(400, "该套餐暂不支持在线购买，请联系管理员");
    }

    const { data: order, error } = await admin
      .from("subscription_order")
      .insert({
        user_id: user.id,
        kind,
        item_code: itemCode,
        period,
        amount_cents: amountCents,
        points,
        status: "PENDING",
      })
      .select("id,status,amount_cents,points,kind,item_code,period,created_at")
      .single();
    if (error) throw new Error("创建订单失败：" + error.message);

    return ok(order);
  } catch (e) {
    return handleError(e);
  }
}
