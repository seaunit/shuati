"use client";

import React from "react";

function renderInline(text: string): React.ReactNode[] {
  const nodes: React.ReactNode[] = [];
  const regex = /(`[^`]+`|\*\*[^*]+\*\*|\*[^*]+\*|\[[^\]]+\]\([^)]+\))/g;
  let last = 0;
  let m: RegExpExecArray | null;
  let key = 0;
  while ((m = regex.exec(text)) !== null) {
    if (m.index > last) nodes.push(text.slice(last, m.index));
    const token = m[0];
    if (token.startsWith("`")) {
      nodes.push(
        <code key={key++} className="rounded bg-mist px-1 py-0.5 font-mono text-[0.9em] text-ink">
          {token.slice(1, -1)}
        </code>,
      );
    } else if (token.startsWith("**")) {
      nodes.push(<strong key={key++}>{token.slice(2, -2)}</strong>);
    } else if (token.startsWith("*")) {
      nodes.push(<em key={key++}>{token.slice(1, -1)}</em>);
    } else {
      const linkMatch = token.match(/^\[([^\]]+)\]\(([^)]+)\)$/);
      if (linkMatch) {
        nodes.push(
          <a key={key++} href={linkMatch[2]} target="_blank" rel="noreferrer" className="text-moss underline">
            {linkMatch[1]}
          </a>,
        );
      } else {
        nodes.push(token);
      }
    }
    last = m.index + token.length;
  }
  if (last < text.length) nodes.push(text.slice(last));
  return nodes;
}

export default function Markdown({ text, className }: { text: string; className?: string }) {
  const lines = (text ?? "").split("\n");
  const out: React.ReactNode[] = [];
  let i = 0;
  let listItems: React.ReactNode[] = [];
  let inList = false;
  let inCode = false;
  let codeBuf: string[] = [];

  const flushList = () => {
    if (inList) {
      out.push(<ul key={"ul" + i} className="my-2 list-disc space-y-1 pl-5">{listItems}</ul>);
      listItems = [];
      inList = false;
    }
  };

  while (i < lines.length) {
    const line = lines[i];
    if (line.trim().startsWith("```")) {
      if (inCode) {
        out.push(
          <pre key={"pre" + i} className="my-2 overflow-x-auto rounded-xl bg-ink/90 p-3 text-sm text-paper">
            <code>{codeBuf.join("\n")}</code>
          </pre>,
        );
        codeBuf = [];
        inCode = false;
      } else {
        flushList();
        inCode = true;
      }
      i++;
      continue;
    }
    if (inCode) {
      codeBuf.push(line);
      i++;
      continue;
    }
    if (/^\s*[-*]\s+/.test(line)) {
      if (!inList) inList = true;
      listItems.push(<li key={i}>{renderInline(line.replace(/^\s*[-*]\s+/, ""))}</li>);
      i++;
      continue;
    }
    if (/^\s*\d+[.)]\s+/.test(line)) {
      flushList();
      out.push(
        <p key={i} className="my-1">
          {renderInline(line)}
        </p>,
      );
      i++;
      continue;
    }
    if (line.trim().startsWith("### ")) {
      flushList();
      out.push(<h4 key={i} className="mt-4 mb-1 font-semibold text-ink">{renderInline(line.trim().slice(4))}</h4>);
      i++;
      continue;
    }
    if (line.trim().startsWith("## ")) {
      flushList();
      out.push(<h3 key={i} className="mt-4 mb-1 font-semibold text-ink">{renderInline(line.trim().slice(3))}</h3>);
      i++;
      continue;
    }
    if (line.trim().startsWith("# ")) {
      flushList();
      out.push(<h2 key={i} className="mt-4 mb-1 font-semibold text-ink">{renderInline(line.trim().slice(2))}</h2>);
      i++;
      continue;
    }
    if (line.trim() === "") {
      flushList();
      i++;
      continue;
    }
    flushList();
    out.push(
      <p key={i} className="my-1 leading-relaxed">
        {renderInline(line)}
      </p>,
    );
    i++;
  }
  flushList();
  if (inCode) {
    out.push(
      <pre key="pre-tail" className="my-2 overflow-x-auto rounded-xl bg-ink/90 p-3 text-sm text-paper">
        <code>{codeBuf.join("\n")}</code>
      </pre>,
    );
  }
  return <div className={className}>{out}</div>;
}