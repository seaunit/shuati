import { requireUser, ok, ApiError, handleError } from "@/lib/server";
import { getAdminClient } from "@/lib/supabase/admin";

export async function GET() {
  try {
    const { user, profile } = await requireUser();
    const admin = getAdminClient();
    let q = admin
      .from("bank")
      .select("*")
      .order("is_default", { ascending: false })
      .order("id", { ascending: true });
    let data: any[] = [];
    if (profile.role === "ADMIN") {
      const { data: all } = await q;
      data = all ?? [];
    } else {
      const { data: all } = await q;
      data = (all ?? []).filter((b) => b.owner_id === user.id);
    }

    const bankIds = data.map((b) => b.id);
    let units: { id: number; bank_id: number }[] = [];
    if (bankIds.length) {
      const { data: u } = await admin.from("unit").select("id,bank_id").in("bank_id", bankIds);
      units = u ?? [];
    }
    const unitIds = units.map((u) => u.id);
    let questions: { id: number; unit_id: number }[] = [];
    if (unitIds.length) {
      const { data: qq } = await admin.from("question").select("id,unit_id").in("unit_id", unitIds);
      questions = qq ?? [];
    }
    const uCount = new Map<number, number>();
    const qCount = new Map<number, number>();
    for (const u of units) uCount.set(u.bank_id, (uCount.get(u.bank_id) ?? 0) + 1);
    for (const qq of questions) qCount.set(qq.unit_id, (qCount.get(qq.unit_id) ?? 0) + 1);
    const result = data.map((b) => {
      const us = units.filter((u) => u.bank_id === b.id);
      return {
        ...b,
        is_public: b.owner_id === null,
        unit_count: us.length,
        question_count: us.reduce((s, u) => s + (qCount.get(u.id) ?? 0), 0),
      };
    });
    return ok(result);
  } catch (e) {
    return handleError(e);
  }
}

export async function POST(req: Request) {
  try {
    const { user, profile } = await requireUser();
    const admin = getAdminClient();
    const body = await req.json().catch(() => ({}));
    const name = String(body.name ?? "").trim();
    if (!name) throw new ApiError(400, "题库名不能为空");
    const row: Record<string, unknown> = { name, description: body.description ?? null };
    if (profile.role !== "ADMIN") row.owner_id = user.id;
    const { error } = await admin.from("bank").insert(row as any);
    if (error) {
      if (error.message.toLowerCase().includes("duplicate")) throw new ApiError(400, "题库名已存在");
      throw new Error("创建题库失败：" + error.message);
    }
    return ok(null);
  } catch (e) {
    return handleError(e);
  }
}