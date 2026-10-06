import { requireUser, ok, handleError } from "@/lib/server";
import { getAdminClient } from "@/lib/supabase/admin";

export async function GET(_req: Request, { params }: { params: Promise<{ id: string }> }) {
  try {
    const { user } = await requireUser();
    const admin = getAdminClient();
    const unitId = Number((await params).id);
    const { data: questions } = await admin
      .from("question")
      .select("id")
      .eq("unit_id", unitId)
      .eq("status", "ON");
    const total = (questions ?? []).length;
    const { data: records } = await admin
      .from("practice_record")
      .select("question_id,verdict,created_at")
      .eq("user_id", user.id)
      .order("created_at", { ascending: false });
    const qIds = new Set((questions ?? []).map((q) => q.id));
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