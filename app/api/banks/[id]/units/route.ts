import { requireUser, ok, ApiError, handleError } from "@/lib/server";
import { getAdminClient } from "@/lib/supabase/admin";

export async function GET(_req: Request, { params }: { params: Promise<{ id: string }> }) {
  try {
    const { user, profile } = await requireUser();
    const admin = getAdminClient();
    const bankId = Number((await params).id);

    const { data: bank } = await admin.from("bank").select("*").eq("id", bankId).single();
    if (!bank) throw new ApiError(404, "题库不存在");
    if (profile.role !== "ADMIN" && bank.owner_id !== null && bank.owner_id !== user.id) {
      throw new ApiError(403, "无权访问该题库");
    }

    const { data: units } = await admin
      .from("unit")
      .select("id,bank_id,name,sort")
      .eq("bank_id", bankId)
      .order("sort", { ascending: true })
      .order("id", { ascending: true });

    const unitIds = (units ?? []).map((u) => u.id);
    let questions: { id: number; unit_id: number; status: string }[] = [];
    if (unitIds.length) {
      const { data: q } = await admin
        .from("question")
        .select("id,unit_id,status")
        .in("unit_id", unitIds);
      questions = (q ?? []) as typeof questions;
    }
    const { data: recs } = await admin
      .from("practice_record")
      .select("question_id")
      .eq("user_id", user.id);
    const answeredSet = new Set((recs ?? []).map((r) => r.question_id as number));

    const result = (units ?? []).map((u) => {
      const qs = questions.filter((q) => q.unit_id === u.id);
      return {
        ...u,
        question_count: qs.filter((q) => q.status === "ON").length,
        answered_count: qs.filter((q) => answeredSet.has(q.id)).length,
      };
    });
    return ok(result);
  } catch (e) {
    return handleError(e);
  }
}