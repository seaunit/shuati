import { requireAdmin, ok, ApiError, handleError } from "@/lib/server";

export async function POST(req: Request) {
  try {
    await requireAdmin();
    const body = await req.json().catch(() => ({}));
    const baseUrl = String(body.baseUrl ?? "").trim().replace(/\/+$/, "");
    const apiKey = String(body.apiKey ?? "").trim();
    const model = String(body.model ?? "deepseek-chat").trim();
    if (!baseUrl || !apiKey) throw new ApiError(400, "Base URL 与 API Key 必填");
    const start = Date.now();
    try {
      const resp = await fetch(baseUrl + "/chat/completions", {
        method: "POST",
        headers: { "Content-Type": "application/json", Authorization: "Bearer " + apiKey },
        body: JSON.stringify({
          model,
          messages: [
            { role: "system", content: "你是连通性测试助手。" },
            { role: "user", content: "回复 ok 两个字即可。" },
          ],
          temperature: 0,
        }),
      });
      const text = await resp.text();
      if (!resp.ok) return ok({ success: false, latencyMs: Date.now() - start, error: text.slice(0, 200) });
      return ok({ success: true, latencyMs: Date.now() - start, reply: text.slice(0, 200) });
    } catch (e) {
      return ok({ success: false, latencyMs: Date.now() - start, error: (e as Error).message });
    }
  } catch (e) {
    return handleError(e);
  }
}