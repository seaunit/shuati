import { requireAdmin, ok, handleError } from "@/lib/server";
import { getAdminClient } from "@/lib/supabase/admin";

export async function GET() {
  try {
    await requireAdmin();
    const admin = getAdminClient();
    const [questions, units, banks, records] = await Promise.all([
      admin.from("question").select("id,content,unit_id"),
      admin.from("unit").select("id,bank_id,name"),
      admin.from("bank").select("id,name"),
      admin.from("practice_record").select("question_id,verdict"),
    ]);
    const unitMap = new Map((units.data ?? []).map((u) => [u.id, u]));
    const bankMap = new Map((banks.data ?? []).map((b) => [b.id, b]));
    const qMap = new Map((questions.data ?? []).map((q) => [q.id, q]));
    const stat = new Map<number, { times: number; correct: number }>();
    for (const r of records.data ?? []) {
      const g = stat.get(r.question_id) ?? { times: 0, correct: 0 };
      g.times++;
      if (r.verdict === "CORRECT") g.correct++;
      stat.set(r.question_id, g);
    }
    const rows = [...stat.entries()]
      .filter(([, g]) => g.times >= 2)
      .map(([qid, g]) => {
        const q = qMap.get(qid);
        const unit = q ? unitMap.get(q.unit_id) : undefined;
        const bank = unit ? bankMap.get(unit.bank_id) : undefined;
        return {
          id: qid,
          content_preview: String(q?.content ?? "").slice(0, 80),
          bank_name: bank?.name ?? "",
          unit_name: unit?.name ?? "",
          times: g.times,
          correct_times: g.correct,
          correct_rate: Math.round((g.correct / g.times) * 1000) / 10,
        };
      })
      .sort((a, b) => a.correct_rate - b.correct_rate || b.times - a.times)
      .slice(0, 10);
    return ok(rows);
  } catch (e) {
    return handleError(e);
  }
}