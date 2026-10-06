import { getAdminClient } from "./supabase/admin";
import { decrypt } from "./crypto";

export interface AiEndpoint {
  baseUrl: string;
  apiKey: string;
  model: string;
}

export interface AiResult {
  content: string;
  model: string;
  promptTokens: number;
  completionTokens: number;
  totalTokens: number;
}

export type AiPurpose = "JUDGE" | "EXPLAIN" | "EXTRACT" | "TEST";

/** DeepSeek 当前主力模型；旧名 deepseek-chat 已停用 */
export const DEFAULT_MODEL = "deepseek-flash";

export async function resolveEndpoint(purpose: AiPurpose): Promise<AiEndpoint> {
  const admin = getAdminClient();
  const { data, error } = await admin
    .from("ai_config")
    .select("base_url, api_key_encrypted, model, purpose")
    .eq("status", "ENABLED")
    .in("purpose", [purpose, "BOTH"])
    .order("id", { ascending: true })
    .limit(20);

  if (!error && data && data.length > 0) {
    const exact = data.find((r) => r.purpose === purpose) ?? data[0];
    return {
      baseUrl: String(exact.base_url).replace(/\/+$/, ""),
      apiKey: decrypt(String(exact.api_key_encrypted)),
      model: String(exact.model),
    };
  }

  const key = process.env.DEEPSEEK_API_KEY;
  if (!key) throw new Error("未配置可用的 AI 接入（请在后台填写 DeepSeek API Key）");
  return {
    baseUrl: (process.env.DEEPSEEK_BASE_URL || "https://api.deepseek.com").replace(/\/+$/, ""),
    apiKey: key,
    model: process.env.DEEPSEEK_MODEL || DEFAULT_MODEL,
  };
}

export function stripCodeFence(text: string): string {
  let t = text.trim();
  if (t.startsWith("```")) {
    const firstNewline = t.indexOf("\n");
    if (firstNewline > 0) t = t.slice(firstNewline + 1);
    if (t.endsWith("```")) t = t.slice(0, -3);
    return t.trim();
  }
  return t;
}

async function chat(
  endpoint: AiEndpoint,
  system: string,
  user: string,
  jsonMode: boolean,
): Promise<AiResult> {
  const body: Record<string, unknown> = {
    model: endpoint.model,
    messages: [
      { role: "system", content: system },
      { role: "user", content: user },
    ],
    temperature: 0.3,
  };
  if (jsonMode) body.response_format = { type: "json_object" };

  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), 175_000);
  try {
    const resp = await fetch(endpoint.baseUrl + "/chat/completions", {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        Authorization: "Bearer " + endpoint.apiKey,
      },
      body: JSON.stringify(body),
      signal: controller.signal,
    });
    if (!resp.ok) {
      const text = await resp.text().catch(() => "");
      throw new Error("AI 请求失败（" + resp.status + "）：" + text.slice(0, 300));
    }
    const json = (await resp.json()) as {
      choices?: { message?: { content?: string } }[];
      usage?: { prompt_tokens?: number; completion_tokens?: number; total_tokens?: number };
    };
    const content = json.choices?.[0]?.message?.content?.trim() ?? "";
    if (!content) throw new Error("AI 返回内容为空");
    return {
      content: jsonMode ? stripCodeFence(content) : content,
      model: endpoint.model,
      promptTokens: json.usage?.prompt_tokens ?? 0,
      completionTokens: json.usage?.completion_tokens ?? 0,
      totalTokens: json.usage?.total_tokens ?? 0,
    };
  } finally {
    clearTimeout(timer);
  }
}

export async function chatJson(
  purpose: AiPurpose,
  system: string,
  user: string,
): Promise<AiResult> {
  return chat(await resolveEndpoint(purpose), system, user, true);
}

export async function chatText(
  purpose: AiPurpose,
  system: string,
  user: string,
): Promise<AiResult> {
  return chat(await resolveEndpoint(purpose), system, user, false);
}

export async function logUsage(
  userId: string | null,
  purpose: AiPurpose,
  model: string,
  questionId: number | null,
  result: AiResult | null,
  error: string | null,
  durationMs: number,
) {
  try {
    const admin = getAdminClient();
    await admin.from("ai_call_log").insert({
      user_id: userId,
      purpose,
      model,
      question_id: questionId,
      prompt_tokens: result?.promptTokens ?? 0,
      completion_tokens: result?.completionTokens ?? 0,
      total_tokens: result?.totalTokens ?? 0,
      success: !error,
      error,
      duration_ms: durationMs,
    });
  } catch {
    // 日志失败不影响主流程
  }
}
