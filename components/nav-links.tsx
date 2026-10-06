"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { Home, Dumbbell, BookX, BarChart3, Settings, type LucideIcon } from "lucide-react";

const ICONS: Record<string, LucideIcon> = {
  home: Home,
  practice: Dumbbell,
  "wrong-book": BookX,
  stats: BarChart3,
  admin: Settings,
};

export default function NavLinks({
  nav,
}: {
  nav: { href: string; label: string; icon: string }[];
}) {
  const pathname = usePathname();
  return (
    <>
      {nav.map((item) => {
        const Icon = ICONS[item.icon] ?? Home;
        const active =
          item.href === "/app"
            ? pathname === "/app"
            : pathname === item.href || pathname.startsWith(item.href + "/") || pathname.startsWith(item.href + "?");
        return (
          <Link
            key={item.href}
            href={item.href}
            className={`flex items-center gap-3 rounded-xl px-3 py-2 text-sm transition ${
              active ? "bg-moss/15 font-medium text-moss" : "text-ink/75 hover:bg-mist"
            }`}
          >
            <Icon className={`h-4 w-4 ${active ? "text-moss" : "text-oat"}`} />
            {item.label}
          </Link>
        );
      })}
    </>
  );
}
