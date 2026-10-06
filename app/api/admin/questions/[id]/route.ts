import { requireUser, ok, ApiError, handleError } from "@/lib/server";
import { getAdminClient } from "@/lib/supabase/admin";
import { assertCanManageQuestion } from "@/lib/admin-scope";
import { validateQuestion, type QuestionType } from "@/lib/rules";

export async function PUT(req: Request, { params }: { params: Promise<{ id: string }> }) {
  try {
    const { profile } = await requireUser();
    const admin = getAdminClient();
    const questionId = Number((await params).id);
    await assertCanManageQuestion(profile, questionId);
    const body = await req.json().catch(() => ({}));
    const type = String(body.type ?? "").toUpperCase() as QuestionType;
    const content = String(body.content ?? "").trim();
    const answer = String(body.answer ?? "").trim();
    const options = body.options ?? null;
    const keyPoints = body.keyPoints ?? null;
    if (!content) throw new ApiError(400, "题干不能为空");
    try {
      validateQuestion(type, options, answer, keyPoints);
    } catch (e) {
      throw new ApiError(400, (e as Error).message);
    }
    const { error } = await admin
      .from("question")
      .update({
        type,
        content,
        options: type === "SINGLE" || type === "MULTI" ? options : null,
        answer,
        key_points: type === "SHORT" ? keyPoints : null,
        difficulty: body.difficulty ?? "MEDIUM",
        explanation: body.explanation ?? null,
        tags: body.tags ?? [],
      })
      .eq("id", questionId);
    if (error) throw new Error("更新失败：" + error.message);
    return ok(null);
  } catch (e) {
    return handleError(e);
  }
}

export async function DELETE(_req: Request, { params }: { params: Promise<{ id: string }> }) {
  try {
    const { profile } = await requireUser();
    const admin = getAdminClient();
    const questionId = Number((await params).id);
    await assertCanManageQuestion(profile, questionId);
    const { error } = await admin.from("question").delete().eq("id", questionId);
    if (error) throw new Error("删除失败：" + error.message);
    return ok(null);
  } catch (e) {
    return handleError(e);
  }
}