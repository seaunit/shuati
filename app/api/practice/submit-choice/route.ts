import { requireUser, ok, ApiError, handleError } from "@/lib/server";
import { getAdminClient } from "@/lib/supabase/admin";
import { normalizeSelected } from "@/lib/rules";

export async function POST(req: Request) {
  try {
    const { user } = await requireUser();
    const body = await req.json().catch(() => ({}));
    const questionId = Number(body.questionId);
    const selected = normalizeSelected(body.selected);
    const durationMs = Number(body.durationMs) || null;
    if (!questionId) throw new ApiError(400, "缺少题目 ID");
    if (!selected) throw new ApiError(400, "请先选择选项");

    const admin = getAdminClient();
    const { data: q } = await admin
      .from("question")
      .select("type,answer,status")
      .eq("id", questionId)
      .single();
    if (!q || q.status !== "ON" || (q.type !== "SINGLE" && q.type !== "MULTI")) {
      throw new ApiError(404, "题目不存在或已下架");
    }

    const verdict = q.answer === selected ? "CORRECT" : "WRONG";
    const score = verdict === "CORRECT" ? 10 : 0;
    const { data: rec, error } = await admin
      .from("practice_record")
      .insert({
        user_id: user.id,
        question_id: questionId,
        user_answer: selected,
        judge_source: "LOCAL",
        verdict,
        score,
        duration_ms: durationMs,
      })
      .select("id")
      .single();
    if (error) throw new Error("保存作答记录失败：" + error.message);

    return ok({ recordId: rec.id, verdict, score, correctAnswer: q.answer });
  } catch (e) {
    return handleError(e);
  }
}