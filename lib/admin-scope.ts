import { getAdminClient } from "@/lib/supabase/admin";
import type { Profile } from "@/lib/types";
import { ApiError } from "@/lib/server";

export async function visibleBankIds(profile: Profile): Promise<number[] | null> {
  const admin = getAdminClient();
  const { data } = await admin.from("bank").select("id,owner_id");
  if (profile.role === "ADMIN") return (data ?? []).map((b) => b.id);
  return (data ?? [])
    .filter((b) => b.owner_id === null || b.owner_id === profile.id)
    .map((b) => b.id);
}

export async function assertCanManageBank(profile: Profile, bankId: number) {
  const admin = getAdminClient();
  const { data: bank } = await admin.from("bank").select("owner_id,is_default").eq("id", bankId).single();
  if (!bank) throw new ApiError(404, "题库不存在");
  if (profile.role !== "ADMIN" && bank.owner_id !== profile.id) {
    throw new ApiError(403, "只能管理自己的题库");
  }
  return bank;
}

export async function assertCanManageUnit(profile: Profile, unitId: number) {
  const admin = getAdminClient();
  const { data: unit } = await admin
    .from("unit")
    .select("id,bank_id")
    .eq("id", unitId)
    .single();
  if (!unit) throw new ApiError(404, "单元不存在");
  await assertCanManageBank(profile, unit.bank_id);
  return unit;
}

export async function assertCanManageQuestion(profile: Profile, questionId: number) {
  const admin = getAdminClient();
  const { data: q } = await admin
    .from("question")
    .select("id,unit_id")
    .eq("id", questionId)
    .single();
  if (!q) throw new ApiError(404, "题目不存在");
  await assertCanManageUnit(profile, q.unit_id);
  return q;
}