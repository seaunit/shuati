import { requireUser, ok, ApiError, handleError } from "@/lib/server";
import { getAdminClient } from "@/lib/supabase/admin";
import { triggerWorker } from "@/lib/import";

export async function POST(req: Request, { params }: { params: Promise<{ id: string }> }) {
  try {
    const { user } = await requireUser();
    const admin = getAdminClient();
    const id = (await params).id;
    const body = await req.json().catch(() => ({}));
    const selectedIndexes = Array.isArray(body.selectedIndexes) ? body.selectedIndexes : null;

    const { data: task } = await admin.from("import_task").select("*").eq("id", id).single();
    if (!task) throw new ApiError(404, "导入任务不存在");
    if (task.user_id !== user.id) throw new ApiError(403, "无权操作该任务");
    if (task.phase !== "READY" && task.phase !== "DONE") throw new ApiError(400, "任务尚未解析完成");
    if (!task.bank_name || !String(task.bank_name).trim()) throw new ApiError(400, "题库名称必填");

    await admin
      .from("import_task")
      .update({
        phase: "IMPORT",
        status: "PENDING",
        selected_indexes: selectedIndexes,
        error: null,
      })
      .eq("id", id);

    await triggerWorker(id);
    return ok({ taskId: id });
  } catch (e) {
    return handleError(e);
  }
}