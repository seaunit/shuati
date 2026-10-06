"use client";

import { useEffect, useState } from "react";
import { Plus, Trash2, Pencil, Zap } from "lucide-react";
import { api } from "@/lib/client-api";

interface Cfg { id: number; name: string; base_url: string; model: string; purpose: string; status: string; remark: string | null; api_key_masked: string; }

export default function AiConfigTab() {
  const [cfgs, setCfgs] = useState<Cfg[]>([]);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [form, setForm] = useState({ name: "", baseUrl: "https://api.deepseek.com", apiKey: "", model: "deepseek-flash", purpose: "BOTH", remark: "" });
  const [showForm, setShowForm] = useState(false);
  const [testing, setTesting] = useState(false);
  const [testResult, setTestResult] = useState("");
  const [error, setError] = useState("");

  function load() { api<Cfg[]>("/api/admin/ai-config").then(setCfgs).catch((e) => setError(e.message)); }
  useEffect(load, []);

  function openNew() { setEditingId(null); setForm({ name: "", baseUrl: "https://api.deepseek.com", apiKey: "", model: "deepseek-flash", purpose: "BOTH", remark: "" }); setShowForm(true); }
  function openEdit(c: Cfg) { setEditingId(c.id); setForm({ name: c.name, baseUrl: c.base_url, apiKey: "", model: c.model, purpose: c.purpose, remark: c.remark ?? "" }); setShowForm(true); }

  async function save() {
    setError("");
    try {
      if (editingId) await api(`/api/admin/ai-config/${editingId}`, { method: "PUT", body: JSON.stringify(form) });
      else await api("/api/admin/ai-config", { method: "POST", body: JSON.stringify(form) });
      setShowForm(false); load();
    } catch (e) { setError((e as Error).message); }
  }
  async function test() {
    setTesting(true); setTestResult("");
    try {
      const r = await api<any>("/api/admin/ai-config/test", { method: "POST", body: JSON.stringify({ baseUrl: form.baseUrl, apiKey: form.apiKey, model: form.model }) });
      setTestResult(r.success ? `连通成功（${r.latencyMs}ms）` : `失败：${r.error}`);
    } catch (e) { setTestResult((e as Error).message); }
    finally { setTesting(false); }
  }
  async function remove(c: Cfg) { if (!confirm(`删除配置「${c.name}」？`)) return; try { await api(`/api/admin/ai-config/${c.id}`, { method: "DELETE" }); load(); } catch (e) { setError((e as Error).message); } }
  async function toggle(c: Cfg) { try { await api(`/api/admin/ai-config/${c.id}/status`, { method: "POST", body: JSON.stringify({ status: c.status === "ENABLED" ? "DISABLED" : "ENABLED" }) }); load(); } catch (e) { setError((e as Error).message); } }

  return (
    <div>
      <div className="mb-4 rounded-2xl border border-sand bg-sand/20 px-4 py-3 text-sm text-oat">
        当前仅支持 DeepSeek 模型，开通地址：<a href="https://platform.deepseek.com" target="_blank" rel="noreferrer" className="text-moss underline">platform.deepseek.com</a>；Base URL 固定为 https://api.deepseek.com
      </div>

      {error && <p className="mb-3 rounded-xl bg-rose/10 px-4 py-2.5 text-sm text-rose">{error}</p>}

      <div className="mb-4 flex justify-end">
        <button onClick={openNew} className="inline-flex items-center gap-1.5 rounded-xl bg-ink px-4 py-2 text-sm text-white hover:bg-ink/90"><Plus className="h-4 w-4" /> 新增配置</button>
      </div>

      {showForm && (
        <div className="mb-4 rounded-2xl border border-moss/30 bg-white/80 p-5">
          <h3 className="mb-3 font-medium text-ink">{editingId ? "编辑配置" : "新增配置"}</h3>
          <div className="space-y-3">
            <div className="flex flex-wrap gap-3">
              <input value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} placeholder="配置名称" className="min-w-36 flex-1 rounded-xl border border-mist bg-paper px-3 py-2 text-sm outline-none focus:border-moss" />
              <input value={form.model} onChange={(e) => setForm({ ...form, model: e.target.value })} placeholder="模型" className="min-w-36 flex-1 rounded-xl border border-mist bg-paper px-3 py-2 text-sm outline-none focus:border-moss" />
              <select value={form.purpose} onChange={(e) => setForm({ ...form, purpose: e.target.value })} className="rounded-xl border border-mist bg-paper px-3 py-2 text-sm outline-none focus:border-moss">
                <option value="BOTH">判分 + 解析</option><option value="JUDGE">仅判分</option><option value="EXPLAIN">仅解析</option>
              </select>
            </div>
            <input value={form.baseUrl} onChange={(e) => setForm({ ...form, baseUrl: e.target.value })} placeholder="Base URL" className="w-full rounded-xl border border-mist bg-paper px-3 py-2 text-sm outline-none focus:border-moss" />
            <input type="password" value={form.apiKey} onChange={(e) => setForm({ ...form, apiKey: e.target.value })} placeholder={editingId ? "API Key（留空则不修改）" : "API Key"} className="w-full rounded-xl border border-mist bg-paper px-3 py-2 text-sm outline-none focus:border-moss" />
            <input value={form.remark} onChange={(e) => setForm({ ...form, remark: e.target.value })} placeholder="备注（可选）" className="w-full rounded-xl border border-mist bg-paper px-3 py-2 text-sm outline-none focus:border-moss" />
            {testResult && <p className="text-sm text-oat">{testResult}</p>}
            <div className="flex gap-2">
              <button onClick={save} className="rounded-xl bg-moss px-4 py-2 text-sm text-white hover:bg-moss/90">保存</button>
              <button onClick={test} disabled={testing} className="inline-flex items-center gap-1 rounded-xl border border-mist px-4 py-2 text-sm text-ink/70 hover:border-moss"><Zap className="h-4 w-4" /> {testing ? "测试中…" : "连通性测试"}</button>
              <button onClick={() => setShowForm(false)} className="rounded-xl border border-mist px-4 py-2 text-sm text-ink/70">取消</button>
            </div>
          </div>
        </div>
      )}

      <div className="space-y-2.5">
        {cfgs.map((c) => (
          <div key={c.id} className="flex flex-wrap items-center gap-3 rounded-2xl border border-mist bg-white/70 px-4 py-3">
            <div className="min-w-0 flex-1">
              <div className="flex items-center gap-2">
                <span className="font-medium text-ink">{c.name}</span>
                <span className={`rounded-full px-2 py-0.5 text-xs ${c.status === "ENABLED" ? "bg-moss/15 text-moss" : "bg-rose/10 text-rose"}`}>{c.status === "ENABLED" ? "启用" : "禁用"}</span>
              </div>
              <p className="mt-0.5 text-xs text-oat">{c.base_url} · {c.model} · API Key：{c.api_key_masked}</p>
            </div>
            <button onClick={() => toggle(c)} className="rounded-lg border border-mist px-3 py-1.5 text-xs text-ink/70 hover:border-moss">{c.status === "ENABLED" ? "禁用" : "启用"}</button>
            <button onClick={() => openEdit(c)} className="rounded-lg border border-mist p-1.5 text-oat hover:text-moss"><Pencil className="h-4 w-4" /></button>
            <button onClick={() => remove(c)} className="rounded-lg border border-mist p-1.5 text-oat hover:text-rose"><Trash2 className="h-4 w-4" /></button>
          </div>
        ))}
        {cfgs.length === 0 && <p className="py-10 text-center text-sm text-oat">还没有 AI 配置，将使用环境变量中的 DeepSeek Key</p>}
      </div>
    </div>
  );
}
