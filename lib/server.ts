import { NextResponse } from "next/server";
import { createClient } from "@/lib/supabase/server";
import { getAdminClient } from "@/lib/supabase/admin";
import type { Profile } from "@/lib/types";

export class ApiError extends Error {
  status: number;
  constructor(status: number, message: string) {
    super(message);
    this.status = status;
  }
}

export function ok<T>(data: T) {
  return NextResponse.json({ code: 200, message: "ok", data });
}

export function fail(status: number, message: string) {
  return NextResponse.json({ code: status, message, data: null }, { status });
}

export function handleError(e: unknown) {
  if (e instanceof ApiError) return fail(e.status, e.message);
  const msg = e instanceof Error ? e.message : "服务器内部错误";
  return fail(500, msg);
}

export function requireBody<T>(body: T): T {
  return body;
}

export const admin = getAdminClient();

export async function getSession() {
  const supabase = await createClient();
  const { data } = await supabase.auth.getSession();
  return { supabase, user: data.session?.user ?? null };
}

export async function getProfile(userId: string): Promise<Profile | null> {
  const { data } = await admin
    .from("profiles")
    .select("*")
    .eq("id", userId)
    .maybeSingle();
  return (data as Profile | null) ?? null;
}

export async function requireUser() {
  const { user } = await getSession();
  if (!user) throw new ApiError(401, "请先登录");
  const profile = await getProfile(user.id);
  if (!profile) throw new ApiError(403, "账号资料不存在");
  if (profile.status === "DISABLED") throw new ApiError(403, "账号已被禁用");
  return { user, profile };
}

export function isAdmin(profile: Profile | null) {
  return profile?.role === "ADMIN" && profile.status === "ENABLED";
}

export async function requireAdmin() {
  const ctx = await requireUser();
  if (!isAdmin(ctx.profile)) throw new ApiError(403, "仅管理员可执行此操作");
  return ctx;
}

export async function canManageBank(profile: Profile, bankOwnerId: string | null) {
  if (isAdmin(profile)) return true;
  return bankOwnerId === profile.id;
}