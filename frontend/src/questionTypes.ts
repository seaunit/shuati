export type QuestionType = "SINGLE" | "MULTI" | "TRUE_FALSE" | "SHORT";

export interface QuestionOption {
  key: string;
  text: string;
}

export const TRUE_FALSE_OPTIONS: readonly QuestionOption[] = [
  { key: "T", text: "正确" },
  { key: "F", text: "错误" },
];

export function typeLabel(type: string, short = false): string {
  if (type === "SINGLE") return short ? "单选" : "单选题";
  if (type === "MULTI") return short ? "多选" : "多选题";
  if (type === "TRUE_FALSE") return "判断题";
  if (type === "SHORT") return short ? "简答" : "简答题";
  return type;
}

export function isChoiceQuestion(type: string): boolean {
  return type === "SINGLE" || type === "MULTI" || type === "TRUE_FALSE";
}

export function isSingleChoiceQuestion(type: string): boolean {
  return type === "SINGLE" || type === "TRUE_FALSE";
}

export function allowsCustomOptions(type: string): boolean {
  return type === "SINGLE" || type === "MULTI";
}

export function normalizeTrueFalseAnswer(value: unknown): "T" | "F" | "" {
  const normalized = String(value ?? "")
    .replace(/\uFEFF/g, "")
    .trim()
    .replace(/[。.!！\s]+$/g, "")
    .toUpperCase();
  if (["T", "TRUE", "正确", "对", "是", "√", "✓", "✔", "☑"].includes(normalized)) {
    return "T";
  }
  if (["F", "FALSE", "错误", "错", "否", "×", "✕", "✗", "☒"].includes(normalized)) {
    return "F";
  }
  return "";
}

export function answerText(type: string, value: unknown): string {
  const raw = String(value ?? "");
  if (type !== "TRUE_FALSE") return raw;
  const normalized = normalizeTrueFalseAnswer(raw);
  return normalized === "T" ? "正确" : normalized === "F" ? "错误" : raw;
}

export function optionBadgeLabel(type: string, key: string): string {
  if (type !== "TRUE_FALSE") return key;
  return key === "T" ? "✓" : "✗";
}
