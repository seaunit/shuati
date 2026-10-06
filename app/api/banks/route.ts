import { requireUser, ok, handleError } from "@/lib/server";
import { getAdminClient } from "@/lib/supabase/admin";

export async function GET() {
  try {
    const { user, profile } = await requireUser();
  const admin = getAdminClient();
  const isAdmin = profile.role === "ADMIN";

  const { data: all } = await admin
    .from("bank")
    .select("*")
    .order("is_default", { ascending: false })
    .order("id", { ascending: true });

  const banks = (all ?? []).filter(
    (b) => isAdmin || b.owner_id === null || b.owner_id === user.id,
  );
  const bankIds = banks.map((b) => b.id);

  let units: { id: number; bank_id: number; name: string; sort: number }[] = [];
  let questions: { id: number; unit_id: number; status: string }[] = [];
  if (bankIds.length) {
    const { data: u } = await admin
      .from("unit")
      .select("id,bank_id,name,sort")
      .in("bank_id", bankIds)
      .order("sort", { ascending: true })
      .order("id", { ascending: true });
    units = (u ?? []) as typeof units;
    const unitIds = units.map((x) => x.id);
    if (unitIds.length) {
      const { data: q } = await admin
        .from("question")
        .select("id,unit_id,status")
        .in("unit_id", unitIds);
      questions = (q ?? []) as typeof questions;
    }
  }

  let answeredSet = new Set<number>();
  if (bankIds.length) {
    const { data: recs } = await admin
      .from("practice_record")
      .select("question_id")
      .eq("user_id", user.id);
    answeredSet = new Set((recs ?? []).map((r) => r.question_id as number));
  }

  const onCount = new Map<number, number>();
  const answeredCount = new Map<number, number>();
  for (const q of questions) {
    if (q.status !== "ON") continue;
    onCount.set(q.unit_id, (onCount.get(q.unit_id) ?? 0) + 1);
    if (answeredSet.has(q.id)) answeredCount.set(q.unit_id, (answeredCount.get(q.unit_id) ?? 0) + 1);
  }

  const result = banks.map((b) => {
    const us = units.filter((u) => u.bank_id === b.id);
    return {
      ...b,
      is_public: b.owner_id === null,
      unit_count: us.length,
      question_count: us.reduce((s, u) => s + (onCount.get(u.id) ?? 0), 0),
      answered_count: us.reduce((s, u) => s + (answeredCount.get(u.id) ?? 0), 0),
    };
  });

    return ok(result);
  } catch (e) {
    return handleError(e);
  }
}