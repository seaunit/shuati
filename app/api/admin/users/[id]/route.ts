import { requireAdmin, ok, ApiError, handleError } from "@/lib/server";
import { getAdminClient } from "@/lib/supabase/admin";

export async function PATCH(req: Request, { params }: { params: Promise<{ id: string }> }) {
  try {
    await requireAdmin();
    const admin = getAdminClient();
    const userId = (await params).id;
    const body = await req.json().catch(() => ({}));
    const patch: Record<string, unknown> = {};
    if (body.role) patch.role = body.role;
    if (body.status) patch.status = body.status;
    if (body.nickname !== undefined) patch.nickname = body.nickname;
    if (Object.keys(patch).length) {
      const { error } = await admin.from("profiles").update(patch as any).eq("id", userId);
      if (error) throw new Error("更新失败：" + error.message);
    }
    if (body.password && String(body.password).length >= 6) {
      const { error } = await admin.auth.admin.updateUserById(userId, { password: String(body.password) });
      if (error) throw new Error("重置密码失败：" + error.message);
    } else if (body.password && String(body.password).length < 6) {
      throw new ApiError(400, "密码至少 6 位");
    }
    return ok(null);
  } catch (e) {
    return handleError(e);
  }
}