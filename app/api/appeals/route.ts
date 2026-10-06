import { requireUser, ok, ApiError, handleError } from "@/lib/server";
import { getAdminClient } from "@/lib/supabase/admin";

export async function POST(req: Request) {
  try {
    const { user } = await requireUser();
    const admin = getAdminClient();
    const body = await req.json().catch(() => ({}));
    const recordId = Number(body.practiceRecordId);
    const reason = String(body.reason ?? "").trim();
    if (!recordId) throw new ApiError(400, "缺少作答记录 ID");
    if (!reason) throw new ApiError(400, "请填写申诉原因");

    const { data: rec } = await admin
      .from("practice_record")
      .select("id")
      .eq("id", recordId)
      .eq("user_id", user.id)
      .single();
    if (!rec) throw new ApiError(404, "作答记录不存在");

    const { error } = await admin.from("appeal").insert({
      practice_record_id: recordId,
      user_id: user.id,
      reason,
    });
    if (error) {
      if (error.message.toLowerCase().includes("duplicate")) throw new ApiError(400, "该记录已申诉");
      throw new Error("提交申诉失败：" + error.message);
    }
    return ok(null);
  } catch (e) {
    return handleError(e);
  }
}