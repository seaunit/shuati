import { getAdminClient } from "./supabase/admin";
import { chatJson, logUsage } from "./ai";
import { EXTRACT_SYSTEM } from "./prompts";
import { normalizeAnswer, validateQuestion, type QuestionType } from "./rules";

const CHUNK_SIZE = 5000;
const MAX_TEXT_LENGTH = 100_000;

export interface ImportItem {
  unit: string;
  type: QuestionType;
  content: string;
  options?: { key: string; text: string }[];
  answer: string;
  key_points?: string[];
  difficulty?: "EASY" | "MEDIUM" | "HARD";
  explanation?: string;
  source_url?: string;
}

export function splitText(text: string, maxLen = CHUNK_SIZE): string[] {
  const t = (text ?? "").trim();
  if (t.length <= maxLen) return t ? [t] : [];
  const blocks = t.split(/\n\s*\n/);
  const chunks: string[] = [];
  let sb = "";
  const push = () => {
    const v = sb.trim();
    if (v) chunks.push(v);
    sb = "";
  };
  for (let block of blocks) {
    if (!block.trim()) continue;
    while (block.length > maxLen) {
      if (sb) push();
      chunks.push(block.slice(0, maxLen).trim());
      block = block.slice(maxLen);
    }
    if (sb.length + block.length > maxLen && sb) push();
    sb += block + "\n\n";
  }
  push();
  return chunks;
}

export function htmlToText(html: string): string {
  return html
    .replace(/<script[\s\S]*?<\/script>/gi, " ")
    .replace(/<style[\s\S]*?<\/style>/gi, " ")
    .replace(/<[^>]+>/g, "\n")
    .replace(/&nbsp;/g, " ")
    .replace(/&lt;/g, "<")
    .replace(/&gt;/g, ">")
    .replace(/&amp;/g, "&")
    .replace(/&quot;/g, '"')
    .replace(/&#39;/g, "'")
    .replace(/\n{3,}/g, "\n\n")
    .trim();
}

export async function fetchUrlText(url: string): Promise<string> {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), 20_000);
  try {
    const resp = await fetch(url, {
      headers: {
        "User-Agent":
          "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Safari/537.36",
      },
      signal: controller.signal,
    });
    if (!resp.ok) throw new Error("抓取网页失败（HTTP " + resp.status + "）");
    const html = await resp.text();
    return htmlToText(html);
  } catch (e) {
    throw new Error("抓取网页失败：" + (e instanceof Error ? e.message : String(e)) + "（反爬站点请改用粘贴正文导入）");
  } finally {
    clearTimeout(timer);
  }
}

async function extractDocText(buffer: Buffer, fileName: string): Promise<string> {
  const ext = (fileName.split(".").pop() ?? "").toLowerCase();
  if (ext === "txt" || ext === "md" || ext === "csv") {
    return buffer.toString("utf8");
  }
  try {
    const { parseOffice } = await import("officeparser");
    const ast = (await parseOffice(buffer)) as { toText?: () => string };
    if (typeof ast?.toText === "function") return ast.toText();
    return String(ast ?? "");
  } catch (e) {
    throw new Error("文档解析失败，请确认文件为 Word/PPT/PDF，或改用粘贴正文导入");
  }
}

async function sourceTextOf(task: {
  kind: string;
  source_text: string | null;
  source_name: string | null;
}): Promise<string> {
  if (task.kind === "text") return task.source_text ?? "";
  if (task.kind === "url") return await fetchUrlText(task.source_text ?? "");
  if (task.kind === "doc") {
    if (!task.source_text) throw new Error("缺少文档内容");
    const buffer = Buffer.from(task.source_text, "base64");
    return await extractDocText(buffer, task.source_name ?? "doc");
  }
  throw new Error("未知导入类型");
}

