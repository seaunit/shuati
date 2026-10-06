"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { BookOpen, CheckCircle2 } from "lucide-react";
import { api } from "@/lib/client-api";

interface Bank {
  id: number;
  name: string;
  description: string | null;
  is_default: boolean;
  is_public: boolean;
  unit_count: number;
  question_count: number;
  answered_count: number;
}

interface Unit {
  id: number;
  bank_id: number;
  name: string;
  sort: number;
  question_count: number;
  answered_count: number;
}

export default function HomePage() {
  const [banks, setBanks] = useState<Bank[]>([]);
  const [units, setUnits] = useState<Unit[]>([]);
  const [bankId, setBankId] = useState<number | null>(null);
  const [loading, setLoading] = useState(true);
  const [unitsLoading, setUnitsLoading] = useState(false);

  useEffect(() => {
    api<Bank[]>("/api/banks")
      .then((b) => {
        setBanks(b ?? []);
        const first = (b ?? []).find((x) => x.is_default) ?? (b ?? [])[0];
        if (first) setBankId(first.id);
      })
      .catch(() => setBanks([]))
      .finally(() => setLoading(false));
  }, []);

  useEffect(() => {
    if (!bankId) { setUnits([]); return; }
    setUnitsLoading(true);
    api<Unit[]>(`/api/banks/${bankId}/units`)
      .then((u) => setUnits(u ?? []))
      .catch(() => setUnits([]))
      .finally(() => setUnitsLoading(false));
  }, [bankId]);

  const currentBank = banks.find((b) => b.id === bankId);
  const totalQuestions = units.reduce((s, u) => s + (u.question_count ?? 0), 0);
  const totalAnswered = units.reduce((s, u) => s + (u.answered_count ?? 0), 0);
  const bankPercent = totalQuestions ? Math.round((totalAnswered / totalQuestions) * 100) : 0;

  function percent(u: Unit) {
    return u.question_count ? Math.round((u.answered_count / u.question_count) * 100) : 0;
  }

  return (
    <div className="mx-auto max-w-6xl">
      <header className="mb-6">
        <h1 className="text-2xl font-semibold tracking-tight text-ink">题库</h1>
        <p className="mt-1 text-sm text-oat">选择题库后，按单元开始练习</p>
      </header>

      {loading ? (
        <div className="space-y-4">
          <div className="h-10 animate-pulse rounded-xl border border-mist bg-white/50" />
          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
            {[...Array(4)].map((_, i) => (
              <div key={i} className="h-36 animate-pulse rounded-2xl border border-mist bg-white/60" />
            ))}
          </div>
        </div>
      ) : banks.length === 0 ? (
        <div className="mt-16 rounded-2xl border border-dashed border-mist bg-white/40 p-10 text-center">
          <p className="text-sm text-oat">还没有题库，请到「后台管理」导入或新建题库</p>
          <Link href="/app/admin" className="mt-3 inline-block text-sm text-moss hover:underline">
            前往后台管理
          </Link>
        </div>
      ) : (
        <>
          {/* 题库切换 */}
          <div className="mb-4 flex gap-2 overflow-x-auto pb-2">
            {banks.map((b) => {
              const active = b.id === bankId;
              return (
                <button
                  key={b.id}
                  onClick={() => setBankId(b.id)}
                  className={`inline-flex shrink-0 items-center gap-1.5 rounded-xl px-3.5 py-2 text-sm transition ${
                    active ? "bg-moss text-white shadow-sm" : "border border-mist bg-white/70 text-ink/75 hover:border-moss"
                  }`}
                >
                  {b.name}
                  <span className={`rounded-full px-1.5 text-[10px] ${active ? "bg-white/20" : "bg-sand/40 text-oat"}`}>
                    {b.is_default ? "默认" : b.is_public ? "公共" : "我的"}
                  </span>
                </button>
              );
            })}
          </div>

          {currentBank?.description && (
            <p className="mb-4 text-sm text-oat">{currentBank.description}</p>
          )}

          {/* 单元分类区 */}
          {unitsLoading ? (
            <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
              {[...Array(4)].map((_, i) => (
                <div key={i} className="h-32 animate-pulse rounded-2xl border border-mist bg-white/60" />
              ))}
            </div>
          ) : units.length === 0 ? (
            <div className="rounded-2xl border border-dashed border-mist bg-white/40 p-10 text-center">
              <p className="text-sm text-oat">该题库暂无单元</p>
            </div>
          ) : (
            <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
              <Link
                href={`/app/practice?bank=${bankId}`}
                className="group rounded-2xl border border-dashed border-moss/40 bg-moss/5 p-4 shadow-sm transition hover:-translate-y-0.5 hover:border-moss"
              >
                <h2 className="font-medium text-ink">全部题目</h2>
                <p className="mt-1 text-xs text-oat">{totalQuestions} 题 · 已刷 {totalAnswered}</p>
                <div className="mt-3 h-1.5 overflow-hidden rounded-full bg-mist">
                  <div className="h-full rounded-full bg-moss transition-all" style={{ width: `${bankPercent}%` }} />
                </div>
              </Link>

              {units.map((u) => (
                <Link
                  key={u.id}
                  href={`/app/practice?unit=${u.id}`}
                  className="group rounded-2xl border border-mist bg-white/70 p-4 shadow-sm transition hover:-translate-y-0.5 hover:border-moss"
                >
                  <h2 className="font-medium leading-snug text-ink">{u.name}</h2>
                  <div className="mt-3 flex items-center gap-3 text-xs text-oat">
                    <span className="inline-flex items-center gap-1">
                      <BookOpen className="h-3.5 w-3.5" /> {u.question_count} 题
                    </span>
                    <span className="inline-flex items-center gap-1">
                      <CheckCircle2 className="h-3.5 w-3.5" /> {u.answered_count} 已刷
                    </span>
                  </div>
                  <div className="mt-3 h-1.5 overflow-hidden rounded-full bg-mist">
                    <div className="h-full rounded-full bg-moss transition-all" style={{ width: `${percent(u)}%` }} />
                  </div>
                </Link>
              ))}
            </div>
          )}
        </>
      )}
    </div>
  );
}
