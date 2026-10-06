"use client";

import { useEffect, useState } from "react";
import { api, verdictLabel } from "@/lib/client-api";
import Markdown from "@/components/markdown";

interface Appeal { id: number; reason: string; status: string; admin_note: string | null; created_at: string; user_email: string; user_answer: string; verdict: string; score: number | null; ai_feedback: any; question_content: string; question_type: string; question_answer: string; }

export default function AppealsTab() {
  const [status, setStatus] = useState("PENDING");
  const [items, setItems] = useState<Appeal[]>([]);
  const [note, setNote] = useState<Record<number, string>>({});
  const [error, setError] = useState("");

  function load() { api<Appeal[]>(`/api/admin/appeals?status=${status}`).then(setItems).catch((e) => setError(e.message)); }
  useEffect(load, [status]);

  async function resolve(id: number, s: "RESOLVED" | "REJECTED") {
    try { await api(`/api/admin/appeals/${id}/resolve`, { method: "POST", body: JSON.stringify({ status: s, adminNote: note[id] ?? "" }) }); load(); }
    catch (e) { setError((e as Error).message); }
  }

  return (
    <div>
      <div className="mb-4 flex gap-2">
        {["PENDING", "RESOLVED", "REJECTED", "ALL"].map((s) => (
          <button key={s} onClick={() => setStatus(s)} className={`rounded-xl px-3 py-1.5 text-sm ${status === s ? "bg-ink text-white" : "border border-mist bg-white/70 text-ink/70"}`}>
            {s === "PENDING" ? "待处理" : s === "RESOLVED" ? "已处理" : s === "REJECTED" ? "已驳回" : "全部"}
          </button>
        ))}
      </div>
      {error && <p className="mb-3 rounded-xl bg-rose/10 px-4 py-2.5 text-sm text-rose">{error}</p>}

      <div className="space-y-3">
        {items.map((a) => (
          <div key={a.id} className="rounded-2xl border border-mist bg-white/70 p-5">
            <div className="mb-2 flex flex-wrap items-center gap-2 text-xs text-oat">
              <span>{a.user_email}</span>
              <span className="rounded-full bg-mist px-2 py-0.5">{a.question_type}</span>
              <span className={`rounded-full px-2 py-0.5 ${a.status === "PENDING" ? "bg-sand/50 text-oat" : a.status === "RESOLVED" ? "bg-moss/15 text-moss" : "bg-rose/10 text-rose"}`}>{a.status}</span>
              <span>{new Date(a.created_at).toLocaleString("zh-CN")}</span>
            </div>
            <p className="text-sm text-ink">{a.question_content}</p>
            <div className="mt-2 space-y-1 text-sm">
              <p className="text-oat">用户答案：<span className="text-ink/80">{a.user_answer}</span></p>
              <p className="text-oat">AI 判定：{verdictLabel(a.verdict)} {a.score != null ? `(${a.score}/10)` : ""}</p>
              <p className="text-oat">参考答案：<span className="text-ink/80">{a.question_answer}</span></p>
              {a.ai_feedback?.feedback && (
                <div className="rounded-xl bg-paper px-3 py-2"><Markdown text={String(a.ai_feedback.feedback)} className="text-xs text-ink/70" /></div>
              )}
            </div>
            <p className="mt-2 rounded-xl bg-sand/20 px-3 py-2 text-sm text-oat">申诉原因：{a.reason || "-"}</p>

            {a.status === "PENDING" ? (
              <div className="mt-3 flex gap-2">
                <input value={note[a.id] ?? ""} onChange={(e) => setNote({ ...note, [a.id]: e.target.value })} placeholder="处理备注（可选）" className="min-w-40 flex-1 rounded-xl border border-mist bg-paper px-3 py-2 text-sm outline-none focus:border-moss" />
                <button onClick={() => resolve(a.id, "RESOLVED")} className="rounded-xl bg-moss px-3 py-2 text-xs text-white">采纳</button>
                <button onClick={() => resolve(a.id, "REJECTED")} className="rounded-xl border border-mist px-3 py-2 text-xs text-ink/70">驳回</button>
              </div>
            ) : (
              a.admin_note && <p className="mt-2 text-xs text-oat">处理备注：{a.admin_note}</p>
            )}
          </div>
        ))}
        {items.length === 0 && <p className="py-10 text-center text-sm text-oat">暂无申诉</p>}
      </div>
    </div>
  );
}