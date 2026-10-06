import { requireUser, ok, ApiError, handleError } from "@/lib/server";
import { getAdminClient } from "@/lib/supabase/admin";

export async function GET(_req: Request, { params }: { params: Promise<{ id: string }> }) {
  try {
    const { user } = await requireUser();
    const admin = getAdminClient();
    const id = (await params).id;
    const { data: task } = await admin.from("import_task").select("*").eq("id", id).single();
    if (!task) throw new ApiError(404, "导入任务不存在");
    if (task.user_id !== user.id) throw new ApiError(403, "无权查看该任务");
    return ok(task);
  } catch (e) {
    return handleError(e);
  }
}