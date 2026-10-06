import { requireUser, ok, ApiError, handleError } from "@/lib/server";
import { getAdminClient } from "@/lib/supabase/admin";

export async function PATCH(req: Request, { params }: { params: Promise<{ id: string }> }) {
  try {
    const { user } = await requireUser();
    const id = Number((await params).id);
    if (!id) throw new ApiError(400, "缺少练习记录 ID");

    const body = await req.json().catch(() => ({}));
    const status = body.status === "COMPLETED" || body.status === "ABANDONED" ? body.status : "IN_PROGRESS";
    const patch: Record<string, unknown> = {
      answered: Math.max(0, Number(body.answered) || 0),
      correct: Math.max(0, Number(body.correct) || 0),
      partial: Math.max(0, Number(body.partial) || 0),
      wrong: Math.max(0, Number(body.wrong) || 0),
      score: body.score == null ? null : Math.max(0, Math.min(100, Number(body.score) || 0)),
      status,
    };
    if (status !== "IN_PROGRESS") patch.finished_at = new Date().toISOString();

    const admin = getAdminClient();
    const { data, error } = await admin
      .from("practice_session")
      .update(patch)
      .eq("id", id)
      .eq("user_id", user.id)
      .select("id")
      .single();
    if (error) throw new ApiError(500, "保存练习进度失败：" + error.message);
    return ok({ id: data.id });
  } catch (e) {
    return handleError(e);
  }
}