async function extractChunk(text: string, userId: string): Promise<ImportItem[]> {
  const start = Date.now();
  let result: Awaited<ReturnType<typeof chatJson>> | null = null;
  let err: string | null = null;
  try {
    result = await chatJson("EXTRACT", EXTRACT_SYSTEM, "【原文】\n" + text);
    const root = JSON.parse(result.content) as { questions?: unknown } | unknown[];
    const arr = Array.isArray(root) ? root : (root as { questions?: unknown }).questions;
    if (!Array.isArray(arr)) throw new Error("AI 解析结果格式不正确");
    return (arr as unknown[]).map((it) => normalizeItem(it as Record<string, unknown>));
  } catch (e) {
    err = e instanceof Error ? e.message : String(e);
    throw new Error("AI 解析失败：" + err);
  } finally {
    await logUsage(userId, "EXTRACT", "deepseek-chat", null, result, err, Date.now() - start);
  }
}

function normalizeItem(raw: Record<string, unknown>): ImportItem {
  const unit = String(raw.unit ?? "未分类").trim() || "未分类";
  const type = String(raw.type ?? "").toUpperCase() as QuestionType;
  const content = String(raw.content ?? "").trim();
  const answer = String(raw.answer ?? "").trim();
  const optionsRaw = raw.options;
  const keyPointsRaw = raw.key_points;
  const difficultyRaw = String(raw.difficulty ?? "MEDIUM").toUpperCase();

  let options: { key: string; text: string }[] | undefined;
  if (Array.isArray(optionsRaw)) {
    options = optionsRaw
      .map((o) => ({
        key: String((o as { key?: string }).key ?? "").trim().toUpperCase(),
        text: String((o as { text?: string }).text ?? "").trim(),
      }))
      .filter((o) => o.key && o.text);
  }

  let keyPoints: string[] | undefined;
  if (Array.isArray(keyPointsRaw)) {
    keyPoints = keyPointsRaw.map((k) => String(k)).filter(Boolean);
  }

  const item: ImportItem = {
    unit,
    type,
    content,
    answer,
    options,
    key_points: keyPoints,
    difficulty: (["EASY", "MEDIUM", "HARD"].includes(difficultyRaw)
      ? difficultyRaw
      : "MEDIUM") as "EASY" | "MEDIUM" | "HARD",
    explanation: raw.explanation ? String(raw.explanation) : undefined,
  };

  // 补齐单选/多选选项或简答要点，交给规则校验兜底
  if ((type === "SINGLE" || type === "MULTI") && options && options.length) {
    item.options = options;
  }
  if (type === "SHORT" && keyPoints && keyPoints.length) {
    item.key_points = keyPoints;
  }

  validateQuestion(item.type, item.options, item.answer, item.key_points);
  return item;
}

export async function runImportTask(taskId: string) {
  const admin = getAdminClient();
  const { data: task } = await admin.from("import_task").select("*").eq("id", taskId).single();
  if (!task) return;

  try {
    if (task.phase === "PARSE") {
      await admin
        .from("import_task")
        .update({ status: "RUNNING", progress: 0, error: null })
        .eq("id", taskId);

      const text = await sourceTextOf(task);
      const capped = text.length > MAX_TEXT_LENGTH ? text.slice(0, MAX_TEXT_LENGTH) : text;
      const chunks = splitText(capped, CHUNK_SIZE);
      if (chunks.length === 0) throw new Error("未提取到可解析的文本内容");

      await admin
        .from("import_task")
        .update({ total_chunks: chunks.length, done_chunks: 0 })
        .eq("id", taskId);

      const questions: ImportItem[] = [];
      const chunkErrors: string[] = [];
      for (let i = 0; i < chunks.length; i++) {
        try {
          const list = await extractChunk(chunks[i], task.user_id);
          questions.push(...list);
        } catch (e) {
          chunkErrors.push("第 " + (i + 1) + " 块：" + (e instanceof Error ? e.message : String(e)));
        }
        const done = i + 1;
        await admin
          .from("import_task")
          .update({
            done_chunks: done,
            progress: Math.round((done * 100) / chunks.length),
          })
          .eq("id", taskId);
      }

      await admin
        .from("import_task")
        .update({
          items: questions,
          progress: 100,
          phase: "READY",
          status: chunkErrors.length ? "FAILED" : "COMPLETED",
          error: chunkErrors.length ? chunkErrors.join("\n") : null,
        })
        .eq("id", taskId);
      return;
    }

    if (task.phase === "IMPORT") {
      await admin
        .from("import_task")
        .update({ status: "RUNNING", progress: 0, error: null })
        .eq("id", taskId);

      const items: ImportItem[] = Array.isArray(task.items) ? (task.items as ImportItem[]) : [];
      const selected: number[] = Array.isArray(task.selected_indexes)
        ? (task.selected_indexes as number[])
        : [];
      const chosen = selected.length ? selected.map((i) => items[i]).filter(Boolean) : items;
      if (chosen.length === 0) throw new Error("没有可导入的题目");

      const bankId = await importBankAndQuestions(task, chosen);
      await admin
        .from("import_task")
        .update({
          bank_id: bankId,
          progress: 100,
          phase: "DONE",
          status: "COMPLETED",
        })
        .eq("id", taskId);
      return;
    }
  } catch (e) {
    const msg = e instanceof Error ? e.message : String(e);
    await admin
      .from("import_task")
      .update({ status: "FAILED", error: msg })
      .eq("id", taskId);
  }
}

