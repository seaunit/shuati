import { requireAdmin, ok, ApiError, handleError } from "@/lib/server";
import { getAdminClient } from "@/lib/supabase/admin";
import { encrypt, decrypt, mask } from "@/lib/crypto";

const DEEPSEEK_BASE_URL = "https://api.deepseek.com";

function normalizeBaseUrl(u: string) {
  const normalized = (u ?? "").trim().replace(/\/+$/, "");
  if (normalized !== DEEPSEEK_BASE_URL) {
    throw new ApiError(400, "当前仅支持 DeepSeek 模型，Base URL 固定为 " + DEEPSEEK_BASE_URL);
  }
  return normalized;
}

export async function GET() {
  try {
    await requireAdmin();
    const admin = getAdminClient();
    const { data } = await admin
      .from("ai_config")
      .select("id,name,base_url,model,purpose,status,remark,api_key_encrypted,created_at,updated_at")
      .order("id", { ascending: true });
    const rows = (data ?? []).map((r) => {
      const { api_key_encrypted, ...rest } = r;
      let masked = "****";
      try {
        masked = mask(decrypt(String(api_key_encrypted)));
      } catch {
        masked = "****";
      }
      return { ...rest, api_key_masked: masked };
    });
    return ok(rows);
  } catch (e) {
    return handleError(e);
  }
}

export async function POST(req: Request) {
  try {
    await requireAdmin();
    const admin = getAdminClient();
    const body = await req.json().catch(() => ({}));
    const name = String(body.name ?? "").trim();
    const baseUrl = normalizeBaseUrl(String(body.baseUrl ?? ""));
    const apiKey = String(body.apiKey ?? "").trim();
    const model = String(body.model ?? "").trim();
    if (!name || !apiKey || !model) throw new ApiError(400, "名称、API Key、模型均必填");
    const { error } = await admin.from("ai_config").insert({
      name,
      base_url: baseUrl,
      api_key_encrypted: encrypt(apiKey),
      model,
      purpose: body.purpose ?? "BOTH",
      remark: body.remark ?? null,
    });
    if (error) {
      if (error.message.toLowerCase().includes("duplicate")) throw new ApiError(400, "配置名称已存在");
      throw new Error("创建失败：" + error.message);
    }
    return ok(null);
  } catch (e) {
    return handleError(e);
  }
}