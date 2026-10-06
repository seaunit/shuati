import { requireUser, ok, ApiError, handleError } from "@/lib/server";
import { getAdminClient } from "@/lib/supabase/admin";

export async function GET(_req: Request, { params }: { params: Promise<{ id: string }> }) {
  try {
    const { user, profile } = await requireUser();
    const admin = getAdminClient();
    const unitId = Number((await params).id);
    const url = new URL(_req.url);
    const mode = url.searchParams.get("mode") ?? "seq";

    const { data: unit } = await admin.from("unit").select("id,bank_id").eq("id", unitId).single();
    if (!unit) throw new ApiError(404, "单元不存在");
    const { data: bank } = await admin.from("bank").select("*").eq("id", unit.bank_id).single();
    if (!bank) throw new ApiError(404, "题库不存在");
    if (profile.role !== "ADMIN" && bank.owner_id !== null && bank.owner_id !== user.id) {
      throw new ApiError(403, "无权访问该单元");
    }

    let q = admin
      .from("question")
      .select("id,unit_id,type,content,options,difficulty,images,tags,status")
      .eq("unit_id", unitId)
      .eq("status", "ON");
    if (mode === "random") {
      const { data } = await q;
      const arr = data ?? [];
      for (let i = arr.length - 1; i > 0; i--) {
        const j = Math.floor(Math.random() * (i + 1));
        [arr[i], arr[j]] = [arr[j], arr[i]];
      }
      return ok(arr);
    }
    const { data } = await q.order("id", { ascending: true });
    return ok(data ?? []);
  } catch (e) {
    return handleError(e);
  }
}