export type SpeechSegmentKind = "text" | "code";

export type SpeechSegmentSource =
  | "verdict"
  | "answer"
  | "feedback"
  | "explanation"
  | "code";

export interface SpeechSegment {
  id: string;
  text: string;
  kind: SpeechSegmentKind;
  source: SpeechSegmentSource;
}

export interface SpeechContent {
  verdict?: string;
  correctAnswer?: string;
  referenceAnswer?: string;
  feedback?: string;
  hitPoints?: string[];
  missedPoints?: string[];
  explanation?: string;
}

export interface SpeechBuildOptions {
  readCode: boolean;
}

const MAX_SEGMENT_LENGTH = 180;

export function buildSpeechSegments(
  content: SpeechContent,
  options: SpeechBuildOptions,
): SpeechSegment[] {
  const segments: SpeechSegment[] = [];
  const push = (
    text: string,
    source: SpeechSegmentSource,
    kind: SpeechSegmentKind = "text",
  ) => {
    const normalized = text.replace(/\s+/g, " ").trim();
    if (!normalized) return;
    segments.push({
      id: `${source}-${segments.length}`,
      text: normalized,
      kind,
      source,
    });
  };

  if (content.verdict) push(`判定结果：${content.verdict}。`, "verdict");
  if (content.correctAnswer) {
    push(`正确答案：${content.correctAnswer.split("").join("、")}。`, "answer");
  }
  if (content.referenceAnswer) {
    appendMarkdown(push, content.referenceAnswer, "answer", options.readCode);
  }
  if (content.feedback) {
    appendMarkdown(push, content.feedback, "feedback", options.readCode);
  }
  for (const point of content.hitPoints ?? []) {
    appendMarkdown(push, `命中要点：${point}`, "feedback", options.readCode);
  }
  for (const point of content.missedPoints ?? []) {
    appendMarkdown(push, `遗漏要点：${point}`, "feedback", options.readCode);
  }
  if (content.explanation) {
    appendMarkdown(push, content.explanation, "explanation", options.readCode);
  }

  return segments;
}

function appendMarkdown(
  push: (text: string, source: SpeechSegmentSource, kind?: SpeechSegmentKind) => void,
  markdown: string,
  source: SpeechSegmentSource,
  readCode: boolean,
) {
  const lines = markdown.replace(/\r\n?/g, "\n").split("\n");
  let inCode = false;
  let codeLines: string[] = [];

  const flushText = (text: string) => {
    const plain = cleanMarkdown(text);
    for (const sentence of splitSentences(plain)) {
      push(sentence, source);
    }
  };
  const flushCode = () => {
    const code = codeLines.join("\n").trim();
    codeLines = [];
    if (!code) return;
    if (!readCode) {
      push("此处有一段代码。", "code", "code");
      return;
    }
    push(`代码内容：${simplifyCode(code)}`, "code", "code");
  };

  let textBuffer: string[] = [];
  for (const line of lines) {
    if (/^\s*(```|~~~)/.test(line)) {
      flushText(textBuffer.join("\n"));
      textBuffer = [];
      if (inCode) flushCode();
      inCode = !inCode;
      continue;
    }
    if (inCode) {
      codeLines.push(line);
    } else {
      textBuffer.push(line);
    }
  }
  flushText(textBuffer.join("\n"));
  if (inCode) flushCode();
}

export function cleanMarkdown(markdown: string): string {
  return markdown
    .replace(/!\[([^\]]*)\]\([^)]+\)/g, "$1")
    .replace(/\[([^\]]+)\]\([^)]+\)/g, "$1")
    .replace(/<[^>]+>/g, " ")
    .replace(/^[ \t]{0,3}#{1,6}\s*/gm, "")
    .replace(/^[ \t]{0,3}>\s?/gm, "")
    .replace(/^[ \t]{0,3}(?:[-*+]|\d+[.)])\s+/gm, "")
    .replace(/^[ \t]{0,3}(?:---|\*\*\*|___)[ \t]*$/gm, "")
    .replace(/`([^`]+)`/g, "$1")
    .replace(/(\*\*|__)(.*?)\1/g, "$2")
    .replace(/(\*|_)(.*?)\1/g, "$2")
    .replace(/~~(.*?)~~/g, "$1")
    .replace(/\\([\\`*_[\]{}()#+\-.!])/g, "$1")
    .replace(/[ \t]+/g, " ")
    .replace(/\n{2,}/g, "\n")
    .trim();
}

function simplifyCode(code: string): string {
  return code
    .replace(/[{}()[\];]/g, " ")
    .replace(/\s+/g, " ")
    .trim();
}

export function splitSentences(text: string): string[] {
  const normalized = text
    .replace(/([。！？!?；;：:])\s*\n+/g, "$1")
    .replace(/\n+/g, "。")
    .replace(/\s+/g, " ")
    .trim();
  if (!normalized) return [];
  const rawParts = normalized
    .split(/(?<=[。！？!?；;])\s*/)
    .flatMap((part) => part.split(/(?<=[：:])\s*/))
    .map((part) => part.trim())
    .filter((part) => /[\p{L}\p{N}]/u.test(part));
  return rawParts.flatMap(chunkLongSentence);
}

function chunkLongSentence(sentence: string): string[] {
  if (sentence.length <= MAX_SEGMENT_LENGTH) return [sentence];
  const chunks: string[] = [];
  let rest = sentence;
  while (rest.length > MAX_SEGMENT_LENGTH) {
    const window = rest.slice(0, MAX_SEGMENT_LENGTH + 1);
    const breakAt = Math.max(
      window.lastIndexOf("，"),
      window.lastIndexOf(","),
      window.lastIndexOf(" "),
    );
    const cut = breakAt > 40 ? breakAt + 1 : MAX_SEGMENT_LENGTH;
    chunks.push(rest.slice(0, cut).trim());
    rest = rest.slice(cut).trim();
  }
  if (rest) chunks.push(rest);
  return chunks;
}
