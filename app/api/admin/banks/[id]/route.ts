import { requireUser, ok, ApiError, handleError } from "@/lib/server";
import { getAdminClient } from "@/lib/supabase/admin";
import { assertCanManageBank } from "@/lib/admin-scope";

export async function PUT(req: Request, { params }: { params: Promise<{ id: string }> }) {
  try {
    const { profile } = await requireUser();
    const admin = getAdminClient();
    const bankId = Number((await params).id);
    await assertCanManageBank(profile, bankId);
    const body = await req.json().catch(() => ({}));
    const name = String(body.name ?? "").trim();
    if (!name) throw new ApiError(400, "题库名不能为空");
    const { error } = await admin
      .from("bank")
      .update({ name, description: body.description ?? null })
      .eq("id", bankId);
    if (error) {
      if (error.message.toLowerCase().includes("duplicate")) throw new ApiError(400, "题库名已存在");
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
    const bankId = Number((await params).id);
    const bank = await assertCanManageBank(profile, bankId);
    if (bank.is_default) throw new ApiError(400, "默认题库不能删除，请先把其他题库设为默认");
    const { count } = await admin
      .from("unit")
      .select("id", { count: "exact", head: true })
      .eq("bank_id", bankId);
    if ((count ?? 0) > 0) throw new ApiError(400, `该题库下还有 ${count} 个单元，请先删除或迁移`);
    const { error } = await admin.from("bank").delete().eq("id", bankId);
    if (error) throw new Error("删除失败：" + error.message);
    return ok(null);
  } catch (e) {
    return handleError(e);
  }
}