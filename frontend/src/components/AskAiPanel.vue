<script setup lang="ts">
import { computed, nextTick, ref } from "vue";
import { Loader2, MessageCircle, Send } from "lucide-vue-next";
import { api } from "@/api/client";
import MarkdownText from "@/components/MarkdownText.vue";
import { useBillingStore } from "@/stores/billing";

interface ChatMessage {
  role: "user" | "assistant";
  content: string;
}

interface AskAiResponse {
  answer: string;
  cost: number;
  available: number;
}

const props = defineProps<{
  questionId: number;
}>();

const billing = useBillingStore();
const open = ref(false);
const input = ref("");
const error = ref("");
const sending = ref(false);
const pendingQuestion = ref("");
const requestId = ref("");
const messages = ref<ChatMessage[]>([]);
const scroller = ref<HTMLElement | null>(null);

const cost = computed(() => {
  const rule = billing.rules.find((item) => item.purpose === "ASK");
  return typeof rule?.points === "number" ? rule.points : 1;
});

const costLabel = computed(() => (cost.value > 0 ? `每次追问 ${cost.value} 点` : "本次免费"));

function newRequestId() {
  if (typeof crypto !== "undefined" && typeof crypto.randomUUID === "function") {
    return crypto.randomUUID();
  }
  return `${Date.now()}-${Math.random().toString(16).slice(2)}`;
}

async function scrollToLatest() {
  await nextTick();
  if (scroller.value) {
    scroller.value.scrollTop = scroller.value.scrollHeight;
  }
}

async function send() {
  const question = input.value.trim();
  if (!question || sending.value) return;
  error.value = "";
  sending.value = true;
  pendingQuestion.value = question;
  if (!requestId.value) {
    requestId.value = newRequestId();
  }
  await scrollToLatest();

  try {
    const history = messages.value.slice(-20).map((message) => ({
      role: message.role,
      content: message.content,
    }));
    const result = await api<AskAiResponse>(
      `/api/practice/questions/${props.questionId}/ask-ai`,
      {
        method: "POST",
        body: JSON.stringify({
          question,
          history,
          requestId: requestId.value,
        }),
      },
    );
    messages.value.push(
      { role: "user", content: question },
      { role: "assistant", content: result.answer },
    );
    messages.value = messages.value.slice(-20);
    input.value = "";
    requestId.value = "";
    await billing.load();
    await scrollToLatest();
  } catch (e) {
    error.value = e instanceof Error ? e.message : "问 AI 失败，请稍后重试";
    await billing.load();
  } finally {
    sending.value = false;
    pendingQuestion.value = "";
    await scrollToLatest();
  }
}

function handleKeydown(event: KeyboardEvent) {
  if (event.key === "Enter" && !event.shiftKey) {
    event.preventDefault();
    void send();
  }
}
</script>

<template>
  <section class="mt-4 rounded-xl border border-moss/25 bg-moss/[0.04]" data-testid="ask-ai-panel">
    <button
      type="button"
      class="flex w-full items-center justify-between gap-3 px-4 py-3 text-left"
      :aria-expanded="open"
      @click="open = !open"
    >
      <span class="flex items-center gap-2 text-sm font-medium text-ink">
        <MessageCircle class="h-4 w-4 text-moss" />
        问 AI
      </span>
      <span class="text-xs text-oat">{{ costLabel }}</span>
    </button>

    <div v-if="open" class="border-t border-moss/15 px-4 pb-4 pt-3">
      <div
        v-if="messages.length || pendingQuestion || error"
        ref="scroller"
        class="mb-3 max-h-80 space-y-3 overflow-y-auto pr-1"
        aria-live="polite"
      >
        <div
          v-for="(message, index) in messages"
          :key="index"
          class="flex"
          :class="message.role === 'user' ? 'justify-end' : 'justify-start'"
        >
          <div
            class="max-w-[92%] rounded-xl px-3 py-2 text-sm"
            :class="message.role === 'user'
              ? 'bg-ink text-white'
              : 'border border-mist bg-white text-ink'"
          >
            <MarkdownText v-if="message.role === 'assistant'" :text="message.content" />
            <p v-else class="whitespace-pre-wrap">{{ message.content }}</p>
          </div>
        </div>

        <div v-if="pendingQuestion && !error" class="flex justify-end">
          <div class="max-w-[92%] rounded-xl bg-ink px-3 py-2 text-sm text-white">
            {{ pendingQuestion }}
          </div>
        </div>
        <div v-if="sending" class="flex items-center gap-2 text-xs text-oat">
          <Loader2 class="h-3.5 w-3.5 animate-spin" />
          正在结合题目和解析回答…
        </div>
        <p v-if="error" class="rounded-lg border border-rose/20 bg-rose/5 px-3 py-2 text-sm text-rose">
          {{ error }}
        </p>
      </div>

      <div class="flex items-end gap-2">
        <textarea
          v-model="input"
          rows="2"
          maxlength="1000"
          :disabled="sending"
          placeholder="输入你想追问的问题…"
          class="min-h-20 flex-1 resize-none rounded-xl border border-mist bg-white px-3 py-2 text-sm outline-none transition focus:border-moss disabled:opacity-70"
          @keydown="handleKeydown"
        />
        <button
          type="button"
          class="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-ink text-white transition hover:bg-ink/85 disabled:cursor-not-allowed disabled:opacity-45"
          :disabled="sending || !input.trim()"
          aria-label="发送问题"
          @click="send"
        >
          <Loader2 v-if="sending" class="h-4 w-4 animate-spin" />
          <Send v-else class="h-4 w-4" />
        </button>
      </div>
    </div>
  </section>
</template>
