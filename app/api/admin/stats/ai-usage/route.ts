import { requireAdmin, ok, handleError } from "@/lib/server";
import { getAdminClient } from "@/lib/supabase/admin";

export async function GET() {
  try {
    await requireAdmin();
    const admin = getAdminClient();
    const { data } = await admin.from("ai_call_log").select("purpose,success,prompt_tokens,completion_tokens,total_tokens,duration_ms,created_at");
    const rows = data ?? [];
    const summary = {
      ai_calls: rows.length,
      failed_calls: rows.filter((r) => !r.success).length,
      prompt_tokens: rows.reduce((s, r) => s + (r.prompt_tokens ?? 0), 0),
      completion_tokens: rows.reduce((s, r) => s + (r.completion_tokens ?? 0), 0),
      total_tokens: rows.reduce((s, r) => s + (r.total_tokens ?? 0), 0),
      avg_duration_ms: rows.length ? Math.round(rows.reduce((s, r) => s + (r.duration_ms ?? 0), 0) / rows.length) : 0,
    };
    const byPurpose = new Map<string, { calls: number; total_tokens: number }>();
    for (const r of rows) {
      const g = byPurpose.get(r.purpose) ?? { calls: 0, total_tokens: 0 };
      g.calls++;
      g.total_tokens += r.total_tokens ?? 0;
      byPurpose.set(r.purpose, g);
    }
    return ok({ summary, by_purpose: [...byPurpose.entries()].map(([purpose, v]) => ({ purpose, ...v })) });
  } catch (e) {
    return handleError(e);
  }
}