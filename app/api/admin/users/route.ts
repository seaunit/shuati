import { requireAdmin, ok, handleError } from "@/lib/server";
import { getAdminClient } from "@/lib/supabase/admin";

export async function GET() {
  try {
    await requireAdmin();
    const admin = getAdminClient();
    const { data } = await admin
      .from("profiles")
      .select("id,email,nickname,role,status,created_at,last_login_at")
      .order("created_at", { ascending: true });
    return ok(data ?? []);
  } catch (e) {
    return handleError(e);
  }
}