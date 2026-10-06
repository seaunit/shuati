import { requireUser, ok, handleError } from "@/lib/server";
import { getAdminClient } from "@/lib/supabase/admin";

export async function GET() {
  try {
    const { user } = await requireUser();
    const admin = getAdminClient();
    const { data } = await admin
      .from("import_task")
      .select("id,kind,phase,status,progress,total_chunks,done_chunks,bank_name,error,created_at,updated_at")
      .eq("user_id", user.id)
      .order("created_at", { ascending: false })
      .limit(50);
    return ok(data ?? []);
  } catch (e) {
    return handleError(e);
  }
}