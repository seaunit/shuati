"use client";

import { useEffect, useState } from "react";
import { Plus, Trash2, Pencil } from "lucide-react";
import { api } from "@/lib/client-api";

interface Bank { id: number; name: string; }
interface Unit { id: number; bank_id: number; name: string; sort: number; question_count: number; }

export default function UnitsTab({ isAdmin }: { isAdmin: boolean }) {
  const [banks, setBanks] = useState<Bank[]>([]);
  const [bankId, setBankId] = useState<number | null>(null);
  const [units, setUnits] = useState<Unit[]>([]);
  const [name, setName] = useState("");
  const [editing, setEditing] = useState<Unit | null>(null);
  const [error, setError] = useState("");

  useEffect(() => { api<Bank[]>("/api/admin/banks").then(setBanks).catch(() => setBanks([])); }, []);
  function load(bid: number) { api<Unit[]>(`/api/admin/units?bankId=${bid}`).then(setUnits).catch(() => setUnits([])); }
  useEffect(() => { if (bankId) load(bankId); else setUnits([]); }, [bankId]);

  async function create() {
    if (!bankId) { setError("请先选择题库"); return; }
    setError("");
    try { await api("/api/admin/units", { method: "POST", body: JSON.stringify({ bankId, name }) }); setName(""); load(bankId); }
    catch (e) { setError((e as Error).message); }
  }
  async function saveEdit() {
    if (!editing) return;
    setError("");
    try { await api(`/api/admin/units/${editing.id}`, { method: "PUT", body: JSON.stringify({ name: editing.name, sort: editing.sort }) }); setEditing(null); if (bankId) load(bankId); }
    catch (e) { setError((e as Error).message); }
  }
  async function remove(u: Unit) {
    if (!confirm(`确定删除单元「${u.name}」？`)) return;
    setError("");
    try { await api(`/api/admin/units/${u.id}`, { method: "DELETE" }); if (bankId) load(bankId); }
    catch (e) { setError((e as Error).message); }
  }

  return (
    <div>
      <div className="mb-4 flex items-center gap-3">
        <label className="text-sm text-ink">题库</label>
        <select value={bankId ?? ""} onChange={(e) => setBankId(e.target.value ? Number(e.target.value) : null)} className="rounded-xl border border-mist bg-paper px-3 py-2 text-sm outline-none focus:border-moss">
          <option value="">请选择题库</option>
          {banks.map((b) => <option key={b.id} value={b.id}>{b.name}</option>)}
        </select>
      </div>

      {bankId && (
        <div className="mb-4 flex flex-wrap gap-2 rounded-2xl border border-mist bg-white/70 p-4">
          <input value={name} onChange={(e) => setName(e.target.value)} placeholder="单元名称" className="min-w-40 flex-1 rounded-xl border border-mist bg-paper px-3 py-2 text-sm outline-none focus:border-moss" />
          <button onClick={create} className="inline-flex items-center gap-1.5 rounded-xl bg-ink px-4 py-2 text-sm text-white hover:bg-ink/90"><Plus className="h-4 w-4" /> 新增单元</button>
        </div>
      )}

      {error && <p className="mb-3 rounded-xl bg-rose/10 px-4 py-2.5 text-sm text-rose">{error}</p>}

      <div className="space-y-2.5">
        {units.map((u) => (
          <div key={u.id} className="flex items-center gap-3 rounded-2xl border border-mist bg-white/70 px-4 py-3">
            {editing?.id === u.id ? (
              <>
                <input value={editing.name} onChange={(e) => setEditing({ ...editing, name: e.target.value })} className="min-w-40 flex-1 rounded-lg border border-mist px-3 py-1.5 text-sm outline-none focus:border-moss" />
                <button onClick={saveEdit} className="rounded-lg bg-moss px-3 py-1.5 text-xs text-white">保存</button>
                <button onClick={() => setEditing(null)} className="rounded-lg border border-mist px-3 py-1.5 text-xs text-ink/70">取消</button>
              </>
            ) : (
              <>
                <span className="flex-1 font-medium text-ink">{u.name}</span>
                <span className="text-xs text-oat">{u.question_count} 题</span>
                <button onClick={() => setEditing(u)} className="rounded-lg border border-mist p-1.5 text-oat hover:text-moss"><Pencil className="h-4 w-4" /></button>
                <button onClick={() => remove(u)} className="rounded-lg border border-mist p-1.5 text-oat hover:text-rose"><Trash2 className="h-4 w-4" /></button>
              </>
            )}
          </div>
        ))}
        {bankId && units.length === 0 && <p className="py-10 text-center text-sm text-oat">该题库还没有单元</p>}
      </div>
    </div>
  );
}