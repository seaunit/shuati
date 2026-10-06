import { requireUser, ok, handleError } from "@/lib/server";
import { getAdminClient } from "@/lib/supabase/admin";

export async function GET(_req: Request, { params }: { params: Promise<{ id: string }> }) {
  try {
    const { user } = await requireUser();
    const admin = getAdminClient();
    const bankId = Number((await params).id);
    const { data: units } = await admin.from("unit").select("id").eq("bank_id", bankId);
    const unitIds = (units ?? []).map((u) => u.id);
    let questions: { id: number }[] = [];
    if (unitIds.length) {
      const { data: q } = await admin
        .from("question")
        .select("id")
        .in("unit_id", unitIds)
        .eq("status", "ON");
      questions = q ?? [];
    }
    const total = questions.length;
    const qIds = new Set(questions.map((q) => q.id));
    const { data: records } = await admin
      .from("practice_record")
      .select("question_id,verdict,created_at")
      .eq("user_id", user.id)
      .order("created_at", { ascending: false });
    const latest = new Map<number, string>();
    for (const r of records ?? []) {
      if (qIds.has(r.question_id) && !latest.has(r.question_id)) latest.set(r.question_id, r.verdict);
    }
    const answeredIds = [...latest.keys()];
    const correct = answeredIds.filter((id) => latest.get(id) === "CORRECT").length;
    return ok({ total, answered: answeredIds.length, correct, answeredIds });
  } catch (e) {
    return handleError(e);
  }
}