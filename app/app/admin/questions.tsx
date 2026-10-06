"use client";

import { useEffect, useState } from "react";
import { Plus, Trash2, Pencil, Power } from "lucide-react";
import { api, typeLabel } from "@/lib/client-api";

interface Bank { id: number; name: string; }
interface Unit { id: number; name: string; }
interface Q { id: number; unit_id: number; type: string; content: string; options: { key: string; text: string }[] | null; answer: string; key_points: string[] | null; difficulty: string; explanation: string | null; status: string; }

const emptyForm = { type: "SINGLE", content: "", options: [{ key: "A", text: "" }, { key: "B", text: "" }, { key: "C", text: "" }, { key: "D", text: "" }], answer: "", keyPoints: "", difficulty: "MEDIUM", explanation: "" };

export default function QuestionsTab({ isAdmin }: { isAdmin: boolean }) {
  const [banks, setBanks] = useState<Bank[]>([]);
  const [bankId, setBankId] = useState<number | null>(null);
  const [units, setUnits] = useState<Unit[]>([]);
  const [unitId, setUnitId] = useState<number | null>(null);
  const [questions, setQuestions] = useState<Q[]>([]);
  const [showForm, setShowForm] = useState(false);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [form, setForm] = useState(emptyForm);
  const [error, setError] = useState("");

  useEffect(() => { api<Bank[]>("/api/admin/banks").then(setBanks).catch(() => setBanks([])); }, []);
  useEffect(() => {
    if (!bankId) { setUnits([]); return; }
    api<Unit[]>(`/api/admin/units?bankId=${bankId}`).then(setUnits).catch(() => setUnits([]));
  }, [bankId]);

  function load() {
    if (!bankId && !unitId) { setQuestions([]); return; }
    const params = new URLSearchParams();
    if (bankId) params.set("bankId", String(bankId));
    if (unitId) params.set("unitId", String(unitId));
    api<Q[]>(`/api/admin/questions?${params}`).then(setQuestions).catch((e) => setError(e.message));
  }
  useEffect(load, [bankId, unitId]);

  function optionCount() {
    if (form.type === "SINGLE") return 4;
    if (form.type === "MULTI") return Math.max(4, Math.min(6, form.options.length));
    return 0;
  }
  function setOptions(count: number) {
    const keys = "ABCDEF".slice(0, count).split("");
    setForm((f) => ({ ...f, options: keys.map((k, i) => f.options[i] && f.options[i].key === k ? f.options[i] : { key: k, text: f.options[i]?.text ?? "" }) }));
  }

  function openNew() {
    setEditingId(null);
    setForm(emptyForm);
    setShowForm(true);
  }
  function openEdit(q: Q) {
    setEditingId(q.id);
    setForm({
      type: q.type,
      content: q.content,
      options: q.options ?? [{ key: "A", text: "" }, { key: "B", text: "" }, { key: "C", text: "" }, { key: "D", text: "" }],
      answer: q.answer,
      keyPoints: (q.key_points ?? []).join("\n"),
      difficulty: q.difficulty,
      explanation: q.explanation ?? "",
    });
    setShowForm(true);
  }

  async function submit() {
    setError("");
    const body: any = {
      unitId, type: form.type, content: form.content, answer: form.answer,
      difficulty: form.difficulty, explanation: form.explanation,
    };
    if (form.type === "SINGLE" || form.type === "MULTI") body.options = form.options;
    else body.keyPoints = form.keyPoints.split("\n").map((s) => s.trim()).filter(Boolean);
    try {
      if (editingId) await api(`/api/admin/questions/${editingId}`, { method: "PUT", body: JSON.stringify(body) });
      else await api("/api/admin/questions", { method: "POST", body: JSON.stringify(body) });
      setShowForm(false); load();
    } catch (e) { setError((e as Error).message); }
  }

  async function remove(q: Q) {
    if (!confirm("确定删除该题目？")) return;
    try { await api(`/api/admin/questions/${q.id}`, { method: "DELETE" }); load(); }
    catch (e) { setError((e as Error).message); }
  }
  async function toggle(q: Q) {
    try { await api(`/api/admin/questions/${q.id}/status`, { method: "POST", body: JSON.stringify({ status: q.status === "ON" ? "OFF" : "ON" }) }); load(); }
    catch (e) { setError((e as Error).message); }
  }

  return (
    <div>
      <div className="mb-4 flex flex-wrap items-center gap-3">
        <label className="text-sm text-ink">题库</label>
        <select value={bankId ?? ""} onChange={(e) => { setBankId(e.target.value ? Number(e.target.value) : null); setUnitId(null); }} className="rounded-xl border border-mist bg-paper px-3 py-2 text-sm outline-none focus:border-moss">
          <option value="">全部题库</option>
          {banks.map((b) => <option key={b.id} value={b.id}>{b.name}</option>)}
        </select>
        <label className="text-sm text-ink">单元</label>
        <select value={unitId ?? ""} onChange={(e) => setUnitId(e.target.value ? Number(e.target.value) : null)} className="rounded-xl border border-mist bg-paper px-3 py-2 text-sm outline-none focus:border-moss">
          <option value="">全部单元</option>
          {units.map((u) => <option key={u.id} value={u.id}>{u.name}</option>)}
        </select>
        <button onClick={openNew} disabled={!unitId} className="inline-flex items-center gap-1.5 rounded-xl bg-ink px-4 py-2 text-sm text-white hover:bg-ink/90 disabled:opacity-50"><Plus className="h-4 w-4" /> 新增题目</button>
      </div>

      {error && <p className="mb-3 rounded-xl bg-rose/10 px-4 py-2.5 text-sm text-rose">{error}</p>}

      {showForm && (
        <div className="mb-4 rounded-2xl border border-moss/30 bg-white/80 p-5">
          <h3 className="mb-4 font-medium text-ink">{editingId ? "编辑题目" : "新增题目"}</h3>
          <div className="space-y-3">
            <div className="flex flex-wrap gap-3">
              <label className="text-sm">题型
                <select value={form.type} onChange={(e) => { setForm({ ...form, type: e.target.value }); if (e.target.value === "SINGLE") setOptions(4); else if (e.target.value === "MULTI") setOptions(4); }} className="ml-2 rounded-xl border border-mist bg-paper px-3 py-2 text-sm outline-none focus:border-moss">
                  <option value="SINGLE">单选题</option><option value="MULTI">多选题</option><option value="SHORT">简答题</option>
                </select>
              </label>
              <label className="text-sm">难度
                <select value={form.difficulty} onChange={(e) => setForm({ ...form, difficulty: e.target.value })} className="ml-2 rounded-xl border border-mist bg-paper px-3 py-2 text-sm outline-none focus:border-moss">
                  <option value="EASY">简单</option><option value="MEDIUM">中等</option><option value="HARD">困难</option>
                </select>
              </label>
            </div>

            <textarea value={form.content} onChange={(e) => setForm({ ...form, content: e.target.value })} rows={3} placeholder="题干（支持 Markdown）" className="w-full rounded-xl border border-mist bg-paper px-4 py-2.5 text-sm outline-none focus:border-moss" />

            {(form.type === "SINGLE" || form.type === "MULTI") && (
              <div className="space-y-2">
                <div className="flex items-center justify-between">
                  <span className="text-xs text-oat">选项（单选恰好 4 个，多选 4~6 个）</span>
                  {form.type === "MULTI" && (
                    <div className="flex gap-1">
                      {[4, 5, 6].map((n) => <button key={n} onClick={() => setOptions(n)} className={`rounded-lg border px-2 py-0.5 text-xs ${form.options.length === n ? "border-moss text-moss" : "border-mist text-oat"}`}>{n} 项</button>)}
                    </div>
                  )}
                </div>
                {form.options.map((o, i) => (
                  <div key={o.key} className="flex items-center gap-2">
                    <span className="flex h-7 w-7 shrink-0 items-center justify-center rounded-md bg-mist text-xs font-medium text-ink">{o.key}</span>
                    <input value={o.text} onChange={(e) => { const next = [...form.options]; next[i] = { ...next[i], text: e.target.value }; setForm({ ...form, options: next }); }} placeholder="选项内容" className="flex-1 rounded-lg border border-mist bg-paper px-3 py-1.5 text-sm outline-none focus:border-moss" />
                  </div>
                ))}
                <input value={form.answer} onChange={(e) => setForm({ ...form, answer: e.target.value.toUpperCase() })} placeholder={form.type === "SINGLE" ? "答案（如 A）" : "答案（如 ABD）"} className="w-full rounded-xl border border-mist bg-paper px-3 py-2 text-sm outline-none focus:border-moss" />
              </div>
            )}

            {form.type === "SHORT" && (
              <>
                <textarea value={form.answer} onChange={(e) => setForm({ ...form, answer: e.target.value })} rows={3} placeholder="参考答案（完整答案 / 思路 / 伪代码）" className="w-full rounded-xl border border-mist bg-paper px-4 py-2.5 text-sm outline-none focus:border-moss" />
                <textarea value={form.keyPoints} onChange={(e) => setForm({ ...form, keyPoints: e.target.value })} rows={3} placeholder="评分要点（每行一条）" className="w-full rounded-xl border border-mist bg-paper px-4 py-2.5 text-sm outline-none focus:border-moss" />
              </>
            )}

            <textarea value={form.explanation} onChange={(e) => setForm({ ...form, explanation: e.target.value })} rows={2} placeholder="解析（可选，留空则由 AI 生成）" className="w-full rounded-xl border border-mist bg-paper px-4 py-2.5 text-sm outline-none focus:border-moss" />

            <div className="flex gap-2">
              <button onClick={submit} className="rounded-xl bg-moss px-4 py-2 text-sm text-white hover:bg-moss/90">保存</button>
              <button onClick={() => setShowForm(false)} className="rounded-xl border border-mist px-4 py-2 text-sm text-ink/70">取消</button>
            </div>
          </div>
        </div>
      )}

      <div className="space-y-2.5">
        {questions.map((q) => (
          <div key={q.id} className="rounded-2xl border border-mist bg-white/70 px-4 py-3">
            <div className="flex items-start gap-3">
              <div className="min-w-0 flex-1">
                <div className="mb-1 flex flex-wrap items-center gap-2 text-xs text-oat">
                  <span className="rounded-full bg-sand/40 px-2 py-0.5">{typeLabel(q.type)}</span>
                  <span className="rounded-full bg-mist px-2 py-0.5">{q.difficulty}</span>
                  <span className={q.status === "ON" ? "text-moss" : "text-rose"}>{q.status === "ON" ? "已上架" : "已下架"}</span>
                </div>
                <p className="line-clamp-2 text-sm text-ink">{q.content}</p>
                <p className="mt-1 text-xs text-oat">答案：{q.answer}</p>
              </div>
              <div className="flex shrink-0 items-center gap-1.5">
                <button onClick={() => toggle(q)} title="上下架" className="rounded-lg border border-mist p-1.5 text-oat hover:text-moss"><Power className="h-4 w-4" /></button>
                <button onClick={() => openEdit(q)} title="编辑" className="rounded-lg border border-mist p-1.5 text-oat hover:text-moss"><Pencil className="h-4 w-4" /></button>
                <button onClick={() => remove(q)} title="删除" className="rounded-lg border border-mist p-1.5 text-oat hover:text-rose"><Trash2 className="h-4 w-4" /></button>
              </div>
            </div>
          </div>
        ))}
        {questions.length === 0 && <p className="py-10 text-center text-sm text-oat">请选择题库 / 单元查看题目</p>}
      </div>
    </div>
  );
}