"use client";

import { useEffect, useState, useCallback } from "react";
import { ArrowRight, ArrowLeft, Check, Loader2, Lightbulb, BookOpen, RotateCcw } from "lucide-react";
import { api, typeLabel, verdictLabel } from "@/lib/client-api";
import Markdown from "@/components/markdown";

interface Option { key: string; text: string; }
interface Question {
  id: number;
  unit_id: number;
  type: "SINGLE" | "MULTI" | "SHORT";
  content: string;
  options: Option[] | null;
  difficulty: string;
}
interface ChoiceResult { recordId: number; verdict: string; score: number; correctAnswer: string; }
interface EssayResult {
  recordId: number; verdict: string; score: number | null;
  feedback: { hit_points?: string[]; missed_points?: string[]; feedback?: string } | null;
  referenceAnswer: string; degraded: boolean;
}

export default function PracticePage() {
  const [scope, setScope] = useState<{ bank?: number; unit?: number; all?: boolean } | null>(null);
  const [scopeName, setScopeName] = useState("刷题");
  const [questions, setQuestions] = useState<Question[]>([]);
  const [index, setIndex] = useState(0);
  const [loading, setLoading] = useState(true);
  const [selected, setSelected] = useState<Set<string>>(new Set());
  const [essay, setEssay] = useState("");
  const [choiceResult, setChoiceResult] = useState<ChoiceResult | null>(null);
  const [essayResult, setEssayResult] = useState<EssayResult | null>(null);
  const [explanation, setExplanation] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [explaining, setExplaining] = useState(false);
  const [error, setError] = useState("");
  const [doneIds, setDoneIds] = useState<Set<number>>(new Set());
  const [results, setResults] = useState<Map<number, { verdict: string; score: number | null }>>(new Map());
  const [finished, setFinished] = useState(false);
  const [sessionId, setSessionId] = useState<number | null>(null);

  const loadQuestions = useCallback(async (s: { bank?: number; unit?: number; all?: boolean }) => {
    setLoading(true);
    setError("");
    try {
      let path = "/api/questions?mode=random";
      if (s.unit) path = `/api/units/${s.unit}/questions?mode=random`;
      else if (s.bank) path = `/api/banks/${s.bank}/questions?mode=random`;
      else path = "/api/questions?mode=random";
      const qs = await api<Question[]>(path);
      const list = qs ?? [];
      setQuestions(list);
      setScopeName(s.all ? "全部题目" : s.unit ? "单元练习" : "题库练习");
      setIndex(0);
      setSelected(new Set());
      setEssay("");
      setChoiceResult(null);
      setEssayResult(null);
      setExplanation(null);
      setDoneIds(new Set());
      setResults(new Map());
      setFinished(false);
      setSessionId(null);
      if (list.length > 0) {
        const scopeType = s.all ? "ALL" : s.unit ? "UNIT" : "BANK";
        const scopeId = s.unit ?? s.bank ?? null;
        const scopeName = s.all ? "全部题目" : s.unit ? "单元练习" : "题库练习";
        api<{ id: number }>("/api/practice/sessions", {
          method: "POST",
          body: JSON.stringify({ scopeType, scopeId, scopeName, totalQuestions: list.length }),
        }).then((r) => setSessionId(r.id)).catch(() => setSessionId(null));
      }
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    const params = new URLSearchParams(window.location.search);
    const bank = params.get("bank");
    const unit = params.get("unit");
    const all = params.get("all") === "1";
    const s = all ? { all: true } : unit ? { unit: Number(unit) } : bank ? { bank: Number(bank) } : null;
    setScope(s);
    if (s) loadQuestions(s);
    else setLoading(false);
  }, [loadQuestions]);

  function restart() {
    setLoading(true);
    loadQuestions(scope ?? { all: true }).finally(() => setLoading(false));
  }

  const q = questions[index];
  const startTs = Date.now();

  function toggleOption(key: string) {
    if (choiceResult) return;
    setSelected((prev) => {
      const next = new Set(prev);
      if (q.type === "SINGLE") { next.clear(); next.add(key); }
      else { if (next.has(key)) next.delete(key); else next.add(key); }
      return next;
    });
  }

  async function submitChoice() {
    if (!q) return;
    if (selected.size === 0) { setError("请先选择选项"); return; }
    setSubmitting(true); setError("");
    try {
      const res = await api<ChoiceResult>("/api/practice/submit-choice", {
        method: "POST",
        body: JSON.stringify({ questionId: q.id, selected: [...selected].sort().join(""), durationMs: Date.now() - startTs }),
      });
      setChoiceResult(res);
      setDoneIds((prev) => new Set(prev).add(q.id));
      setResults((prev) => new Map(prev).set(q.id, { verdict: res.verdict, score: res.score ?? 0 }));
    } catch (e) { setError((e as Error).message); }
    finally { setSubmitting(false); }
  }

  async function submitEssay() {
    if (!q) return;
    if (!essay.trim()) { setError("请先作答"); return; }
    setSubmitting(true); setError("");
    try {
      const res = await api<EssayResult>("/api/practice/submit-essay", {
        method: "POST",
        body: JSON.stringify({ questionId: q.id, answer: essay, durationMs: Date.now() - startTs }),
      });
      setEssayResult(res);
      setDoneIds((prev) => new Set(prev).add(q.id));
      setResults((prev) => new Map(prev).set(q.id, { verdict: res.verdict, score: res.score ?? null }));
    } catch (e) { setError((e as Error).message); }
    finally { setSubmitting(false); }
  }

  async function loadExplanation() {
    if (!q) return;
    setExplaining(true); setError("");
    try {
      const correct = choiceResult?.verdict === "CORRECT";
      const res = await api<{ explanation: string }>(`/api/practice/questions/${q.id}/explanation?selected=${encodeURIComponent([...selected].sort().join(""))}&correct=${correct}`);
      setExplanation(res.explanation);
    } catch (e) { setError((e as Error).message); }
    finally { setExplaining(false); }
  }

  function goNext() {
    if (index < questions.length - 1) {
      setIndex(index + 1);
      setSelected(new Set()); setEssay(""); setChoiceResult(null); setEssayResult(null); setExplanation(null);
    }
  }
  function goPrev() {
    if (index > 0) {
      setIndex(index - 1);
      setSelected(new Set()); setEssay(""); setChoiceResult(null); setEssayResult(null); setExplanation(null);
    }
  }

  async function finish() {
    const correct = [...results.values()].filter((r) => r.verdict === "CORRECT").length;
    const partial = [...results.values()].filter((r) => r.verdict === "PARTIAL").length;
    const wrong = [...results.values()].filter((r) => r.verdict === "WRONG").length;
    const score = questions.length ? Math.round(((correct + partial * 0.5) / questions.length) * 100) : 0;
    if (sessionId) {
      try {
        await api(`/api/practice/sessions/${sessionId}`, {
          method: "PATCH",
          body: JSON.stringify({ answered: doneIds.size, correct, partial, wrong, score, status: "COMPLETED" }),
        });
      } catch { /* ignore */ }
    }
    setFinished(true);
  }

  const finishedCorrect = [...results.values()].filter((r) => r.verdict === "CORRECT").length;
  const finishedPartial = [...results.values()].filter((r) => r.verdict === "PARTIAL").length;
  const finishedWrong = [...results.values()].filter((r) => r.verdict === "WRONG").length;
  const scorePercent = questions.length ? Math.round(((finishedCorrect + finishedPartial * 0.5) / questions.length) * 100) : 0;
  function gradeOf(p: number) { if (p >= 90) return "优秀"; if (p >= 75) return "良好"; if (p >= 60) return "及格"; return "待提高"; }

  if (!scope) return <ScopePicker onPick={(s) => { setScope(s); loadQuestions(s); }} />;
  if (loading) return <div className="flex h-64 items-center justify-center text-sm text-oat"><Loader2 className="mr-2 h-4 w-4 animate-spin" /> 正在加载题目…</div>;
  if (questions.length === 0) {
    return (
      <div className="mx-auto max-w-2xl rounded-2xl border border-dashed border-mist bg-white/40 p-10 text-center">
        <p className="text-sm text-oat">该范围还没有可刷的题目</p>
        <button onClick={() => setScope(null)} className="mt-3 text-sm text-moss hover:underline">重新选择</button>
      </div>
    );
  }

  if (finished) {
    return (
      <div className="mx-auto max-w-2xl">
        <div className="rounded-2xl border border-moss/25 bg-white/70 p-8 text-center shadow-sm">
          <p className="text-xs uppercase tracking-widest text-oat">本次练习完成</p>
          <h2 className="mt-2 text-2xl font-semibold text-ink">{scopeName}</h2>
          <div className="mt-6 flex items-end justify-center gap-1">
            <span className="text-5xl font-semibold text-moss">{scorePercent}</span>
            <span className="pb-1.5 text-lg text-oat">分</span>
          </div>
          <p className="mt-2 text-sm text-oat">评级：{gradeOf(scorePercent)}</p>
          <div className="mt-6 grid grid-cols-3 gap-3 text-center">
            <div className="rounded-xl bg-moss/10 p-3"><p className="text-2xl font-semibold text-moss">{finishedCorrect}</p><p className="text-xs text-oat">正确</p></div>
            <div className="rounded-xl bg-sand/40 p-3"><p className="text-2xl font-semibold text-oat">{finishedPartial}</p><p className="text-xs text-oat">部分正确</p></div>
            <div className="rounded-xl bg-rose/10 p-3"><p className="text-2xl font-semibold text-rose">{finishedWrong}</p><p className="text-xs text-oat">错误</p></div>
          </div>
          <p className="mt-4 text-sm text-oat">共 {questions.length} 题 · 已答 {doneIds.size} 题</p>
          <div className="mt-6 flex justify-center gap-3">
            <button onClick={restart} className="inline-flex items-center gap-1.5 rounded-xl bg-moss px-4 py-2 text-sm text-white transition hover:bg-moss/90"><RotateCcw className="h-4 w-4" /> 再刷一遍</button>
            <button onClick={() => setScope(null)} className="rounded-xl border border-mist bg-white px-4 py-2 text-sm text-ink/70 transition hover:border-moss">返回选择</button>
          </div>
        </div>
      </div>
    );
  }

  const isAnswered = choiceResult !== null || essayResult !== null;

  return (
    <div className="mx-auto max-w-3xl">
      <div className="mb-5 flex items-center justify-between">
        <div>
          <button onClick={() => setScope(null)} className="text-sm text-oat hover:text-ink">← 返回选择</button>
          <p className="mt-1 text-xs text-oat">{scopeName}</p>
        </div>
        <button onClick={restart} className="inline-flex items-center gap-1.5 rounded-xl border border-mist bg-white/70 px-3 py-1.5 text-sm text-ink/70 transition hover:border-moss">
          <RotateCcw className="h-3.5 w-3.5" /> 从头开始
        </button>
      </div>

      <div className="mb-5">
        <div className="flex items-center justify-between text-xs text-oat">
          <span>第 {index + 1} / {questions.length} 题</span>
          <span>{doneIds.size} 题已作答</span>
        </div>
        <div className="mt-1.5 h-1.5 overflow-hidden rounded-full bg-mist">
          <div className="h-full rounded-full bg-moss transition-all" style={{ width: `${((index + 1) / questions.length) * 100}%` }} />
        </div>
      </div>

      <div className="rounded-2xl border border-mist bg-white/70 p-6 shadow-sm">
        <div className="mb-3 flex items-center gap-2">
          <span className="rounded-full bg-sand/40 px-2.5 py-0.5 text-xs text-oat">{typeLabel(q.type)}</span>
          <span className="rounded-full bg-mist px-2.5 py-0.5 text-xs text-oat">{q.difficulty}</span>
        </div>
        <Markdown text={q.content} className="text-[15px] leading-relaxed text-ink" />

        {q.type !== "SHORT" && q.options && (
          <div className="mt-5 space-y-2.5">
            {q.options.map((opt) => {
              const active = selected.has(opt.key);
              const isCorrectKey = choiceResult && choiceResult.correctAnswer.includes(opt.key);
              const isWrongPick = choiceResult && active && !choiceResult.correctAnswer.includes(opt.key);
              return (
                <button key={opt.key} onClick={() => toggleOption(opt.key)} disabled={!!choiceResult}
                  className={`flex w-full items-start gap-3 rounded-xl border px-4 py-3 text-left transition ${
                    isCorrectKey ? "border-moss bg-moss/10" : isWrongPick ? "border-rose bg-rose/10" : active ? "border-moss bg-moss/5" : "border-mist bg-paper hover:border-moss"}`}>
                  <span className={`mt-0.5 flex h-5 w-5 shrink-0 items-center justify-center rounded-md text-xs ${active || isCorrectKey ? "bg-moss text-white" : "bg-mist text-oat"}`}>{opt.key}</span>
                  <Markdown text={opt.text} className="flex-1 text-sm leading-relaxed text-ink" />
                </button>
              );
            })}
          </div>
        )}

        {q.type === "SHORT" && (
          <textarea value={essay} onChange={(e) => setEssay(e.target.value)} disabled={!!essayResult} rows={7}
            placeholder="在此输入你的答案（文字 / 思路 / 伪代码均可）"
            className="mt-5 w-full rounded-xl border border-mist bg-paper px-4 py-3 text-sm leading-relaxed outline-none transition focus:border-moss" />
        )}

        {error && <p className="mt-4 rounded-xl bg-rose/10 px-4 py-2.5 text-sm text-rose">{error}</p>}

        {choiceResult && (
          <div className={`mt-5 rounded-xl px-4 py-3 text-sm ${choiceResult.verdict === "CORRECT" ? "bg-moss/10 text-moss" : "bg-rose/10 text-rose"}`}>
            {choiceResult.verdict === "CORRECT" ? "回答正确" : "回答错误"}
            <span className="ml-2 text-ink/70">正确答案：{choiceResult.correctAnswer}</span>
            <button onClick={loadExplanation} disabled={explaining} className="ml-3 inline-flex items-center gap-1 text-xs text-ink/60 underline hover:text-moss">
              <Lightbulb className="h-3.5 w-3.5" /> {explaining ? "解析中…" : "查看答案解析"}
            </button>
          </div>
        )}

        {essayResult && (
          <div className="mt-5 space-y-3">
            <div className={`rounded-xl px-4 py-3 text-sm ${essayResult.verdict === "CORRECT" ? "bg-moss/10 text-moss" : essayResult.verdict === "PARTIAL" ? "bg-sand/50 text-oat" : "bg-rose/10 text-rose"}`}>
              判定：{verdictLabel(essayResult.verdict)}
              {essayResult.score != null && <span className="ml-2">得分 {essayResult.score}/10</span>}
            </div>
            {essayResult.feedback?.hit_points && essayResult.feedback.hit_points.length > 0 && (
              <div className="rounded-xl bg-moss/5 px-4 py-3 text-sm">
                <p className="font-medium text-moss">命中要点</p>
                <ul className="mt-1 list-disc pl-5 text-ink/80">{essayResult.feedback.hit_points.map((h, i) => <li key={i}>{h}</li>)}</ul>
              </div>
            )}
            {essayResult.feedback?.missed_points && essayResult.feedback.missed_points.length > 0 && (
              <div className="rounded-xl bg-rose/5 px-4 py-3 text-sm">
                <p className="font-medium text-rose">遗漏要点</p>
                <ul className="mt-1 list-disc pl-5 text-ink/80">{essayResult.feedback.missed_points.map((h, i) => <li key={i}>{h}</li>)}</ul>
              </div>
            )}
            {essayResult.feedback?.feedback && (
              <div className="rounded-xl border border-mist bg-paper px-4 py-3 text-sm">
                <p className="font-medium text-ink">AI 评语</p>
                <Markdown text={essayResult.feedback.feedback} className="mt-1 text-ink/80" />
              </div>
            )}
            <details className="rounded-xl border border-mist bg-paper px-4 py-3 text-sm">
              <summary className="cursor-pointer text-ink">参考答案</summary>
              <Markdown text={essayResult.referenceAnswer} className="mt-2 text-ink/80" />
            </details>
            {essayResult.degraded && (
              <div className="rounded-xl bg-sand/40 px-4 py-3 text-sm text-oat">
                AI 判分暂不可用，请对照参考答案自评：
                <div className="mt-2 flex gap-2">
                  {["CORRECT", "PARTIAL", "WRONG"].map((v) => (
                    <button key={v} onClick={async () => {
                      try {
                        await api(`/api/practice/records/${essayResult.recordId}/self-eval`, { method: "POST", body: JSON.stringify({ verdict: v }) });
                        setEssayResult({ ...essayResult, verdict: v, score: v === "CORRECT" ? 10 : v === "PARTIAL" ? 5 : 0, degraded: false });
                        setResults((prev) => new Map(prev).set(q.id, { verdict: v, score: v === "CORRECT" ? 10 : v === "PARTIAL" ? 5 : 0 }));
                      } catch (e) { setError((e as Error).message); }
                    }} className="rounded-lg border border-mist bg-white px-3 py-1 text-xs text-ink hover:border-moss">{verdictLabel(v)}</button>
                  ))}
                </div>
              </div>
            )}
          </div>
        )}

        {explanation && (
          <div className="mt-5 rounded-xl border border-moss/20 bg-moss/5 px-4 py-3">
            <p className="mb-1 flex items-center gap-1.5 text-sm font-medium text-ink"><BookOpen className="h-4 w-4 text-moss" /> 答案解析</p>
            <Markdown text={explanation} className="text-sm leading-relaxed text-ink/80" />
          </div>
        )}

        <div className="mt-6 flex items-center justify-between border-t border-mist pt-4">
          <button onClick={goPrev} disabled={index === 0} className="inline-flex items-center gap-1 rounded-xl border border-mist bg-white/70 px-3 py-2 text-sm text-ink/70 transition hover:border-moss disabled:opacity-40"><ArrowLeft className="h-4 w-4" /> 上一题</button>
          {!isAnswered ? (
            <button onClick={q.type === "SHORT" ? submitEssay : submitChoice} disabled={submitting} className="inline-flex items-center gap-1.5 rounded-xl bg-ink px-4 py-2 text-sm text-white transition hover:bg-ink/90 disabled:opacity-60">{submitting ? <Loader2 className="h-4 w-4 animate-spin" /> : <Check className="h-4 w-4" />} 提交答案</button>
          ) : index === questions.length - 1 ? (
            <button onClick={finish} className="inline-flex items-center gap-1.5 rounded-xl bg-moss px-4 py-2 text-sm text-white transition hover:bg-moss/90">查看成绩 <Check className="h-4 w-4" /></button>
          ) : (
            <button onClick={goNext} className="inline-flex items-center gap-1.5 rounded-xl bg-moss px-4 py-2 text-sm text-white transition hover:bg-moss/90">下一题 <ArrowRight className="h-4 w-4" /></button>
          )}
        </div>
      </div>
    </div>
  );
}

function ScopePicker({ onPick }: { onPick: (s: { bank?: number; unit?: number; all?: boolean }) => void }) {
  const [banks, setBanks] = useState<{ id: number; name: string }[]>([]);
  const [units, setUnits] = useState<{ id: number; name: string; question_count: number }[]>([]);
  const [bankId, setBankId] = useState<number | null>(null);

  useEffect(() => { api<any[]>("/api/banks").then((b) => setBanks(b)).catch(() => setBanks([])); }, []);
  useEffect(() => {
    if (!bankId) { setUnits([]); return; }
    api<any[]>(`/api/banks/${bankId}/units`).then((u) => setUnits(u)).catch(() => setUnits([]));
  }, [bankId]);

  return (
    <div className="mx-auto max-w-2xl">
      <header className="mb-6">
        <h1 className="text-2xl font-semibold tracking-tight text-ink">开始刷题</h1>
        <p className="mt-1 text-sm text-oat">选择练习范围</p>
      </header>
      <button onClick={() => onPick({ all: true })} className="mb-4 flex w-full items-center justify-between rounded-2xl border border-moss/25 bg-moss/10 px-5 py-4 text-left transition hover:border-moss">
        <span className="font-medium text-ink">全部题目</span><ArrowRight className="h-4 w-4 text-moss" />
      </button>
      <div className="rounded-2xl border border-mist bg-white/70 p-5">
        <label className="mb-1.5 block text-sm text-ink">选择题库</label>
        <select value={bankId ?? ""} onChange={(e) => setBankId(e.target.value ? Number(e.target.value) : null)} className="w-full rounded-xl border border-mist bg-paper px-3 py-2.5 text-sm outline-none focus:border-moss">
          <option value="">请选择题库</option>
          {banks.map((b) => <option key={b.id} value={b.id}>{b.name}</option>)}
        </select>
        {bankId && (
          <div className="mt-4">
            <label className="mb-1.5 block text-sm text-ink">选择单元（或整个题库）</label>
            <div className="space-y-2">
              <button onClick={() => onPick({ bank: bankId })} className="flex w-full items-center justify-between rounded-xl border border-mist bg-paper px-4 py-2.5 text-sm text-ink transition hover:border-moss">整个题库 <ArrowRight className="h-4 w-4 text-oat" /></button>
              {units.map((u) => (
                <button key={u.id} onClick={() => onPick({ unit: u.id })} className="flex w-full items-center justify-between rounded-xl border border-mist bg-paper px-4 py-2.5 text-sm text-ink transition hover:border-moss"><span>{u.name}</span><span className="text-xs text-oat">{u.question_count} 题</span></button>
              ))}
            </div>
          </div>
        )}
      </div>
    </div>
  );
}