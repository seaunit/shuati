import { requireUser, ok, handleError } from "@/lib/server";
import { getAdminClient } from "@/lib/supabase/admin";

export async function GET() {
  try {
    const { user } = await requireUser();
    const admin = getAdminClient();

    const { data: records } = await admin
      .from("practice_record")
      .select("id,question_id,verdict,score,created_at")
      .eq("user_id", user.id)
      .order("created_at", { ascending: false });

    const latest = new Map<number, NonNullable<typeof records>[number]>();
    for (const r of records ?? []) {
      if (!latest.has(r.question_id)) latest.set(r.question_id, r);
    }

    const wrong = [...latest.values()].filter((r) => r.verdict !== "CORRECT");
    const questionIds = wrong.map((r) => r.question_id);
    if (questionIds.length === 0) return ok([]);

    const { data: questions } = await admin
      .from("question")
      .select("id,unit_id,type,content,difficulty")
      .in("id", questionIds);
    const unitIds = [...new Set((questions ?? []).map((q) => q.unit_id))];
    const { data: units } = await admin.from("unit").select("id,bank_id,name").in("id", unitIds);
    const bankIds = [...new Set((units ?? []).map((u) => u.bank_id))];
    const { data: banks } = await admin.from("bank").select("id,name").in("id", bankIds);

    const unitMap = new Map((units ?? []).map((u) => [u.id, u]));
    const bankMap = new Map((banks ?? []).map((b) => [b.id, b]));
    const qMap = new Map((questions ?? []).map((q) => [q.id, q]));

    const result = wrong.map((r) => {
      const q = qMap.get(r.question_id);
      const unit = q ? unitMap.get(q.unit_id) : undefined;
      const bank = unit ? bankMap.get(unit.bank_id) : undefined;
      return {
        record_id: r.id,
        question_id: r.question_id,
        verdict: r.verdict,
        score: r.score,
        last_at: r.created_at,
        type: q?.type,
        content: q?.content,
        difficulty: q?.difficulty,
        unit_id: q?.unit_id,
        unit_name: unit?.name ?? "",
        bank_name: bank?.name ?? "",
      };
    });

    return ok(result);
  } catch (e) {
    return handleError(e);
  }
}