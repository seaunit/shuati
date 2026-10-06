import { requireAdmin, ok, ApiError, handleError } from "@/lib/server";
import { getAdminClient } from "@/lib/supabase/admin";
import { encrypt } from "@/lib/crypto";

const DEEPSEEK_BASE_URL = "https://api.deepseek.com";

export async function PUT(req: Request, { params }: { params: Promise<{ id: string }> }) {
  try {
    await requireAdmin();
    const admin = getAdminClient();
    const id = Number((await params).id);
    const body = await req.json().catch(() => ({}));
    const baseUrl = String(body.baseUrl ?? "").trim().replace(/\/+$/, "");
    if (baseUrl !== DEEPSEEK_BASE_URL) {
      throw new ApiError(400, "当前仅支持 DeepSeek 模型，Base URL 固定为 " + DEEPSEEK_BASE_URL);
    }
    const patch: Record<string, unknown> = {
      name: String(body.name ?? "").trim(),
      base_url: baseUrl,
      model: String(body.model ?? "").trim(),
      purpose: body.purpose ?? "BOTH",
      remark: body.remark ?? null,
    };
    if (String(body.apiKey ?? "").trim()) patch.api_key_encrypted = encrypt(String(body.apiKey).trim());
    const { error } = await admin.from("ai_config").update(patch as any).eq("id", id);
    if (error) throw new Error("更新失败：" + error.message);
    return ok(null);
  } catch (e) {
    return handleError(e);
  }
}

export async function DELETE(_req: Request, { params }: { params: Promise<{ id: string }> }) {
  try {
    await requireAdmin();
    const admin = getAdminClient();
    const id = Number((await params).id);
    const { error } = await admin.from("ai_config").delete().eq("id", id);
    if (error) throw new Error("删除失败：" + error.message);
    return ok(null);
  } catch (e) {
    return handleError(e);
  }
}