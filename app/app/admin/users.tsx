"use client";

import { useEffect, useState } from "react";
import { Pencil, ShieldCheck } from "lucide-react";
import { api } from "@/lib/client-api";

interface User { id: string; email: string; nickname: string | null; role: string; status: string; created_at: string; }

export default function UsersTab() {
  const [users, setUsers] = useState<User[]>([]);
  const [editing, setEditing] = useState<User | null>(null);
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");

  function load() { api<User[]>("/api/admin/users").then(setUsers).catch((e) => setError(e.message)); }
  useEffect(load, []);

  async function save() {
    if (!editing) return;
    setError("");
    try {
      await api(`/api/admin/users/${editing.id}`, { method: "PATCH", body: JSON.stringify({ role: editing.role, status: editing.status, nickname: editing.nickname, password }) });
      setEditing(null); setPassword(""); load();
    } catch (e) { setError((e as Error).message); }
  }

  return (
    <div>
      {error && <p className="mb-3 rounded-xl bg-rose/10 px-4 py-2.5 text-sm text-rose">{error}</p>}
      <div className="overflow-hidden rounded-2xl border border-mist bg-white/70">
        <table className="w-full text-sm">
          <thead><tr className="text-left text-xs text-oat">
            <th className="px-5 py-3 font-normal">账号</th><th className="px-3 py-3 font-normal">昵称</th><th className="px-3 py-3 font-normal">角色</th><th className="px-3 py-3 font-normal">状态</th><th className="px-5 py-3 text-right font-normal">操作</th>
          </tr></thead>
          <tbody>
            {users.map((u) => (
              <tr key={u.id} className="border-t border-mist/60 text-ink/80">
                <td className="px-5 py-3">{u.email}</td>
                <td className="px-3 py-3">{u.nickname ?? "-"}</td>
                <td className="px-3 py-3">{u.role === "ADMIN" ? <span className="inline-flex items-center gap-1 text-moss"><ShieldCheck className="h-3.5 w-3.5" /> 管理员</span> : "普通用户"}</td>
                <td className="px-3 py-3"><span className={u.status === "ENABLED" ? "text-moss" : "text-rose"}>{u.status === "ENABLED" ? "启用" : "禁用"}</span></td>
                <td className="px-5 py-3 text-right">
                  <button onClick={() => setEditing(u)} className="rounded-lg border border-mist p-1.5 text-oat hover:text-moss"><Pencil className="h-4 w-4" /></button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {editing && (
        <div className="mt-4 rounded-2xl border border-moss/30 bg-white/80 p-5">
          <h3 className="mb-3 font-medium text-ink">编辑账号：{editing.email}</h3>
          <div className="space-y-3">
            <div className="flex flex-wrap gap-3">
              <label className="text-sm">角色
                <select value={editing.role} onChange={(e) => setEditing({ ...editing, role: e.target.value })} className="ml-2 rounded-xl border border-mist bg-paper px-3 py-2 text-sm outline-none focus:border-moss">
                  <option value="USER">普通用户</option><option value="ADMIN">管理员</option>
                </select>
              </label>
              <label className="text-sm">状态
                <select value={editing.status} onChange={(e) => setEditing({ ...editing, status: e.target.value })} className="ml-2 rounded-xl border border-mist bg-paper px-3 py-2 text-sm outline-none focus:border-moss">
                  <option value="ENABLED">启用</option><option value="DISABLED">禁用</option>
                </select>
              </label>
            </div>
            <input value={editing.nickname ?? ""} onChange={(e) => setEditing({ ...editing, nickname: e.target.value })} placeholder="昵称" className="w-full rounded-xl border border-mist bg-paper px-3 py-2 text-sm outline-none focus:border-moss" />
            <input type="password" value={password} onChange={(e) => setPassword(e.target.value)} placeholder="重置密码（留空则不修改）" className="w-full rounded-xl border border-mist bg-paper px-3 py-2 text-sm outline-none focus:border-moss" />
            <div className="flex gap-2">
              <button onClick={save} className="rounded-xl bg-moss px-4 py-2 text-sm text-white hover:bg-moss/90">保存</button>
              <button onClick={() => { setEditing(null); setPassword(""); }} className="rounded-xl border border-mist px-4 py-2 text-sm text-ink/70">取消</button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}