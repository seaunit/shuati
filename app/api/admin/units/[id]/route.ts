import { requireUser, ok, ApiError, handleError } from "@/lib/server";
import { getAdminClient } from "@/lib/supabase/admin";
import { assertCanManageUnit } from "@/lib/admin-scope";

export async function PUT(req: Request, { params }: { params: Promise<{ id: string }> }) {
  try {
    const { profile } = await requireUser();
    const admin = getAdminClient();
    const unitId = Number((await params).id);
    await assertCanManageUnit(profile, unitId);
    const body = await req.json().catch(() => ({}));
    const name = String(body.name ?? "").trim();
    if (!name) throw new ApiError(400, "单元名不能为空");
    const { error } = await admin
      .from("unit")
      .update({ name, sort: Number(body.sort) || 0 })
      .eq("id", unitId);
    if (error) {
      if (error.message.toLowerCase().includes("duplicate")) throw new ApiError(400, "该题库下已存在同名单元");
      throw new Error("更新失败：" + error.message);
    }
    return ok(null);
  } catch (e) {
    return handleError(e);
  }
}

export async function DELETE(_req: Request, { params }: { params: Promise<{ id: string }> }) {
  try {
    const { profile } = await requireUser();
    const admin = getAdminClient();
    const unitId = Number((await params).id);
    await assertCanManageUnit(profile, unitId);
    const { count } = await admin
      .from("question")
      .select("id", { count: "exact", head: true })
      .eq("unit_id", unitId);
    if ((count ?? 0) > 0) throw new ApiError(400, `该单元下还有 ${count} 道题，请先删除或迁移`);
    const { error } = await admin.from("unit").delete().eq("id", unitId);
    if (error) throw new Error("删除失败：" + error.message);
    return ok(null);
  } catch (e) {
    return handleError(e);
  }
}