async function importBankAndQuestions(
  task: { user_id: string; bank_name: string; bank_description: string | null; source_text: string | null; kind: string },
  items: ImportItem[],
): Promise<number> {
  const admin = getAdminClient();

  const { data: profile } = await admin
    .from("profiles")
    .select("role")
    .eq("id", task.user_id)
    .single();
  const ownerId = profile?.role === "ADMIN" ? null : task.user_id;

  const { data: bank, error: bankErr } = await admin
    .from("bank")
    .insert({
      name: task.bank_name.trim(),
      description: task.bank_description,
      owner_id: ownerId,
    })
    .select("id")
    .single();
  if (bankErr) {
    if (bankErr.message.toLowerCase().includes("duplicate")) {
      throw new Error("题库名已存在，请更换题库名");
    }
    throw new Error("创建题库失败：" + bankErr.message);
  }
  const bankId = bank.id as number;

  const unitMap = new Map<string, number>();
  let sort = 0;
  for (const item of items) {
    const name = item.unit.trim() || "未分类";
    if (!unitMap.has(name)) {
      const { data: u } = await admin
        .from("unit")
        .insert({ bank_id: bankId, name, sort: sort++ })
        .select("id")
        .single();
      if (u) unitMap.set(name, u.id as number);
    }
  }

  const rows = items.map((item, idx) => {
    const unitId = unitMap.get(item.unit.trim() || "未分类")!;
    return {
      unit_id: unitId,
      type: item.type,
      content: item.content,
      options: item.type === "SINGLE" || item.type === "MULTI" ? item.options : null,
      answer: item.type === "SINGLE" || item.type === "MULTI" ? normalizeAnswer(item.answer) : item.answer,
      key_points: item.type === "SHORT" ? item.key_points : null,
      difficulty: item.difficulty ?? "MEDIUM",
      explanation: item.explanation ?? null,
      tags: [],
      source_url: task.kind === "url" ? task.source_text : null,
      status: "ON",
    };
  });

  // 批量插入，降低往返；每批 300 行
  for (let i = 0; i < rows.length; i += 300) {
    const { error } = await admin.from("question").insert(rows.slice(i, i + 300));
    if (error) throw new Error("写入题目失败：" + error.message);
  }

  return bankId;
}

export function triggerWorker(taskId: string) {
  if (process.env.NETLIFY === "true") {
    const base = process.env.URL || process.env.DEPLOY_URL;
    if (base) {
      fetch(base.replace(/\/$/, "") + "/.netlify/functions/import-worker-background", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ taskId }),
      }).catch(() => {
        // 后台函数触发失败时，退化为进程内处理
        void runImportTask(taskId).catch(() => {});
      });
      return;
    }
  }
  void runImportTask(taskId).catch(() => {
    // 失败状态由 runImportTask 写入
  });
}