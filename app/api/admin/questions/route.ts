import { requireUser, ok, ApiError, handleError } from "@/lib/server";
import { getAdminClient } from "@/lib/supabase/admin";
import { assertCanManageUnit } from "@/lib/admin-scope";
import { validateQuestion, type QuestionType } from "@/lib/rules";

export async function GET(req: Request) {
  try {
    const { profile } = await requireUser();
    const admin = getAdminClient();
    const url = new URL(req.url);
    const bankId = Number(url.searchParams.get("bankId") || 0);
    const unitId = Number(url.searchParams.get("unitId") || 0);

    let unitIds: number[] | null = null;
    if (unitId) {
      await assertCanManageUnit(profile, unitId);
      unitIds = [unitId];
    } else if (bankId) {
      const { data: units } = await admin.from("unit").select("id").eq("bank_id", bankId);
      unitIds = (units ?? []).map((u) => u.id);
    } else if (profile.role !== "ADMIN") {
      const { data: ownedBanks } = await admin.from("bank").select("id").eq("owner_id", profile.id);
      const ownedBankIds = (ownedBanks ?? []).map((b) => b.id);
      const { data: units } = await admin.from("unit").select("id").in("bank_id", ownedBankIds);
      unitIds = (units ?? []).map((u) => u.id);
    }

    let q = admin
      .from("question")
      .select("*")
      .order("id", { ascending: false });
    if (unitIds !== null) {
      if (unitIds.length === 0) return ok([]);
      q = admin.from("question").select("*").in("unit_id", unitIds).order("id", { ascending: false });
    }
    const { data } = await q.limit(2000);
    return ok(data ?? []);
  } catch (e) {
    return handleError(e);
  }
}

export async function POST(req: Request) {
  try {
    const { profile } = await requireUser();
    const admin = getAdminClient();
    const body = await req.json().catch(() => ({}));
    const unitId = Number(body.unitId);
    const type = String(body.type ?? "").toUpperCase() as QuestionType;
    const content = String(body.content ?? "").trim();
    const answer = String(body.answer ?? "").trim();
    const options = body.options ?? null;
    const keyPoints = body.keyPoints ?? null;
    if (!unitId) throw new ApiError(400, "请先选择单元");
    if (!content) throw new ApiError(400, "题干不能为空");
    await assertCanManageUnit(profile, unitId);
    try {
      validateQuestion(type, options, answer, keyPoints);
    } catch (e) {
      throw new ApiError(400, (e as Error).message);
    }

    const { error } = await admin.from("question").insert({
      unit_id: unitId,
      type,
      content,
      options: type === "SINGLE" || type === "MULTI" ? options : null,
      answer,
      key_points: type === "SHORT" ? keyPoints : null,
      difficulty: body.difficulty ?? "MEDIUM",
      explanation: body.explanation ?? null,
      tags: body.tags ?? [],
      status: "ON",
    });
    if (error) throw new Error("创建题目失败：" + error.message);
    return ok(null);
  } catch (e) {
    return handleError(e);
  }
}