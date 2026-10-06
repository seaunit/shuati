import { requireUser, ok, ApiError, handleError } from "@/lib/server";
import { getAdminClient } from "@/lib/supabase/admin";
import { chatText, logUsage } from "@/lib/ai";
import { EXPLAIN_SYSTEM, explainUser } from "@/lib/prompts";

export async function GET(_req: Request, { params }: { params: Promise<{ id: string }> }) {
  try {
    const { user } = await requireUser();
    const admin = getAdminClient();
    const questionId = Number((await params).id);
    const url = new URL(_req.url);
    const selected = url.searchParams.get("selected") ?? "";
    const correct = url.searchParams.get("correct") === "true";

    const { data: q } = await admin
      .from("question")
      .select("content,options,answer,explanation")
      .eq("id", questionId)
      .single();
    if (!q) throw new ApiError(404, "题目不存在");

    if (q.explanation && String(q.explanation).trim()) {
      return ok({ explanation: q.explanation });
    }

    const optionsText = JSON.stringify(q.options ?? []);
    const start = Date.now();
    try {
      const res = await chatText(
        "EXPLAIN",
        EXPLAIN_SYSTEM,
        explainUser(String(q.content), optionsText, String(q.answer), selected, correct),
      );
      await logUsage(user.id, "EXPLAIN", "deepseek-chat", questionId, res, null, Date.now() - start);
      const generated = res.content;
      await admin
        .from("question")
        .update({ explanation: generated })
        .eq("id", questionId)
        .is("explanation", null);
      return ok({ explanation: generated });
    } catch (e) {
      await logUsage(user.id, "EXPLAIN", "deepseek-chat", questionId, null, String(e), Date.now() - start);
      throw new ApiError(502, "AI 解析失败，请稍后重试");
    }
  } catch (e) {
    return handleError(e);
  }
}