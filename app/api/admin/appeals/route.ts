import { requireAdmin, ok, handleError } from "@/lib/server";
import { getAdminClient } from "@/lib/supabase/admin";

export async function GET(req: Request) {
  try {
    await requireAdmin();
    const admin = getAdminClient();
    const url = new URL(req.url);
    const status = url.searchParams.get("status") ?? "PENDING";
    const { data: appeals } = await admin
      .from("appeal")
      .select("*")
      .order("created_at", { ascending: false });
    const list = status === "ALL" ? appeals ?? [] : (appeals ?? []).filter((a) => a.status === status);
    const userIds = [...new Set(list.map((a) => a.user_id))];
    const recordIds = [...new Set(list.map((a) => a.practice_record_id))];
    const [profiles, records, questions] = await Promise.all([
      userIds.length ? admin.from("profiles").select("id,email,nickname").in("id", userIds) : Promise.resolve({ data: [] }),
      recordIds.length ? admin.from("practice_record").select("*").in("id", recordIds) : Promise.resolve({ data: [] }),
      recordIds.length
        ? admin.from("question").select("id,content,type,answer").in("id", recordIds.map(() => 0))
        : Promise.resolve({ data: [] }),
    ]);
    // question 通过 practice_record.question_id 关联
    const recMap = new Map((records.data ?? []).map((r) => [r.id, r]));
    const profileMap = new Map((profiles.data ?? []).map((p) => [p.id, p]));
    const questionIds = [...new Set((records.data ?? []).map((r) => r.question_id))];
    const { data: qs } = questionIds.length
      ? await admin.from("question").select("id,content,type,answer").in("id", questionIds)
      : { data: [] };
    const qMap = new Map((qs ?? []).map((q) => [q.id, q]));

    const result = list.map((a) => {
      const rec = recMap.get(a.practice_record_id);
      const p = profileMap.get(a.user_id);
      const q = rec ? qMap.get(rec.question_id) : undefined;
      return {
        id: a.id,
        reason: a.reason,
        status: a.status,
        admin_note: a.admin_note,
        created_at: a.created_at,
        resolved_at: a.resolved_at,
        user_email: p?.email ?? "",
        user_nickname: p?.nickname ?? "",
        user_answer: rec?.user_answer ?? "",
        verdict: rec?.verdict ?? "",
        score: rec?.score ?? null,
        ai_feedback: rec?.ai_feedback ?? null,
        question_content: q?.content ?? "",
        question_type: q?.type ?? "",
        question_answer: q?.answer ?? "",
      };
    });
    return ok(result);
  } catch (e) {
    return handleError(e);
  }
}