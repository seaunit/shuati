import { requireUser, ok, handleError } from "@/lib/server";
import { getAdminClient } from "@/lib/supabase/admin";

export async function GET() {
  try {
    const { user } = await requireUser();
    const admin = getAdminClient();
    const { data: records } = await admin
      .from("practice_record")
      .select("question_id,verdict")
      .eq("user_id", user.id);

    const rows = records ?? [];
    const overall = {
      total: rows.length,
      correct: rows.filter((r) => r.verdict === "CORRECT").length,
      partial: rows.filter((r) => r.verdict === "PARTIAL").length,
      wrong: rows.filter((r) => r.verdict === "WRONG").length,
      pending: rows.filter((r) => r.verdict === "PENDING").length,
    };

    const questionIds = [...new Set(rows.map((r) => r.question_id))];
    let byUnit: {
      bank_name: string;
      unit_name: string;
      total: number;
      correct: number;
    }[] = [];
    if (questionIds.length) {
      const { data: questions } = await admin
        .from("question")
        .select("id,unit_id")
        .in("id", questionIds);
      const unitIds = [...new Set((questions ?? []).map((q) => q.unit_id))];
      const { data: units } = await admin.from("unit").select("id,bank_id,name,sort").in("id", unitIds);
      const bankIds = [...new Set((units ?? []).map((u) => u.bank_id))];
      const { data: banks } = await admin.from("bank").select("id,name").in("id", bankIds);
      const unitMap = new Map((units ?? []).map((u) => [u.id, u]));
      const bankMap = new Map((banks ?? []).map((b) => [b.id, b]));

      const group = new Map<string, { bank_name: string; unit_name: string; total: number; correct: number }>();
      for (const r of rows) {
        const q = (questions ?? []).find((x) => x.id === r.question_id);
        const unit = q ? unitMap.get(q.unit_id) : undefined;
        if (!unit) continue;
        const bank = bankMap.get(unit.bank_id);
        const key = unit.id;
        const g = group.get(String(key)) ?? {
          bank_name: bank?.name ?? "",
          unit_name: unit.name,
          total: 0,
          correct: 0,
        };
        g.total++;
        if (r.verdict === "CORRECT") g.correct++;
        group.set(String(key), g);
      }
      byUnit = [...group.values()].sort((a, b) =>
        a.bank_name.localeCompare(b.bank_name) || a.unit_name.localeCompare(b.unit_name),
      );
    }

    return ok({ overall, byUnit });
  } catch (e) {
    return handleError(e);
  }
}