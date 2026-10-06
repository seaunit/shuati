import { requireUser, ok, handleError } from "@/lib/server";
import { getEntitlements, getLedger, getPriceRules } from "@/lib/points";

export async function GET() {
  try {
    const { user } = await requireUser();
    const [entitlements, ledger, rules] = await Promise.all([
      getEntitlements(user.id),
      getLedger(user.id, 50),
      getPriceRules(),
    ]);
    return ok({ entitlements, ledger, rules: Object.values(rules) });
  } catch (e) {
    return handleError(e);
  }
}
