import { requireAdmin, ok, handleError } from "@/lib/server";
import { getAdminClient } from "@/lib/supabase/admin";

export async function GET() {
  try {
    await requireAdmin();
    const admin = getAdminClient();
    const [banks, units, questions, records] = await Promise.all([
      admin.from("bank").select("id,name,is_default"),
      admin.from("unit").select("id,bank_id,name,sort"),
      admin.from("question").select("id,unit_id"),
      admin.from("practice_record").select("question_id,verdict"),
    ]);
    const qByUnit = new Map<number, number>();
    for (const q of questions.data ?? []) qByUnit.set(q.unit_id, (qByUnit.get(q.unit_id) ?? 0) + 1);
    const recByUnit = new Map<number, { total: number; correct: number }>();
    const unitQMap = new Map<number, number>();
    for (const q of questions.data ?? []) unitQMap.set(q.id, q.unit_id);
    for (const r of records.data ?? []) {
      const uid = unitQMap.get(r.question_id);
      if (!uid) continue;
      const g = recByUnit.get(uid) ?? { total: 0, correct: 0 };
      g.total++;
      if (r.verdict === "CORRECT") g.correct++;
      recByUnit.set(uid, g);
    }
    const bankMap = new Map((banks.data ?? []).map((b) => [b.id, b]));
    const result = (units.data ?? [])
      .map((u) => {
        const bank = bankMap.get(u.bank_id);
        const rec = recByUnit.get(u.id) ?? { total: 0, correct: 0 };
        return {
          bank_name: bank?.name ?? "",
          is_default: bank?.is_default ?? false,
          unit_name: u.name,
          question_count: qByUnit.get(u.id) ?? 0,
          answer_count: rec.total,
          correct_count: rec.correct,
        };
      })
      .sort((a, b) =>
        Number(b.is_default) - Number(a.is_default) || a.bank_name.localeCompare(b.bank_name) || a.unit_name.localeCompare(b.unit_name),
      );
    return ok(result);
  } catch (e) {
    return handleError(e);
  }
}