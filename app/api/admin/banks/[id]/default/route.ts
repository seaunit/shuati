import { requireAdmin, ok, handleError } from "@/lib/server";
import { getAdminClient } from "@/lib/supabase/admin";

export async function POST(_req: Request, { params }: { params: Promise<{ id: string }> }) {
  try {
    await requireAdmin();
    const admin = getAdminClient();
    const bankId = Number((await params).id);
    const { error } = await admin.from("bank").update({ is_default: false }).eq("is_default", true);
    if (error) throw new Error("清除默认失败：" + error.message);
    const { error: e2 } = await admin.from("bank").update({ is_default: true }).eq("id", bankId);
    if (e2) throw new Error("设置默认失败：" + e2.message);
    return ok(null);
  } catch (e) {
    return handleError(e);
  }
}