"use client";

import { useEffect, useState } from "react";
import { Plus, Trash2, Pencil, Star } from "lucide-react";
import { api } from "@/lib/client-api";

interface Bank { id: number; name: string; description: string | null; is_default: boolean; is_public: boolean; owner_id: string | null; unit_count: number; question_count: number; }

export default function BanksTab({ isAdmin }: { isAdmin: boolean }) {
  const [banks, setBanks] = useState<Bank[]>([]);
  const [name, setName] = useState("");
  const [desc, setDesc] = useState("");
  const [editing, setEditing] = useState<Bank | null>(null);
  const [error, setError] = useState("");

  function load() { api<Bank[]>("/api/admin/banks").then(setBanks).catch((e) => setError(e.message)); }
  useEffect(load, []);

  async function create() {
    setError("");
    if (!name.trim()) { setError("题库名不能为空"); return; }
    try {
      await api("/api/admin/banks", { method: "POST", body: JSON.stringify({ name, description: desc }) });
      setName(""); setDesc(""); load();
    } catch (e) { setError((e as Error).message); }
  }

  async function saveEdit() {
    if (!editing) return;
    setError("");
    try {
      await api(`/api/admin/banks/${editing.id}`, { method: "PUT", body: JSON.stringify({ name: editing.name, description: editing.description }) });
      setEditing(null); load();
    } catch (e) { setError((e as Error).message); }
  }

  async function remove(b: Bank) {
    if (!confirm(`确定删除题库「${b.name}」？`)) return;
    setError("");
    try { await api(`/api/admin/banks/${b.id}`, { method: "DELETE" }); load(); }
    catch (e) { setError((e as Error).message); }
  }

  async function setDefault(id: number) {
    setError("");
    try { await api(`/api/admin/banks/${id}/default`, { method: "POST" }); load(); }
    catch (e) { setError((e as Error).message); }
  }

  return (
    <div>
      <div className="mb-4 rounded-2xl border border-mist bg-white/70 p-4">
        <div className="flex flex-wrap gap-2">
          <input value={name} onChange={(e) => setName(e.target.value)} placeholder="题库名称" className="min-w-40 flex-1 rounded-xl border border-mist bg-paper px-3 py-2 text-sm outline-none focus:border-moss" />
          <input value={desc} onChange={(e) => setDesc(e.target.value)} placeholder="描述（可选）" className="min-w-40 flex-1 rounded-xl border border-mist bg-paper px-3 py-2 text-sm outline-none focus:border-moss" />
          <button onClick={create} className="inline-flex items-center gap-1.5 rounded-xl bg-ink px-4 py-2 text-sm text-white hover:bg-ink/90"><Plus className="h-4 w-4" /> 新建题库</button>
        </div>
      </div>

      {error && <p className="mb-3 rounded-xl bg-rose/10 px-4 py-2.5 text-sm text-rose">{error}</p>}

      <div className="space-y-2.5">
        {banks.map((b) => (
          <div key={b.id} className="flex flex-wrap items-center gap-3 rounded-2xl border border-mist bg-white/70 px-4 py-3">
            {editing?.id === b.id ? (
              <>
                <input value={editing.name} onChange={(e) => setEditing({ ...editing, name: e.target.value })} className="min-w-40 flex-1 rounded-lg border border-mist px-3 py-1.5 text-sm outline-none focus:border-moss" />
                <input value={editing.description ?? ""} onChange={(e) => setEditing({ ...editing, description: e.target.value })} placeholder="描述" className="min-w-40 flex-1 rounded-lg border border-mist px-3 py-1.5 text-sm outline-none focus:border-moss" />
                <button onClick={saveEdit} className="rounded-lg bg-moss px-3 py-1.5 text-xs text-white">保存</button>
                <button onClick={() => setEditing(null)} className="rounded-lg border border-mist px-3 py-1.5 text-xs text-ink/70">取消</button>
              </>
            ) : (
              <>
                <div className="min-w-0 flex-1">
                  <div className="flex items-center gap-2">
                    <span className="font-medium text-ink">{b.name}</span>
                    {b.is_default && <span className="rounded-full bg-moss/15 px-2 py-0.5 text-xs text-moss">默认</span>}
                    <span className={`rounded-full px-2 py-0.5 text-xs ${b.is_public ? "bg-sand/50 text-oat" : "bg-rose/10 text-rose"}`}>{b.is_public ? "公共题库" : "我的题库"}</span>
                  </div>
                  {b.description && <p className="mt-0.5 text-xs text-oat">{b.description}</p>}
                </div>
                <span className="text-xs text-oat">{b.unit_count} 单元 · {b.question_count} 题</span>
                {isAdmin && !b.is_default && (
                  <button onClick={() => setDefault(b.id)} title="设为默认" className="rounded-lg border border-mist p-1.5 text-oat hover:text-moss"><Star className="h-4 w-4" /></button>
                )}
                <button onClick={() => setEditing(b)} title="编辑" className="rounded-lg border border-mist p-1.5 text-oat hover:text-moss"><Pencil className="h-4 w-4" /></button>
                <button onClick={() => remove(b)} title="删除" className="rounded-lg border border-mist p-1.5 text-oat hover:text-rose"><Trash2 className="h-4 w-4" /></button>
              </>
            )}
          </div>
        ))}
        {banks.length === 0 && <p className="py-10 text-center text-sm text-oat">还没有题库</p>}
      </div>
    </div>
  );
}