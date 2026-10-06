import { requireUser, ok, ApiError, handleError } from "@/lib/server";
import { getAdminClient } from "@/lib/supabase/admin";

export async function GET(_req: Request, { params }: { params: Promise<{ id: string }> }) {
  try {
    const { user, profile } = await requireUser();
    const admin = getAdminClient();
    const bankId = Number((await params).id);
    const url = new URL(_req.url);
    const mode = url.searchParams.get("mode") ?? "seq";

    const { data: bank } = await admin.from("bank").select("*").eq("id", bankId).single();
    if (!bank) throw new ApiError(404, "题库不存在");
    if (profile.role !== "ADMIN" && bank.owner_id !== null && bank.owner_id !== user.id) {
      throw new ApiError(403, "无权访问该题库");
    }

    const { data: units } = await admin.from("unit").select("id").eq("bank_id", bankId);
    const unitIds = (units ?? []).map((u) => u.id);
    if (unitIds.length === 0) return ok([]);

    let q = admin
      .from("question")
      .select("id,unit_id,type,content,options,difficulty,images,tags,status")
      .in("unit_id", unitIds)
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