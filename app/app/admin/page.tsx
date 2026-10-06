"use client";

import { useEffect, useState } from "react";
import { LayoutDashboard, Library, Layers, FileQuestion, Upload, Users, KeyRound, MessageSquareText } from "lucide-react";
import { api } from "@/lib/client-api";
import Dashboard from "./dashboard";
import BanksTab from "./banks";
import UnitsTab from "./units";
import QuestionsTab from "./questions";
import ImportTab from "./import";
import UsersTab from "./users";
import AiConfigTab from "./ai-config";
import AppealsTab from "./appeals";

export default function AdminPage() {
  const [tab, setTab] = useState("dashboard");
  const [role, setRole] = useState<string | null>(null);

  useEffect(() => {
    api<{ role: string }>("/api/me").then((m) => setRole(m.role)).catch(() => setRole("USER"));
  }, []);

  const isAdmin = role === "ADMIN";

  const tabs = [
    { id: "dashboard", label: "数据统计", icon: LayoutDashboard },
    { id: "banks", label: "题库管理", icon: Library },
    { id: "units", label: "单元管理", icon: Layers },
    { id: "questions", label: "题目管理", icon: FileQuestion },
    { id: "import", label: "题库导入", icon: Upload },
    ...(isAdmin
      ? [
          { id: "users", label: "账号管理", icon: Users },
          { id: "ai", label: "模型与 API Key", icon: KeyRound },
          { id: "appeals", label: "申诉处理", icon: MessageSquareText },
        ]
      : []),
  ];

  return (
    <div className="mx-auto max-w-6xl">
      <header className="mb-6">
        <h1 className="text-2xl font-semibold tracking-tight text-ink">后台管理</h1>
        <p className="mt-1 text-sm text-oat">{isAdmin ? "管理员视图，可管理全部题库与账号" : "普通用户视图，可管理自己的题库与查看个人统计"}</p>
      </header>

      <div className="mb-6 flex flex-wrap gap-2">
        {tabs.map((t) => {
          const Icon = t.icon;
          return (
            <button key={t.id} onClick={() => setTab(t.id)}
              className={`inline-flex items-center gap-1.5 rounded-xl px-3 py-2 text-sm transition ${tab === t.id ? "bg-ink text-white" : "border border-mist bg-white/70 text-ink/70 hover:border-moss"}`}>
              <Icon className="h-4 w-4" /> {t.label}
            </button>
          );
        })}
      </div>

      {role === null ? (
        <div className="p-10 text-center text-sm text-oat">加载中…</div>
      ) : (
        <>
          {tab === "dashboard" && <Dashboard isAdmin={isAdmin} />}
          {tab === "banks" && <BanksTab isAdmin={isAdmin} />}
          {tab === "units" && <UnitsTab isAdmin={isAdmin} />}
          {tab === "questions" && <QuestionsTab isAdmin={isAdmin} />}
          {tab === "import" && <ImportTab />}
          {isAdmin && tab === "users" && <UsersTab />}
          {isAdmin && tab === "ai" && <AiConfigTab />}
          {isAdmin && tab === "appeals" && <AppealsTab />}
        </>
      )}
    </div>
  );
}