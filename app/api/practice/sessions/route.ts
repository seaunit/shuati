import { requireUser, ok, ApiError, handleError } from "@/lib/server";
import { getAdminClient } from "@/lib/supabase/admin";

export async function POST(req: Request) {
  try {
    const { user } = await requireUser();
    const body = await req.json().catch(() => ({}));
    const scopeType = body.scopeType === "UNIT" || body.scopeType === "BANK" ? body.scopeType : "ALL";
    const scopeId = body.scopeId ? Number(body.scopeId) : null;
    const scopeName = String(body.scopeName ?? "");
    const totalQuestions = Math.max(0, Number(body.totalQuestions) || 0);

    const admin = getAdminClient();
    const { data, error } = await admin
      .from("practice_session")
      .insert({
        user_id: user.id,
        scope_type: scopeType,
        scope_id: scopeId,
        scope_name: scopeName,
        total_questions: totalQuestions,
      })
      .select("id")
      .single();
    if (error) throw new ApiError(500, "创建练习记录失败：" + error.message);
    return ok({ id: data.id });
  } catch (e) {
    return handleError(e);
  }
}

export async function GET() {
  try {
    const { user } = await requireUser();
    const admin = getAdminClient();
    const { data } = await admin
      .from("practice_session")
      .select("*")
      .eq("user_id", user.id)
      .order("started_at", { ascending: false })
      .limit(50);
    return ok(data ?? []);
  } catch (e) {
    return handleError(e);
  }
}