import { ok, handleError } from "@/lib/server";
import { getAdminClient } from "@/lib/supabase/admin";
import { getPriceRules } from "@/lib/points";

/** 套餐目录为公开信息，未登录也可查看 */
export async function GET() {
  try {
    const admin = getAdminClient();
    const [plans, packs, rules, bonus] = await Promise.all([
      admin
        .from("plan")
        .select("*")
        .eq("status", "ENABLED")
        .eq("show_on_pricing", true)
        .order("sort", { ascending: true }),
      admin
        .from("point_pack")
        .select("code,name,price_cents,points,bonus_points,sort")
        .eq("status", "ENABLED")
        .order("sort", { ascending: true }),
      getPriceRules(),
      admin
        .from("billing_config")
        .select("value")
        .eq("key", "signup_bonus_points")
        .maybeSingle(),
    ]);

    const signupBonus = Number(
      (bonus.data?.value as unknown as number | undefined) ?? 100,
    );

    return ok({
      plans: plans.data ?? [],
      packs: packs.data ?? [],
      rules: Object.values(rules),
      signupBonus,
    });
  } catch (e) {
    return handleError(e);
  }
}
