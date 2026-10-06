import Link from "next/link";
import { redirect } from "next/navigation";
import { Leaf, Coins } from "lucide-react";
import { createClient } from "@/lib/supabase/server";
import { getProfile } from "@/lib/server";
import { getEntitlements } from "@/lib/points";
import SignOutButton from "@/components/sign-out-button";
import NavLinks from "@/components/nav-links";

export default async function AppLayout({ children }: { children: React.ReactNode }) {
  const supabase = await createClient();
  const {
    data: { user },
  } = await supabase.auth.getUser();
  if (!user) redirect("/login");

  const profile = await getProfile(user.id);
  const isAdmin = profile?.role === "ADMIN" && profile.status === "ENABLED";
  // 账务数据不可用时不阻塞页面渲染
  const entitlements = await getEntitlements(user.id).catch(() => null);
  const nav = [
    { href: "/app", label: "首页", icon: "home" },
    { href: "/app/practice", label: "刷题", icon: "practice" },
    { href: "/app/wrong-book", label: "错题本", icon: "wrong-book" },
    { href: "/app/stats", label: "统计", icon: "stats" },
    { href: "/app/pricing", label: "套餐与点数", icon: "pricing" },
    { href: "/app/admin", label: "后台管理", icon: "admin" },
  ];

  return (
    <div className="flex min-h-screen">
      <aside className="sticky top-0 flex h-screen w-60 shrink-0 flex-col border-r border-mist bg-white/55 px-4 py-6 backdrop-blur">
        <div className="flex items-center gap-2 px-2">
          <span className="flex h-9 w-9 items-center justify-center rounded-xl bg-moss/15 text-moss">
            <Leaf className="h-5 w-5" />
          </span>
          <div className="min-w-0">
            <p className="font-semibold text-ink">拾题</p>
            <p className="truncate text-xs text-oat">{user.email}</p>
          </div>
        </div>

        <div className="mt-3 px-2">
          <span className="inline-flex items-center rounded-full bg-sand/40 px-2.5 py-0.5 text-xs text-oat">
            {isAdmin ? "管理员" : "普通用户"}
          </span>
        </div>

        {entitlements && (
          <Link
            href="/app/pricing"
            className="mt-3 flex items-center justify-between rounded-xl border border-mist bg-white/70 px-3 py-2 transition hover:border-moss"
          >
            <span className="flex items-center gap-2 text-xs text-oat">
              <Coins className="h-3.5 w-3.5 text-moss" />
              {entitlements.planName}
            </span>
            <span className="text-sm font-medium text-ink">{entitlements.available} 点</span>
          </Link>
        )}

        <nav className="mt-6 flex-1 space-y-1">
          <NavLinks nav={nav} />
        </nav>

        <div className="px-2">
          <SignOutButton />
        </div>
      </aside>

      <main className="flex-1 overflow-x-hidden p-6 lg:p-8">{children}</main>
    </div>
  );
}
