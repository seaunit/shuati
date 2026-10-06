import { requireUser, ok, ApiError, handleError } from "@/lib/server";
import { getAdminClient } from "@/lib/supabase/admin";
import { triggerWorker } from "@/lib/import";

export async function POST(req: Request) {
  try {
    const { user } = await requireUser();
    const admin = getAdminClient();
    const body = await req.json().catch(() => ({}));
    const kind = String(body.kind ?? "");
    const bankName = String(body.bankName ?? "").trim();
    const bankDescription = body.bankDescription ? String(body.bankDescription).trim() : null;

    if (!bankName) throw new ApiError(400, "题库名称必填");
    if (!["text", "url", "doc"].includes(kind)) throw new ApiError(400, "导入类型不正确");

    let sourceText: string | null = null;
    let sourceName: string | null = null;
    if (kind === "text") {
      sourceText = String(body.text ?? "");
      if (!sourceText.trim()) throw new ApiError(400, "请粘贴正文内容");
    } else if (kind === "url") {
      sourceText = String(body.url ?? "").trim();
      if (!sourceText) throw new ApiError(400, "请填写网页链接");
    } else {
      sourceText = String(body.base64 ?? "");
      sourceName = String(body.fileName ?? "doc");
      if (!sourceText) throw new ApiError(400, "请上传文档");
    }

    const { data: task, error } = await admin
      .from("import_task")
      .insert({
        user_id: user.id,
        kind,
        phase: "PARSE",
        status: "PENDING",
        bank_name: bankName,
        bank_description: bankDescription,
        source_text: sourceText,
        source_name: sourceName,
      })
      .select("*")
      .single();
    if (error) throw new Error("创建导入任务失败：" + error.message);

    await triggerWorker(task.id);
    return ok({ taskId: task.id });
  } catch (e) {
    return handleError(e);
  }
}