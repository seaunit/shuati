<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { ChevronLeft, ChevronRight, Loader2, Sparkles } from "lucide-vue-next";
import { api } from "@/api/client";
import MarkdownText from "@/components/MarkdownText.vue";

interface Option {
  key: string;
  text: string;
}

interface Question {
  id: number;
  unit_id: number;
  type: "SINGLE" | "MULTI" | "SHORT";
  content: string;
  options: Option[] | null;
  difficulty: string;
  images: string[] | null;
  tags: string[] | null;
  status: string;
}

const route = useRoute();
const router = useRouter();

const questions = ref<Question[]>([]);
const index = ref(0);
const loading = ref(true);
const selected = ref<string[]>([]);
const textAnswer = ref("");
const submitting = ref(false);
const result = ref<{
  recordId: number | null;
  verdict: string;
  score: number | null;
  correctAnswer?: string;
  referenceAnswer?: string;
  feedback?: Record<string, unknown> | null;
  degraded?: boolean;
} | null>(null);
const explanation = ref("");
const explanationLoading = ref(false);
const counts = ref({ answered: 0, correct: 0, partial: 0, wrong: 0 });
const sessionId = ref<number | null>(null);
const startedAt = ref(Date.now());

const current = computed(() => questions.value[index.value]);
const progress = computed(() =>
  questions.value.length ? Math.round(((index.value + (result.value ? 1 : 0)) / questions.value.length) * 100) : 0,
);

function typeLabel(type: string) {
  if (type === "SINGLE") return "单选题";
  if (type === "MULTI") return "多选题";
  if (type === "SHORT") return "简答题";
  return type;
}

function verdictLabel(verdict: string) {
  if (verdict === "CORRECT") return "正确";
  if (verdict === "PARTIAL") return "部分正确";
  if (verdict === "WRONG") return "错误";
  return "待复核";
}

function toggleOption(key: string) {
  if (result.value || !current.value) return;
  if (current.value.type === "SINGLE") {
    selected.value = [key];
    return;
  }
  selected.value = selected.value.includes(key)
    ? selected.value.filter((item) => item !== key)
    : [...selected.value, key];
}

function resetState() {
  selected.value = [];
  textAnswer.value = "";
  result.value = null;
  explanation.value = "";
  startedAt.value = Date.now();
}

async function submit() {
  if (!current.value || submitting.value) return;
  submitting.value = true;
  try {
    if (current.value.type === "SHORT") {
      if (!textAnswer.value.trim()) return;
      const response = await api<{
        recordId: number;
        verdict: string;
        score: number | null;
        feedback: Record<string, unknown> | null;
        referenceAnswer: string;
        degraded: boolean;
      }>("/api/practice/submit-essay", {
        method: "POST",
        body: JSON.stringify({
          questionId: current.value.id,
          answer: textAnswer.value.trim(),
          durationMs: Date.now() - startedAt.value,
        }),
      });
      result.value = { ...response };
    } else {
      if (selected.value.length === 0) return;
      const response = await api<{
        recordId: number;
        verdict: string;
        score: number;
        correctAnswer: string;
      }>("/api/practice/submit-choice", {
        method: "POST",
        body: JSON.stringify({
          questionId: current.value.id,
          selected: selected.value,
          durationMs: Date.now() - startedAt.value,
        }),
      });
      result.value = { ...response };
    }
    counts.value.answered += 1;
    if (result.value.verdict === "CORRECT") counts.value.correct += 1;
    else if (result.value.verdict === "PARTIAL") counts.value.partial += 1;
    else if (result.value.verdict === "WRONG") counts.value.wrong += 1;
  } catch (e) {
    alert(e instanceof Error ? e.message : "提交失败");
  } finally {
    submitting.value = false;
  }
}

async function loadExplanation() {
  if (!current.value || !result.value) return;
  explanationLoading.value = true;
  try {
    const response = await api<{ explanation: string }>(
      `/api/practice/questions/${current.value.id}/explanation?selected=${encodeURIComponent(
        selected.value.join(""),
      )}&correct=${result.value.verdict === "CORRECT"}`,
    );
    explanation.value = response.explanation;
  } catch (e) {
    explanation.value = e instanceof Error ? e.message : "解析失败";
  } finally {
    explanationLoading.value = false;
  }
}

