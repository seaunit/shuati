import { requireUser, ok, ApiError, handleError } from "@/lib/server";
import { getAdminClient } from "@/lib/supabase/admin";
import { chatJson, logUsage } from "@/lib/ai";
import { ESSAY_SYSTEM, essayUser } from "@/lib/prompts";
import {
  consumePoints,
  refundPoints,
  InsufficientPointsError,
} from "@/lib/points";

export async function POST(req: Request) {
  try {
    const { user } = await requireUser();
    const body = await req.json().catch(() => ({}));
    const questionId = Number(body.questionId);
    const answer = String(body.answer ?? "").trim();
    const durationMs = Number(body.durationMs) || null;
    if (!questionId) throw new ApiError(400, "缺少题目 ID");
    if (!answer) throw new ApiError(400, "请先作答");

    const admin = getAdminClient();
    const { data: q } = await admin
      .from("question")
      .select("type,content,answer,key_points,status")
      .eq("id", questionId)
      .single();
    if (!q || q.status !== "ON" || q.type !== "SHORT") {
      throw new ApiError(404, "题目不存在或不是简答题");
    }

    const referenceAnswer = String(q.answer ?? "");
    const keyPoints = JSON.stringify(q.key_points ?? []);

    let verdict = "PENDING";
    let score: number | null = null;
    let judgeSource = "SELF";
    let feedback: Record<string, unknown> | null = null;
    let degraded = false;

    const charged = await consumePoints(user.id, "JUDGE", {
      refType: "question",
      refId: String(questionId),
    });
    if (!charged.ok) throw new InsufficientPointsError(charged.cost, charged.available);

    try {
      const start = Date.now();
      const res = await chatJson(
        "JUDGE",
        ESSAY_SYSTEM,
        essayUser(String(q.content), keyPoints, referenceAnswer, answer),
      );
      await logUsage(user.id, "JUDGE", res.model, questionId, res, null, Date.now() - start);
      const parsed = JSON.parse(res.content) as {
        verdict?: string;
        score?: number;
        hit_points?: string[];
        missed_points?: string[];
        feedback?: string;
      };
      const v = String(parsed.verdict ?? "").toUpperCase();
      verdict = v === "CORRECT" ? "CORRECT" : v === "PARTIAL" ? "PARTIAL" : "WRONG";
      score = Math.max(0, Math.min(10, Number(parsed.score) || 0));
      judgeSource = "AI";
      feedback = {
        hit_points: parsed.hit_points ?? [],
        missed_points: parsed.missed_points ?? [],
        feedback: parsed.feedback ?? "",
      };
    } catch (e) {
      await refundPoints(
        user.id,
        charged.cost,
        "AI_JUDGE_FAILED",
        "question",
        String(questionId),
      );
      degraded = true;
      feedback = { feedback: "AI 判分暂不可用，请对照参考答案自行评估。" };
      console.error("[submit-essay] AI 判分失败:", e instanceof Error ? e.message : String(e));
    }

    const { data: rec, error } = await admin
      .from("practice_record")
      .insert({
        user_id: user.id,
        question_id: questionId,
        user_answer: answer,
        judge_source: judgeSource,
        verdict,
        score: judgeSource === "AI" ? score : null,
        ai_feedback: feedback,
        duration_ms: durationMs,
      })
      .select("id")
      .single();
    if (error) throw new Error("保存作答记录失败：" + error.message);

    return ok({
      recordId: rec.id,
      verdict,
      score: judgeSource === "AI" ? score : null,
      feedback,
      referenceAnswer,
      degraded,
    });
  } catch (e) {
    return handleError(e);
  }
}
