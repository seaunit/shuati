import { requireAdmin, ok, ApiError, handleError } from "@/lib/server";
import { getAdminClient } from "@/lib/supabase/admin";

export async function POST(req: Request, { params }: { params: Promise<{ id: string }> }) {
  try {
    await requireAdmin();
    const admin = getAdminClient();
    const id = Number((await params).id);
    const body = await req.json().catch(() => ({}));
    const status = String(body.status ?? "").toUpperCase();
    if (status !== "RESOLVED" && status !== "REJECTED") throw new ApiError(400, "status 只能是 RESOLVED / REJECTED");
    const { error } = await admin
      .from("appeal")
      .update({ status, admin_note: body.adminNote ?? null, resolved_at: new Date().toISOString() })
      .eq("id", id);
    if (error) throw new Error("处理失败：" + error.message);
    return ok(null);
  } catch (e) {
    return handleError(e);
  }
}