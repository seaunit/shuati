"use client";

import { useEffect, useRef, useState } from "react";
import { Upload, Loader2, FileText, Link2, ClipboardList } from "lucide-react";
import { api, typeLabel } from "@/lib/client-api";

interface Item { unit: string; type: string; content: string; options?: { key: string; text: string }[]; answer: string; key_points?: string[]; difficulty?: string; explanation?: string; }
interface Task {
  id: string; kind: string; phase: string; status: string; progress: number; total_chunks: number; done_chunks: number;
  bank_name: string; bank_description: string | null; items: Item[] | null; error: string | null; bank_id: number | null;
}

export default function ImportTab() {
  const [kind, setKind] = useState<"doc" | "text" | "url">("text");
  const [bankName, setBankName] = useState("");
  const [bankDesc, setBankDesc] = useState("");
  const [text, setText] = useState("");
  const [url, setUrl] = useState("");
  const [fileName, setFileName] = useState("");
  const [base64, setBase64] = useState("");
  const [taskId, setTaskId] = useState<string | null>(null);
  const [task, setTask] = useState<Task | null>(null);
  const [selected, setSelected] = useState<Set<number>>(new Set());
  const [submitting, setSubmitting] = useState(false);
  const [importing, setImporting] = useState(false);
  const [error, setError] = useState("");
  const timer = useRef<ReturnType<typeof setInterval> | null>(null);

  useEffect(() => () => { if (timer.current) clearInterval(timer.current); }, []);

  function startPolling(id: string) {
    if (timer.current) clearInterval(timer.current);
    timer.current = setInterval(async () => {
      try {
        const t = await api<Task>(`/api/import/task/${id}`);
        setTask(t);
        const terminal = t.phase === "READY" || t.phase === "DONE" || t.status === "FAILED";
        if (terminal && timer.current) { clearInterval(timer.current); timer.current = null; }
      } catch {
        /* 忽略轮询失败，等待下次 */
      }
    }, 3000);
  }

  async function start() {
    setError("");
    if (!bankName.trim()) { setError("题库名称必填"); return; }
    if (kind === "text" && !text.trim()) { setError("请粘贴正文内容"); return; }
    if (kind === "url" && !url.trim()) { setError("请填写网页链接"); return; }
    if (kind === "doc" && !base64) { setError("请上传文档"); return; }
    setSubmitting(true);
    setTask(null);
    setSelected(new Set());
    try {
      const res = await api<{ taskId: string }>("/api/import", {
        method: "POST",
        body: JSON.stringify({ kind, bankName, bankDescription: bankDesc, text, url, fileName, base64 }),
      });
      setTaskId(res.taskId);
      startPolling(res.taskId);
    } catch (e) { setError((e as Error).message); }
    finally { setSubmitting(false); }
  }

  async function doImport() {
    if (!task) return;
    if (!task.bank_name || !task.bank_name.trim()) { setError("题库名称必填"); return; }
    setImporting(true);
    setError("");
    try {
      await api(`/api/import/task/${task.id}/import`, { method: "POST", body: JSON.stringify({ selectedIndexes: [...selected] }) });
      setTask(null);
      startPolling(task.id);
    } catch (e) { setError((e as Error).message); }
    finally { setImporting(false); }
  }

  function onFile(e: React.ChangeEvent<HTMLInputElement>) {
    const f = e.target.files?.[0];
    if (!f) return;
    setFileName(f.name);
    const reader = new FileReader();
    reader.onload = () => setBase64(String(reader.result).split(",")[1] ?? "");
    reader.readAsDataURL(f);
  }

  function toggleItem(i: number) {
    setSelected((prev) => { const next = new Set(prev); if (next.has(i)) next.delete(i); else next.add(i); return next; });
  }

  function toggleAll() {
    if (!task?.items) return;
    setSelected(selected.size === task.items.length ? new Set() : new Set(task.items.map((_, i) => i)));
  }

  const ready = task && task.phase === "READY" && (task.status === "COMPLETED" || task.status === "FAILED");
  const done = task && task.phase === "DONE" && task.status === "COMPLETED";
  const failed = task && task.status === "FAILED" && task.phase !== "READY";

  return (
    <div className="space-y-4">
      <div className="rounded-2xl border border-mist bg-white/70 p-5">
        <div className="mb-4 flex rounded-xl bg-mist p-1">
          {([["doc", "文档导入", FileText], ["text", "粘贴正文", ClipboardList], ["url", "网页链接", Link2]] as const).map(([k, label, Icon]) => (
            <button key={k} onClick={() => setKind(k)} className={`flex flex-1 items-center justify-center gap-1.5 rounded-lg py-2 text-sm transition ${kind === k ? "bg-white text-ink shadow-sm" : "text-oat"}`}>
              <Icon className="h-4 w-4" /> {label}
            </button>
          ))}
        </div>

        <div className="space-y-3">
          <div className="flex flex-wrap gap-3">
            <input value={bankName} onChange={(e) => setBankName(e.target.value)} placeholder="题库名称（必填）" className="min-w-44 flex-1 rounded-xl border border-mist bg-paper px-3 py-2 text-sm outline-none focus:border-moss" />
            <input value={bankDesc} onChange={(e) => setBankDesc(e.target.value)} placeholder="题库描述（可选）" className="min-w-44 flex-1 rounded-xl border border-mist bg-paper px-3 py-2 text-sm outline-none focus:border-moss" />
          </div>

          {kind === "text" && (
            <textarea value={text} onChange={(e) => setText(e.target.value)} rows={8} placeholder="粘贴题目正文，AI 将自动划分单元、题型、选项与答案（最多 10 万字）" className="w-full rounded-xl border border-mist bg-paper px-4 py-2.5 text-sm outline-none focus:border-moss" />
          )}
          {kind === "url" && (
            <input value={url} onChange={(e) => setUrl(e.target.value)} placeholder="https://..." className="w-full rounded-xl border border-mist bg-paper px-3 py-2 text-sm outline-none focus:border-moss" />
          )}
          {kind === "doc" && (
            <div className="rounded-xl border border-dashed border-mist bg-paper px-4 py-6 text-center">
              <input id="doc-file" type="file" accept=".docx,.pptx,.pdf,.txt,.md" onChange={onFile} className="hidden" />
              <label htmlFor="doc-file" className="cursor-pointer text-sm text-oat hover:text-moss">
                <Upload className="mx-auto mb-2 h-5 w-5" />
                {fileName ? `已选择：${fileName}` : "点击上传 Word / PPT / PDF 文档"}
              </label>
            </div>
          )}

          {error && <p className="rounded-xl bg-rose/10 px-4 py-2.5 text-sm text-rose">{error}</p>}

          <button onClick={start} disabled={submitting} className="inline-flex items-center gap-1.5 rounded-xl bg-ink px-5 py-2.5 text-sm text-white hover:bg-ink/90 disabled:opacity-60">
            {submitting ? <Loader2 className="h-4 w-4 animate-spin" /> : <Upload className="h-4 w-4" />} 开始解析
          </button>
        </div>
      </div>

      {task && !done && (
        <div className="rounded-2xl border border-mist bg-white/70 p-5">
          <div className="flex items-center justify-between text-sm">
            <span className="text-ink">{ready ? "解析完成，请选择要导入的题目" : failed ? "解析失败" : "AI 正在解析…"}</span>
            <span className="text-xs text-oat">{task.done_chunks}/{task.total_chunks} 块 · {task.progress}%</span>
          </div>
          <div className="mt-2 h-2 overflow-hidden rounded-full bg-mist">
            <div className="h-full rounded-full bg-moss transition-all" style={{ width: `${task.progress}%` }} />
          </div>
          {failed && task.error && <p className="mt-3 rounded-xl bg-rose/10 px-4 py-2.5 text-sm text-rose">{task.error}</p>}
        </div>
      )}

      {ready && task.items && (
        <div className="rounded-2xl border border-mist bg-white/70 p-5">
          <div className="mb-3 flex items-center justify-between gap-3">
            <div className="flex items-center gap-3">
              <label className="inline-flex cursor-pointer items-center gap-1.5 text-sm text-ink">
                <input type="checkbox" checked={selected.size === task.items.length} onChange={toggleAll} className="h-4 w-4 accent-moss" />
                全选
              </label>
              <span className="text-sm font-medium text-ink">共解析出 {task.items.length} 道题</span>
            </div>
            <button onClick={doImport} disabled={importing} className="inline-flex items-center gap-1.5 rounded-xl bg-moss px-4 py-2 text-sm text-white hover:bg-moss/90 disabled:opacity-60">
              {importing ? <Loader2 className="h-4 w-4 animate-spin" /> : null} 导入选中（{selected.size}）
            </button>
          </div>
          <div className="max-h-96 space-y-2 overflow-y-auto pr-1">
            {task.items.map((it, i) => (
              <label key={i} className={`flex cursor-pointer items-start gap-3 rounded-xl border px-4 py-3 transition ${selected.has(i) ? "border-moss bg-moss/5" : "border-mist bg-paper"}`}>
                <input type="checkbox" checked={selected.has(i)} onChange={() => toggleItem(i)} className="mt-1 h-4 w-4 accent-moss" />
                <div className="min-w-0 flex-1">
                  <div className="mb-1 flex items-center gap-2 text-xs text-oat">
                    <span className="rounded-full bg-sand/40 px-2 py-0.5">{it.unit}</span>
                    <span>{typeLabel(it.type)}</span>
                  </div>
                  <p className="line-clamp-2 text-sm text-ink">{it.content}</p>
                  <p className="mt-1 text-xs text-oat">答案：{it.answer}</p>
                </div>
              </label>
            ))}
          </div>
        </div>
      )}

      {done && (
        <div className="rounded-2xl border border-moss/30 bg-moss/10 p-6 text-center">
          <p className="font-medium text-moss">导入完成</p>
          <p className="mt-1 text-sm text-oat">题库「{task.bank_name}」已创建，可到题库管理或首页查看</p>
          <button onClick={() => { setTask(null); setTaskId(null); setSelected(new Set()); }} className="mt-3 text-sm text-moss hover:underline">继续导入其他题库</button>
        </div>
      )}
    </div>
  );
}