<script setup lang="ts">
import { computed } from "vue";

const props = defineProps<{ text?: string | null }>();

function escapeHtml(value: string) {
  return value
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;");
}

function renderInline(value: string) {
  return value
    .replace(/`([^`]+)`/g, '<code class="rounded bg-mist px-1 py-0.5 font-mono text-[0.9em]">$1</code>')
    .replace(/\*\*([^*]+)\*\*/g, "<strong>$1</strong>")
    .replace(/\*([^*]+)\*/g, "<em>$1</em>")
    .replace(
      /\[([^\]]+)\]\((https?:\/\/[^)]+)\)/g,
      '<a href="$2" target="_blank" rel="noreferrer" class="text-moss underline">$1</a>',
    );
}

function renderMarkdown(value: string) {
  const lines = escapeHtml(value).split(/\r?\n/);
  const out: string[] = [];
  let inCode = false;
  let codeBuffer: string[] = [];
  for (const line of lines) {
    if (line.trim().startsWith("```")) {
      if (inCode) {
        out.push(
          '<pre class="overflow-x-auto rounded-xl bg-mist p-3 font-mono text-xs leading-relaxed">' +
            codeBuffer.join("\n") +
            "</pre>",
        );
        codeBuffer = [];
        inCode = false;
      } else {
        inCode = true;
      }
      continue;
    }
    if (inCode) {
      codeBuffer.push(line);
      continue;
    }
    if (!line.trim()) {
      out.push('<div class="h-2"></div>');
      continue;
    }
    out.push("<p>" + renderInline(line) + "</p>");
  }
  if (inCode) {
    out.push(
      '<pre class="overflow-x-auto rounded-xl bg-mist p-3 font-mono text-xs leading-relaxed">' +
        codeBuffer.join("\n") +
        "</pre>",
    );
  }
  return out.join("");
}

const html = computed(() => renderMarkdown(props.text ?? ""));
</script>

<template>
  <div class="space-y-1 text-sm leading-relaxed text-ink/85" v-html="html" />
</template>
