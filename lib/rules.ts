export interface Option {
  key: string;
  text: string;
}

export type QuestionType = "SINGLE" | "MULTI" | "SHORT";

export function optionKeys(options: unknown): string[] {
  if (!Array.isArray(options) || options.length === 0) {
    throw new Error("选择题必须提供选项");
  }
  const keys: string[] = [];
  const seen = new Set<string>();
  for (const opt of options) {
    const key = String((opt as { key?: string }).key ?? "").trim().toUpperCase();
    if (!key) continue;
    if (!seen.has(key)) {
      seen.add(key);
      keys.push(key);
    }
  }
  if (keys.length === 0) {
    throw new Error('选项格式不正确（应为 [{"key":"A","text":"..."}]）');
  }
  return keys;
}

export function normalizeAnswer(answer: string | null | undefined): string {
  if (!answer) return "";
  return answer
    .replace(/[^A-Za-z]/g, "")
    .toUpperCase()
    .split("")
    .sort()
    .join("");
}

export function validateQuestion(
  type: QuestionType,
  options: unknown,
  answer: string | null | undefined,
  keyPoints: unknown,
) {
  if (type === "SINGLE" || type === "MULTI") {
    const keys = optionKeys(options);
    const normalized = normalizeAnswer(answer);
    if (type === "SINGLE") {
      if (keys.length !== 4) throw new Error(`单选题必须恰好 4 个选项（当前 ${keys.length} 个）`);
      if (normalized.length !== 1) throw new Error("单选题答案必须是 1 个选项字母");
    } else {
      if (keys.length < 4 || keys.length > 6) {
        throw new Error(`多选题选项数量须在 4~6 个（当前 ${keys.length} 个）`);
      }
      if (normalized.length < 2) throw new Error("多选题答案必须至少 2 个选项字母");
    }
    for (const c of normalized) {
      if (!keys.includes(c)) {
        throw new Error(`答案字母 ${c} 不在选项中（可选：${keys.join("")}）`);
      }
    }
  } else if (type === "SHORT") {
    if (!Array.isArray(keyPoints) || keyPoints.length === 0) {
      throw new Error("简答题必须提供评分要点");
    }
  } else {
    throw new Error("type 只能是 SINGLE / MULTI / SHORT");
  }
}

export function normalizeSelected(selected: string | null | undefined): string {
  return normalizeAnswer(selected);
}