async function selfEval(verdict: "CORRECT" | "PARTIAL" | "WRONG") {
  if (!result.value?.recordId) return;
  await api(`/api/practice/records/${result.value.recordId}/self-eval`, {
    method: "POST",
    body: JSON.stringify({ verdict }),
  });
  result.value = { ...result.value, verdict };
}

async function next() {
  if (index.value < questions.value.length - 1) {
    index.value += 1;
    resetState();
  } else {
    await finish();
  }
}

async function finish() {
  if (sessionId.value) {
    const answered = counts.value.answered;
    const score = answered ? Math.round((counts.value.correct / answered) * 100) : null;
    await api(`/api/practice/sessions/${sessionId.value}`, {
      method: "PATCH",
      body: JSON.stringify({
        status: "COMPLETED",
        answered,
        correct: counts.value.correct,
        partial: counts.value.partial,
        wrong: counts.value.wrong,
        score,
      }),
    }).catch(() => null);
  }
  router.push("/app");
}

onMounted(async () => {
  const bankId = route.query.bankId ? Number(route.query.bankId) : null;
  const unitId = route.query.unitId ? Number(route.query.unitId) : null;
  try {
    if (unitId) {
      questions.value = await api<Question[]>(`/api/units/${unitId}/questions?mode=seq`);
    } else if (bankId) {
      questions.value = await api<Question[]>(`/api/banks/${bankId}/questions?mode=seq`);
    } else {
      questions.value = await api<Question[]>("/api/questions?mode=seq");
    }
    if (questions.value.length > 0) {
      const session = await api<{ id: number }>("/api/practice/sessions", {
        method: "POST",
        body: JSON.stringify({
          scopeType: unitId ? "UNIT" : bankId ? "BANK" : "ALL",
          scopeId: unitId ?? bankId,
          scopeName: unitId ? "单元练习" : bankId ? "题库练习" : "全部题目",
          totalQuestions: questions.value.length,
        }),
      });
      sessionId.value = session.id;
    }
  } catch {
    questions.value = [];
  } finally {
    loading.value = false;
  }
});
</script>

