import { requireUser, ok, ApiError, handleError } from "@/lib/server";
import { getAdminClient } from "@/lib/supabase/admin";
import { assertCanManageQuestion } from "@/lib/admin-scope";

export async function POST(req: Request, { params }: { params: Promise<{ id: string }> }) {
  try {
    const { profile } = await requireUser();
    const admin = getAdminClient();
    const questionId = Number((await params).id);
    await assertCanManageQuestion(profile, questionId);
    const body = await req.json().catch(() => ({}));
    const status = String(body.status ?? "").toUpperCase();
    if (status !== "ON" && status !== "OFF") throw new ApiError(400, "status 只能是 ON / OFF");
    const { error } = await admin.from("question").update({ status }).eq("id", questionId);
    if (error) throw new Error("更新失败：" + error.message);
    return ok(null);
  } catch (e) {
    return handleError(e);
  }
}