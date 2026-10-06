"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { BookX, RefreshCw } from "lucide-react";
import { api, typeLabel, verdictLabel } from "@/lib/client-api";

interface WrongItem {
  record_id: number;
  question_id: number;
  verdict: string;
  score: number | null;
  last_at: string;
  type: string;
  content: string;
  difficulty: string;
  unit_id: number;
  unit_name: string;
  bank_name: string;
}

export default function WrongBookPage() {
  const [items, setItems] = useState<WrongItem[]>([]);
  const [loading, setLoading] = useState(true);

  function load() {
    setLoading(true);
    api<WrongItem[]>("/api/wrong-book")
      .then(setItems)
      .catch(() => setItems([]))
      .finally(() => setLoading(false));
  }
  useEffect(load, []);

  return (
    <div className="mx-auto max-w-4xl">
      <header className="mb-6 flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight text-ink">错题本</h1>
          <p className="mt-1 text-sm text-oat">最近一次作答未答对的题目会自动收集到这里</p>
        </div>
        <button onClick={load} className="inline-flex items-center gap-1.5 rounded-xl border border-mist bg-white/70 px-3 py-2 text-sm text-ink/70 transition hover:border-moss">
          <RefreshCw className="h-4 w-4" /> 刷新
        </button>
      </header>

      {loading ? (
        <div className="space-y-3">{[...Array(3)].map((_, i) => <div key={i} className="h-24 animate-pulse rounded-2xl bg-white/60" />)}</div>
      ) : items.length === 0 ? (
        <div className="rounded-2xl border border-dashed border-mist bg-white/40 p-12 text-center">
          <BookX className="mx-auto h-8 w-8 text-oat" />
          <p className="mt-3 text-sm text-oat">暂无错题，继续保持</p>
        </div>
      ) : (
        <div className="space-y-3">
          {items.map((it) => (
            <div key={it.record_id} className="rounded-2xl border border-mist bg-white/70 p-5 shadow-sm">
              <div className="mb-2 flex flex-wrap items-center gap-2 text-xs text-oat">
                <span className="rounded-full bg-sand/40 px-2 py-0.5">{it.bank_name} · {it.unit_name}</span>
                <span className="rounded-full bg-mist px-2 py-0.5">{typeLabel(it.type)}</span>
                <span className="rounded-full bg-mist px-2 py-0.5">{it.difficulty}</span>
                <span className={`rounded-full px-2 py-0.5 ${it.verdict === "PARTIAL" ? "bg-sand/50 text-oat" : "bg-rose/10 text-rose"}`}>{verdictLabel(it.verdict)}</span>
              </div>
              <p className="line-clamp-2 text-sm leading-relaxed text-ink">{it.content}</p>
              <div className="mt-3 flex items-center justify-between">
                <span className="text-xs text-oat">{new Date(it.last_at).toLocaleString("zh-CN")}</span>
                <Link href={`/app/practice?unit=${it.unit_id}`} className="text-sm text-moss hover:underline">重新练习</Link>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}