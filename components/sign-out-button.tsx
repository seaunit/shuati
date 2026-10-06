"use client";

import { useRouter } from "next/navigation";
import { LogOut } from "lucide-react";
import { createClient } from "@/lib/supabase/client";

export default function SignOutButton() {
  const router = useRouter();
  const supabase = createClient();

  async function signOut() {
    await supabase.auth.signOut();
    router.push("/login");
    router.refresh();
  }

  return (
    <button
      onClick={signOut}
      className="flex w-full items-center gap-3 rounded-xl px-3 py-2 text-sm text-ink/70 transition hover:bg-mist"
    >
      <LogOut className="h-4 w-4 text-rose" />
      退出登录
    </button>
  );
}