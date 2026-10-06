import { requireUser, ok, handleError } from "@/lib/server";
import { getAdminClient } from "@/lib/supabase/admin";

// 跨题库"全部"入口：当前用户可见的所有上架题目
export async function GET(req: Request) {
  try {
    const { user, profile } = await requireUser();
    const admin = getAdminClient();
    const url = new URL(req.url);
    const mode = url.searchParams.get("mode") ?? "seq";

    const { data: banks } = await admin.from("bank").select("id,owner_id");
    const visibleBankIds = (banks ?? [])
      .filter((b) => profile.role === "ADMIN" || b.owner_id === null || b.owner_id === user.id)
      .map((b) => b.id);
    if (visibleBankIds.length === 0) return ok([]);

    const { data: units } = await admin.from("unit").select("id").in("bank_id", visibleBankIds);
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