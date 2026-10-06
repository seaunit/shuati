"use client";

import { useEffect, useState } from "react";
import { api } from "@/lib/client-api";

interface Overview {
  userCount: number; questionCount: number; answerCount: number; todayActiveUsers: number;
  verdicts: { CORRECT: number; PARTIAL: number; WRONG: number; PENDING: number };
  ai_calls: number; total_tokens: number;
}
interface ByUnit { bank_name: string; unit_name: string; question_count: number; answer_count: number; correct_count: number; }

export default function Dashboard({ isAdmin }: { isAdmin: boolean }) {
  const [overview, setOverview] = useState<Overview | null>(null);
  const [byUnit, setByUnit] = useState<ByUnit[]>([]);

  useEffect(() => {
    if (isAdmin) {
      api<Overview>("/api/admin/stats/overview").then(setOverview).catch(() => setOverview(null));
      api<ByUnit[]>("/api/admin/stats/by-unit").then(setByUnit).catch(() => setByUnit([]));
    } else {
      api<any>("/api/stats/me").then((s) => {
        setOverview({
          userCount: 0, questionCount: 0, todayActiveUsers: 0,
          answerCount: s.overall.total,
          verdicts: { CORRECT: s.overall.correct, PARTIAL: s.overall.partial, WRONG: s.overall.wrong, PENDING: s.overall.pending },
          ai_calls: 0, total_tokens: 0,
        });
        setByUnit(s.byUnit.map((u: any) => ({ bank_name: u.bank_name, unit_name: u.unit_name, question_count: 0, answer_count: u.total, correct_count: u.correct })));
      }).catch(() => setOverview(null));
    }
  }, [isAdmin]);

  if (!overview) return <div className="p-10 text-center text-sm text-oat">加载中…</div>;

  const rate = overview.answerCount ? Math.round((overview.verdicts.CORRECT / overview.answerCount) * 100) : 0;

  const cards = isAdmin
    ? [
        { label: "用户数", value: overview.userCount },
        { label: "题目数", value: overview.questionCount },
        { label: "作答数", value: overview.answerCount },
        { label: "今日活跃", value: overview.todayActiveUsers },
        { label: "AI Token", value: overview.total_tokens },
      ]
    : [
        { label: "总作答", value: overview.answerCount },
        { label: "答对", value: overview.verdicts.CORRECT },
        { label: "正确率", value: `${rate}%` },
        { label: "待复核", value: overview.verdicts.PENDING },
      ];

  return (
    <div className="space-y-6">
      <div className={`grid gap-3 ${isAdmin ? "grid-cols-2 sm:grid-cols-5" : "grid-cols-2 sm:grid-cols-4"}`}>
        {cards.map((c) => (
          <div key={c.label} className="rounded-2xl border border-mist bg-white/70 p-4 text-center shadow-sm">
            <p className="text-2xl font-semibold text-ink">{c.value}</p>
            <p className="mt-1 text-xs text-oat">{c.label}</p>
          </div>
        ))}
      </div>

      <div className="overflow-hidden rounded-2xl border border-mist bg-white/70 shadow-sm">
        <div className="border-b border-mist px-5 py-3 text-sm font-medium text-ink">题库 → 单元统计</div>
        {byUnit.length === 0 ? (
          <div className="p-10 text-center text-sm text-oat">暂无数据</div>
        ) : (
          <table className="w-full text-sm">
            <thead><tr className="text-left text-xs text-oat">
              <th className="px-5 py-2.5 font-normal">题库</th><th className="px-3 py-2.5 font-normal">单元</th>
              <th className="px-3 py-2.5 text-right font-normal">题目</th><th className="px-3 py-2.5 text-right font-normal">作答</th><th className="px-5 py-2.5 text-right font-normal">答对</th>
            </tr></thead>
            <tbody>
              {byUnit.map((u, i) => (
                <tr key={i} className="border-t border-mist/60 text-ink/80">
                  <td className="px-5 py-2.5">{u.bank_name}</td><td className="px-3 py-2.5">{u.unit_name}</td>
                  <td className="px-3 py-2.5 text-right">{u.question_count}</td><td className="px-3 py-2.5 text-right">{u.answer_count}</td><td className="px-5 py-2.5 text-right">{u.correct_count}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
    </div>
  );
}