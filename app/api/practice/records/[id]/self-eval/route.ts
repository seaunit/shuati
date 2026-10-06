import { requireUser, ok, ApiError, handleError } from "@/lib/server";
import { getAdminClient } from "@/lib/supabase/admin";

export async function POST(req: Request, { params }: { params: Promise<{ id: string }> }) {
  try {
    const { user } = await requireUser();
    const admin = getAdminClient();
    const recordId = Number((await params).id);
    const body = await req.json().catch(() => ({}));
    const v = String(body.verdict ?? "").toUpperCase();
    const verdict = v === "CORRECT" ? "CORRECT" : v === "PARTIAL" ? "PARTIAL" : "WRONG";
    const score = verdict === "CORRECT" ? 10 : verdict === "PARTIAL" ? 5 : 0;

    const { data, error } = await admin
      .from("practice_record")
      .update({ verdict, judge_source: "SELF", score })
      .eq("id", recordId)
      .eq("user_id", user.id)
      .eq("verdict", "PENDING")
      .select("id");
    if (error) throw new Error("更新失败：" + error.message);
    if (!data || data.length === 0) throw new ApiError(400, "记录不存在、不属于你或不处于待复核状态");
    return ok(null);
  } catch (e) {
    return handleError(e);
  }
}