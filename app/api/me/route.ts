import { requireUser, ok, handleError } from "@/lib/server";

export async function GET() {
  try {
    const { user, profile } = await requireUser();
    return ok({ id: user.id, email: user.email, nickname: profile.nickname, role: profile.role, status: profile.status });
  } catch (e) {
    return handleError(e);
  }
}