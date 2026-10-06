"use client";

import { useEffect, useState } from "react";
import { BarChart3 } from "lucide-react";
import { api } from "@/lib/client-api";

interface PracticeSession {
  id: number;
  scope_name: string;
  total_questions: number;
  answered: number;
  correct: number;
  partial: number;
  wrong: number;
  score: number | null;
  status: string;
  started_at: string;
  finished_at: string | null;
}

interface Stats {
  overall: { total: number; correct: number; partial: number; wrong: number; pending: number };
  byUnit: { bank_name: string; unit_name: string; total: number; correct: number }[];
}

function formatTime(v: string) {
  const d = new Date(v);
  if (Number.isNaN(d.getTime())) return "";
  const p = (n: number) => String(n).padStart(2, "0");
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}`;
}

function statusLabel(s: string) {
  if (s === "COMPLETED") return "已完成";
  if (s === "ABANDONED") return "已放弃";
  return "进行中";
}

export default function StatsPage() {
  const [stats, setStats] = useState<Stats | null>(null);
  const [sessions, setSessions] = useState<PracticeSession[]>([]);
  useEffect(() => {
    api<Stats>("/api/stats/me").then(setStats).catch(() => setStats(null));
    api<PracticeSession[]>("/api/practice/sessions")
      .then((s) => setSessions(s ?? []))
      .catch(() => setSessions([]));
  }, []);

  if (!stats) return <div className="flex h-64 items-center justify-center text-sm text-oat">加载中…</div>;

  const { overall } = stats;
  const rate = overall.total ? Math.round((overall.correct / overall.total) * 100) : 0;

  const cards = [
    { label: "总刷题数", value: overall.total },
    { label: "答对", value: overall.correct },
    { label: "部分正确", value: overall.partial },
    { label: "答错", value: overall.wrong },
    { label: "正确率", value: `${rate}%` },
  ];

  return (
    <div className="mx-auto max-w-4xl">
      <header className="mb-6">
        <h1 className="text-2xl font-semibold tracking-tight text-ink">我的统计</h1>
        <p className="mt-1 text-sm text-oat">按题库 → 单元维度汇总</p>
      </header>

      <div className="grid grid-cols-2 gap-3 sm:grid-cols-5">
        {cards.map((c) => (
          <div key={c.label} className="rounded-2xl border border-mist bg-white/70 p-4 text-center shadow-sm">
            <p className="text-2xl font-semibold text-ink">{c.value}</p>
            <p className="mt-1 text-xs text-oat">{c.label}</p>
          </div>
        ))}
      </div>

      <div className="mt-6 overflow-hidden rounded-2xl border border-mist bg-white/70 shadow-sm">
        <div className="border-b border-mist px-5 py-3 text-sm font-medium text-ink">单元明细</div>
        {stats.byUnit.length === 0 ? (
          <div className="p-10 text-center text-sm text-oat">还没有作答记录</div>
        ) : (
          <table className="w-full text-sm">
            <thead>
              <tr className="text-left text-xs text-oat">
                <th className="px-5 py-2.5 font-normal">题库</th>
                <th className="px-3 py-2.5 font-normal">单元</th>
                <th className="px-3 py-2.5 text-right font-normal">作答</th>
                <th className="px-3 py-2.5 text-right font-normal">答对</th>
                <th className="px-5 py-2.5 text-right font-normal">正确率</th>
              </tr>
            </thead>
            <tbody>
              {stats.byUnit.map((u, i) => (
                <tr key={i} className="border-t border-mist/60 text-ink/80">
                  <td className="px-5 py-2.5">{u.bank_name}</td>
                  <td className="px-3 py-2.5">{u.unit_name}</td>
                  <td className="px-3 py-2.5 text-right">{u.total}</td>
                  <td className="px-3 py-2.5 text-right">{u.correct}</td>
                  <td className="px-5 py-2.5 text-right">{u.total ? Math.round((u.correct / u.total) * 100) : 0}%</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>

      <div className="mt-6 overflow-hidden rounded-2xl border border-mist bg-white/70 shadow-sm">
        <div className="border-b border-mist px-5 py-3 text-sm font-medium text-ink">我的练习记录</div>
        {sessions.length === 0 ? (
          <div className="p-10 text-center text-sm text-oat">还没有练习记录</div>
        ) : (
          <table className="w-full text-sm">
            <thead>
              <tr className="text-left text-xs text-oat">
                <th className="px-5 py-2.5 font-normal">范围</th>
                <th className="px-3 py-2.5 font-normal">时间</th>
                <th className="px-3 py-2.5 text-right font-normal">得分</th>
                <th className="px-3 py-2.5 text-right font-normal">答对/总题</th>
                <th className="px-5 py-2.5 text-right font-normal">状态</th>
              </tr>
            </thead>
            <tbody>
              {sessions.map((s) => (
                <tr key={s.id} className="border-t border-mist/60 text-ink/80">
                  <td className="px-5 py-2.5">{s.scope_name}</td>
                  <td className="px-3 py-2.5 text-ink/60">{formatTime(s.started_at)}</td>
                  <td className="px-3 py-2.5 text-right">{s.score == null ? "-" : s.score}</td>
                  <td className="px-3 py-2.5 text-right">{s.correct}/{s.total_questions}</td>
                  <td className="px-5 py-2.5 text-right">{statusLabel(s.status)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
    </div>
  );
}