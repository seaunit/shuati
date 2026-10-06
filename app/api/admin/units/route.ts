import { requireUser, ok, ApiError, handleError } from "@/lib/server";
import { getAdminClient } from "@/lib/supabase/admin";
import { assertCanManageBank } from "@/lib/admin-scope";

export async function GET(req: Request) {
  try {
    const { profile } = await requireUser();
    const admin = getAdminClient();
    const url = new URL(req.url);
    const bankId = Number(url.searchParams.get("bankId") || 0);
    if (!bankId) return ok([]);
    await assertCanManageBank(profile, bankId);

    const { data: units } = await admin
      .from("unit")
      .select("id,bank_id,name,sort")
      .eq("bank_id", bankId)
      .order("sort", { ascending: true })
      .order("id", { ascending: true });
    const unitIds = (units ?? []).map((u) => u.id);
    let questions: { id: number; unit_id: number }[] = [];
    if (unitIds.length) {
      const { data: q } = await admin.from("question").select("id,unit_id").in("unit_id", unitIds);
      questions = q ?? [];
    }
    const qCount = new Map<number, number>();
    for (const q of questions) qCount.set(q.unit_id, (qCount.get(q.unit_id) ?? 0) + 1);
    const result = (units ?? []).map((u) => ({ ...u, question_count: qCount.get(u.id) ?? 0 }));
    return ok(result);
  } catch (e) {
    return handleError(e);
  }
}

export async function POST(req: Request) {
  try {
    const { profile } = await requireUser();
    const admin = getAdminClient();
    const body = await req.json().catch(() => ({}));
    const bankId = Number(body.bankId);
    const name = String(body.name ?? "").trim();
    if (!bankId) throw new ApiError(400, "请先选择题库");
    if (!name) throw new ApiError(400, "单元名不能为空");
    await assertCanManageBank(profile, bankId);
    const { error } = await admin
      .from("unit")
      .insert({ bank_id: bankId, name, sort: Number(body.sort) || 0 });
    if (error) {
      if (error.message.toLowerCase().includes("duplicate")) throw new ApiError(400, "该题库下已存在同名单元");
      throw new Error("创建单元失败：" + error.message);
    }
    return ok(null);
  } catch (e) {
    return handleError(e);
  }
}