<template>
  <div class="mx-auto max-w-3xl">
    <div v-if="loading" class="flex h-64 items-center justify-center text-sm text-oat">加载中…</div>
    <div v-else-if="questions.length === 0" class="flex h-64 items-center justify-center text-sm text-oat">
      没有可练习的题目
    </div>
    <template v-else-if="current">
      <header class="mb-4 flex items-center justify-between">
        <div>
          <p class="text-xs text-oat">
            第 {{ index + 1 }} / {{ questions.length }} 题 · {{ typeLabel(current.type) }}
          </p>
          <div class="mt-2 h-1.5 w-64 overflow-hidden rounded-full bg-mist">
            <div class="h-full rounded-full bg-moss" :style="{ width: progress + '%' }" />
          </div>
        </div>
        <button class="text-sm text-oat transition hover:text-ink" @click="finish">结束练习</button>
      </header>

      <article class="rounded-2xl border border-mist bg-white/70 p-6">
        <MarkdownText :text="current.content" />
        <div v-if="current.images?.length" class="mt-4 space-y-3">
          <img
            v-for="(image, i) in current.images"
            :key="i"
            :src="image"
            class="max-w-full rounded-xl border border-mist"
            alt="题目图片"
          />
        </div>

        <div v-if="current.type === 'SHORT'" class="mt-5">
          <textarea
            v-model="textAnswer"
            :disabled="!!result"
            rows="6"
            class="w-full rounded-xl border border-mist bg-paper px-4 py-3 text-sm outline-none transition focus:border-moss disabled:opacity-80"
            placeholder="写下你的答案、解题思路或伪代码…"
          />
        </div>
        <div v-else class="mt-5 space-y-2">
          <button
            v-for="option in current.options ?? []"
            :key="option.key"
            class="flex w-full items-start gap-3 rounded-xl border px-4 py-3 text-left text-sm transition"
            :class="[
              selected.includes(option.key) ? 'border-moss bg-moss/10' : 'border-mist bg-paper hover:border-moss/60',
              result && result.correctAnswer?.includes(option.key) ? 'border-moss bg-moss/15' : '',
            ]"
            :disabled="!!result"
            @click="toggleOption(option.key)"
          >
            <span class="font-medium text-ink">{{ option.key }}</span>
            <span class="text-ink/85">{{ option.text }}</span>
          </button>
        </div>

        <div v-if="result" class="mt-5 space-y-3 rounded-xl bg-mist/60 p-4">
          <p class="text-sm font-medium text-ink">
            判定：{{ verdictLabel(result.verdict) }}
            <span v-if="result.score !== null">（{{ result.score }} 分）</span>
          </p>
          <p v-if="result.correctAnswer" class="text-sm text-ink/80">正确答案：{{ result.correctAnswer }}</p>
          <p v-if="result.referenceAnswer" class="text-sm text-ink/80">参考答案：{{ result.referenceAnswer }}</p>
          <div v-if="result.feedback" class="text-sm text-ink/80">
            <MarkdownText :text="String(result.feedback.feedback ?? '')" />
            <ul v-if="Array.isArray(result.feedback.hit_points) && result.feedback.hit_points.length" class="mt-2 list-disc pl-5 text-xs text-moss">
              <li v-for="(point, i) in result.feedback.hit_points as string[]" :key="i">命中：{{ point }}</li>
            </ul>
            <ul v-if="Array.isArray(result.feedback.missed_points) && result.feedback.missed_points.length" class="mt-2 list-disc pl-5 text-xs text-rose">
              <li v-for="(point, i) in result.feedback.missed_points as string[]" :key="i">遗漏：{{ point }}</li>
            </ul>
          </div>
          <div v-if="result.degraded" class="flex gap-2">
            <button class="rounded-lg border border-mist bg-white px-3 py-1.5 text-xs text-ink" @click="selfEval('CORRECT')">自评：正确</button>
            <button class="rounded-lg border border-mist bg-white px-3 py-1.5 text-xs text-ink" @click="selfEval('PARTIAL')">自评：部分</button>
            <button class="rounded-lg border border-mist bg-white px-3 py-1.5 text-xs text-ink" @click="selfEval('WRONG')">自评：错误</button>
          </div>
        </div>

        <div v-if="explanation" class="mt-4 rounded-xl border border-mist bg-white p-4">
          <MarkdownText :text="explanation" />
        </div>
      </article>

      <div class="mt-4 flex items-center justify-between">
        <button
          class="flex items-center gap-1 rounded-xl border border-mist bg-white/70 px-4 py-2 text-sm text-ink transition hover:border-moss disabled:opacity-40"
          :disabled="index === 0"
          @click="index -= 1; resetState()"
        >
          <ChevronLeft class="h-4 w-4" /> 上一题
        </button>

        <div class="flex items-center gap-2">
          <button
            v-if="result"
            class="flex items-center gap-1 rounded-xl border border-moss px-4 py-2 text-sm text-moss transition hover:bg-moss/10"
            :disabled="explanationLoading || !!explanation"
            @click="loadExplanation"
          >
            <Loader2 v-if="explanationLoading" class="h-4 w-4 animate-spin" />
            <Sparkles v-else class="h-4 w-4" />
            AI 解析
          </button>
          <button
            v-if="!result"
            class="flex items-center gap-1 rounded-xl bg-ink px-5 py-2 text-sm text-white transition hover:bg-ink/90 disabled:opacity-50"
            :disabled="submitting"
            @click="submit"
          >
            <Loader2 v-if="submitting" class="h-4 w-4 animate-spin" />
            提交答案
          </button>
          <button
            v-else
            class="flex items-center gap-1 rounded-xl bg-ink px-5 py-2 text-sm text-white transition hover:bg-ink/90"
            @click="next"
          >
            {{ index < questions.length - 1 ? "下一题" : "完成练习" }}
            <ChevronRight class="h-4 w-4" />
          </button>
        </div>
      </div>
    </template>
  </div>
</template>
