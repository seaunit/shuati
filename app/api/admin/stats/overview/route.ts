import { requireAdmin, ok, handleError } from "@/lib/server";
import { getAdminClient } from "@/lib/supabase/admin";

export async function GET() {
  try {
    await requireAdmin();
    const admin = getAdminClient();
    const [users, questions, answers, todayRows, ai] = await Promise.all([
      admin.from("profiles").select("id", { count: "exact", head: true }),
      admin.from("question").select("id", { count: "exact", head: true }),
      admin.from("practice_record").select("id", { count: "exact", head: true }),
      admin.from("practice_record").select("user_id,created_at").gte("created_at", new Date(new Date().setHours(0, 0, 0, 0)).toISOString()),
      admin.from("ai_call_log").select("success,total_tokens"),
    ]);
    const verdicts = await admin.from("practice_record").select("verdict");
    const vc: Record<string, number> = { CORRECT: 0, PARTIAL: 0, WRONG: 0, PENDING: 0 };
    for (const v of verdicts.data ?? []) {
      if (v.verdict in vc) vc[v.verdict]++;
    }
    const aiRows = ai.data ?? [];
    return ok({
      userCount: users.count ?? 0,
      questionCount: questions.count ?? 0,
      answerCount: answers.count ?? 0,
      todayActiveUsers: new Set((todayRows.data ?? []).map((r) => r.user_id)).size,
      verdicts: vc,
      ai_calls: aiRows.length,
      total_tokens: aiRows.reduce((s, r) => s + (r.total_tokens ?? 0), 0),
    });
  } catch (e) {
    return handleError(e);
  }
}