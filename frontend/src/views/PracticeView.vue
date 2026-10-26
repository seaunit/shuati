<script setup lang="ts">
import { computed, onMounted, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { ChevronLeft, ChevronRight, Loader2, Sparkles } from "lucide-vue-next";
import { api } from "@/api/client";
import MarkdownText from "@/components/MarkdownText.vue";
import { useBillingStore } from "@/stores/billing";

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
  // 解析库内容：题目已有解析时直接带出来，无需再点 AI 解析
  explanation: string | null;
}

const route = useRoute();
const router = useRouter();
const billing = useBillingStore();

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
const explanationError = ref("");
// 是否是题库解析库里已有的解析（点「重新生成」后变为本次生成）
const explanationFromLibrary = ref(false);
// 错题重练模式：题目来自错题本带过来的一组 id
const wrongIds = ref<number[]>([]);
const wrongRedo = computed(() => wrongIds.value.length > 0);
// 本次练习的范围（与建会话、进度接口用的是同一套 scope）
const activeUnitId = ref<number | null>(null);
const activeBankId = ref<number | null>(null);
const scopeType = computed(() =>
  wrongRedo.value ? "ALL" : activeUnitId.value ? "UNIT" : activeBankId.value ? "BANK" : "ALL",
);
const scopeId = computed(() => activeUnitId.value ?? activeBankId.value ?? 0);
const counts = ref({ answered: 0, correct: 0, partial: 0, wrong: 0 });
const sessionId = ref<number | null>(null);
const startedAt = ref(Date.now());
const resumed = ref(false);
/**
 * 「已完成」的口径与题库卡片一致：**这个范围里已提交作答过的题目**（去重、不看顺序）。
 *
 * 刷题页还会显示「第 x / y 题」，那是**当前光标位置** —— 跳过几道之后位置会跑在已完成数
 * 前面，两者本来就不是一回事，所以分开写清楚，别只给一个数让人以为前后矛盾。
 */
const scopeAnsweredIds = ref<number[]>([]);
const scopeTotal = ref(0);
const doneCount = computed(() => scopeAnsweredIds.value.length);
const scopeTotalCount = computed(() => scopeTotal.value || questions.value.length);

const current = computed(() => questions.value[index.value]);
// 只有选择题才回显「你的答案」：简答的长文本再贴一遍太啰嗦
const answeredChoice = computed(
  () => !!result.value && current.value?.type !== "SHORT" && selected.value.length > 0,
);
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

/**
 * 作答后的四种选项状态：
 * correct = 正确答案；wrong = 自己选了但答错；chosen = 尚未提交时的选中；idle = 其余。
 * 答对的选项会落在 correct，不会出现「又绿又红」的矛盾配色。
 */
type OptionState = "correct" | "wrong" | "chosen" | "idle";

const OPTION_ROW_CLASS: Record<OptionState, string> = {
  correct: "border-moss bg-moss/10",
  wrong: "border-rose bg-rose/10",
  chosen: "border-moss bg-moss/10",
  idle: "border-mist bg-paper hover:border-moss/60",
};

const OPTION_BADGE_CLASS: Record<OptionState, string> = {
  correct: "bg-moss text-white",
  wrong: "bg-rose text-white",
  chosen: "bg-moss/15 text-moss",
  idle: "bg-mist text-ink/70",
};

// 颜色之外再给读屏用户一段文字，避免只靠颜色传达对错
const OPTION_STATE_LABEL: Record<OptionState, string> = {
  correct: "（正确答案）",
  wrong: "（你的选择，答错）",
  chosen: "",
  idle: "",
};

function optionState(key: string): OptionState {
  if (!result.value) {
    return selected.value.includes(key) ? "chosen" : "idle";
  }
  if (result.value.correctAnswer?.toUpperCase().includes(key)) {
    return "correct";
  }
  return selected.value.includes(key) ? "wrong" : "idle";
}

// 「你的答案」那行单独配色：答对时不能还标红
function yourAnswerClass() {
  if (result.value?.verdict === "WRONG") return "text-rose";
  if (result.value?.verdict === "CORRECT") return "text-moss";
  return "text-ink/80";
}

function resetState() {
  selected.value = [];
  textAnswer.value = "";
  result.value = null;
  explanationError.value = "";
  explanation.value = current.value?.explanation ?? "";
  explanationFromLibrary.value = !!current.value?.explanation;
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
    // 这道题这次提交后就算「已完成」（按题目去重），与题库卡片口径一致
    if (!scopeAnsweredIds.value.includes(current.value.id)) {
      scopeAnsweredIds.value = [...scopeAnsweredIds.value, current.value.id];
    }
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
  explanationError.value = "";
  try {
    const response = await api<{ explanation: string }>(
      `/api/practice/questions/${current.value.id}/explanation`,
    );
    // 同步更新当前题目对象，切题再返回时 resetState 才能带出刚生成的解析。
    current.value.explanation = response.explanation;
    explanation.value = response.explanation;
    explanationFromLibrary.value = false;
    await billing.load();
  } catch (e) {
    // 重新生成失败时保留解析库里原有的解析，只提示错误
    explanationError.value = e instanceof Error ? e.message : "解析失败";
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

/**
 * 从头开始这一轮：不管已经刷到第几题、进度是多少，都回到第 1 题重来。
 *
 * 只重置"本轮的位置与计数"——每道题的作答记录在提交时就已经写库，不受影响。
 */
function restart() {
  index.value = 0;
  counts.value = { answered: 0, correct: 0, partial: 0, wrong: 0 };
  resumed.value = false;
  resetState();
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
  // 一轮做完清掉光标：下次进入回到「全部答完就从头开始」的老规则
  await clearCursor();
  // 错题重练做完回错题本，方便看看还剩几道没拿下
  router.push(wrongRedo.value ? "/app/wrong-book" : "/app");
}

/**
 * 落位规则（与安卓端同一套，见 specs/004-practice-cursor）：
 * 1) 先看「光标」——上次停在当前哪道题就落在哪道题，**不管前面有没有刷过**；
 * 2) 没有光标（或光标题已不在范围内）才回退到老的「第一道没答过的题」。
 *
 * 老规则保留下来是为了不丢历史进度：从没记过位置的账号行为与以前完全一致。
 */
async function resumeProgress(unitId: number | null, bankId: number | null) {
  if (wrongRedo.value) return; // 错题重练是一次性题集，不参与光标与范围进度

  // 1) 范围进度（"已完成"）：题库卡片用的是同一个接口，拿它的 answeredIds 当口径，
  //    这样刷题页与题库卡片不会出现两个数的说法
  let answeredIds: number[] = [];
  try {
    const url = unitId
      ? `/api/progress/unit/${unitId}`
      : bankId
        ? `/api/progress/bank/${bankId}`
        : "/api/progress/all";
    const data = await api<{ answeredIds?: number[] }>(url);
    answeredIds = data.answeredIds ?? [];
    scopeAnsweredIds.value = answeredIds;
  } catch {
    // 进度读取失败不影响练习
  }

  // 2) 光标优先（"当前停在哪道题"）。读不到就往下走老规则，两段各自容错
  let cursorQuestionId: number | null = null;
  try {
    const cursor = await api<{ questionId: number | null }>(
      `/api/progress/cursor?scopeType=${scopeType.value}&scopeId=${scopeId.value}`,
    );
    cursorQuestionId = cursor?.questionId ?? null;
  } catch {
    // 光标读不到（未登录/网络问题）时按老规则走
  }
  const cursorIndex = cursorQuestionId
    ? questions.value.findIndex((item) => item.id === cursorQuestionId)
    : -1;
  if (cursorIndex >= 0) {
    index.value = cursorIndex;
    resumed.value = cursorIndex > 0;
    resetState();
    return;
  }

  // 3) 没有光标（或光标题已不在范围内）→ 老的「第一道没答过的题」
  try {
    const answered = new Set(answeredIds);
    const nextIndex = questions.value.findIndex((item) => !answered.has(item.id));
    if (nextIndex > 0) {
      index.value = nextIndex;
      resetState();
      resumed.value = true;
    }
  } catch {
    // 进度读取失败不影响正常练习，退化为从第一题开始
  }
}

/** 把「当前停在哪道题」记到服务端（切一题写一次；失败不影响做题） */
async function saveCursor() {
  if (wrongRedo.value || !current.value) return;
  await api("/api/progress/cursor", {
    method: "PUT",
    body: JSON.stringify({
      scopeType: scopeType.value,
      scopeId: scopeId.value,
      questionId: current.value.id,
    }),
  }).catch(() => null);
}

/** 一轮做完：清掉光标，下次进入回到第 1 题重新开始 */
async function clearCursor() {
  if (wrongRedo.value) return;
  await api("/api/progress/cursor", {
    method: "PUT",
    body: JSON.stringify({
      scopeType: scopeType.value,
      scopeId: scopeId.value,
      questionId: null,
    }),
  }).catch(() => null);
}

// 任何一次切题（下一题 / 上一题 / 从头开始 / 落位）都把位置记下来
watch(index, () => {
  void saveCursor();
});

onMounted(async () => {
  const bankId = route.query.bankId ? Number(route.query.bankId) : null;
  const unitId = route.query.unitId ? Number(route.query.unitId) : null;
  activeBankId.value = bankId;
  activeUnitId.value = unitId;
  // 错题重练：错题本把当前筛出来的题目 id 带过来，顺序就是错题本里的顺序（最近错的排前面）
  wrongIds.value = (route.query.ids ? String(route.query.ids) : "")
    .split(",")
    .map((item) => Number(item))
    .filter((id) => Number.isFinite(id) && id > 0);
  try {
    if (wrongIds.value.length > 0) {
      // 复用已有的全部题目接口，按错题本给的顺序取，不新增接口
      const all = await api<Question[]>("/api/questions?mode=seq");
      const byId = new Map(all.map((item) => [item.id, item]));
      questions.value = wrongIds.value
        .map((id) => byId.get(id))
        .filter((item): item is Question => Boolean(item));
    } else if (unitId) {
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
          // 错题重练服务端没有这个 scope 类型，按"全部题目"记会话即可（服务端会归一化）
          scopeType: wrongIds.value.length > 0 ? "ALL" : unitId ? "UNIT" : bankId ? "BANK" : "ALL",
          scopeId: wrongIds.value.length > 0 ? null : (unitId ?? bankId),
          scopeName: wrongIds.value.length > 0
            ? "错题重练"
            : unitId
              ? "单元练习"
              : bankId
                ? "题库练习"
                : "全部题目",
          totalQuestions: questions.value.length,
        }),
      });
      sessionId.value = session.id;
      // 错题重练：这些题本来都答过，续做会把它们全跳过，所以固定从第一题开始
      if (wrongIds.value.length === 0) {
        await resumeProgress(unitId, bankId);
      }
      // 首页进入时把当前题的解析库内容同步到界面上
      resetState();
    }
  } catch {
    questions.value = [];
  } finally {
    loading.value = false;
  }
});
</script>

<template>
  <div class="w-full pb-28 lg:pb-0">
    <div v-if="loading" class="flex h-64 items-center justify-center text-sm text-oat">加载中…</div>
    <div v-else-if="questions.length === 0" class="flex h-64 items-center justify-center text-sm text-oat">
      没有可练习的题目
    </div>
    <template v-else-if="current">
      <header class="mb-4 flex flex-wrap items-start justify-between gap-x-4 gap-y-2">
        <!-- 手机上让这行独占一行，按钮换到下一行，避免"第 x/y 题"被挤到折行 -->
        <div class="min-w-0 basis-full sm:basis-auto sm:flex-1">
          <p class="text-xs text-oat">
            第 {{ index + 1 }} / {{ questions.length }} 题 · {{ typeLabel(current.type) }}
            <span class="ml-2">·</span>
            <span class="ml-1" data-testid="practice-done">
              已完成 {{ doneCount }} / {{ scopeTotalCount }} 题
            </span>
          </p>
          <p v-if="resumed" class="mt-1 text-xs text-moss">已从上次进度继续</p>
          <div class="mt-2 h-1.5 w-full max-w-64 overflow-hidden rounded-full bg-mist">
            <div class="h-full rounded-full bg-moss" :style="{ width: progress + '%' }" />
          </div>
        </div>
        <div class="ml-auto flex shrink-0 items-center gap-4">
          <button class="text-sm text-oat transition hover:text-ink" @click="restart">从头开始</button>
          <button class="text-sm text-oat transition hover:text-ink" @click="finish">结束练习</button>
        </div>
      </header>

      <article class="rounded-2xl border border-mist bg-white/70 p-4 sm:p-6">
        <div class="mb-3 flex items-center gap-2">
          <span
            data-testid="practice-type"
            class="rounded-full bg-moss/12 px-2.5 py-1 text-xs font-medium text-moss"
          >
            {{ typeLabel(current.type) }}
          </span>
          <span v-if="current.type === 'MULTI'" class="text-xs text-oat">可多选</span>
        </div>
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
            :class="OPTION_ROW_CLASS[optionState(option.key)]"
            :disabled="!!result"
            @click="toggleOption(option.key)"
          >
            <span
              class="flex h-7 w-7 shrink-0 items-center justify-center rounded-lg text-sm font-medium transition"
              :class="OPTION_BADGE_CLASS[optionState(option.key)]"
            >
              {{ option.key }}
            </span>
            <span class="pt-0.5 text-ink/85">
              {{ option.text }}
              <span class="sr-only">{{ OPTION_STATE_LABEL[optionState(option.key)] }}</span>
            </span>
          </button>
        </div>

        <div v-if="result" class="mt-5 space-y-3 rounded-xl bg-mist/60 p-4">
          <p class="text-sm font-medium text-ink">
            判定：{{ verdictLabel(result.verdict) }}
            <span v-if="result.score !== null">（{{ result.score }} 分）</span>
          </p>
          <div class="flex flex-wrap gap-x-6 gap-y-1 text-sm text-ink/80">
            <p v-if="result.correctAnswer">
              正确答案：<span class="font-medium text-moss">{{ result.correctAnswer }}</span>
            </p>
            <p v-if="answeredChoice">
              你的答案：<span class="font-medium" :class="yourAnswerClass()">{{ selected.join("") }}</span>
            </p>
          </div>
          <div v-if="result.referenceAnswer" data-testid="reference-answer" class="text-sm text-ink/80">
            <p class="font-medium text-ink">参考答案</p>
            <MarkdownText :text="String(result.referenceAnswer)" />
          </div>
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

        <div
          v-if="result && (explanation || explanationError)"
          class="mt-4 rounded-xl border border-mist bg-white p-4"
        >
          <p v-if="explanation" class="mb-2 text-xs text-oat">
            {{ explanationFromLibrary ? "来自解析库" : "本次 AI 解析" }}
          </p>
          <MarkdownText v-if="explanation" :text="explanation" />
          <p v-if="explanationError" class="text-sm text-rose">{{ explanationError }}</p>
        </div>
      </article>

      <div
        data-testid="practice-actions"
        class="fixed inset-x-4 bottom-[5.5rem] z-30 mt-4 flex items-center justify-between gap-2 rounded-2xl border border-mist bg-paper/95 p-2 shadow-sm backdrop-blur sm:inset-x-5 lg:static lg:border-0 lg:bg-transparent lg:p-0 lg:shadow-none"
      >
        <button
          class="flex shrink-0 items-center gap-1 rounded-xl border border-mist bg-white/80 px-3 py-2.5 text-sm text-ink transition hover:border-moss disabled:opacity-40 sm:px-4"
          :disabled="index === 0"
          @click="index -= 1; resetState()"
        >
          <ChevronLeft class="h-4 w-4" /> 上一题
        </button>

        <div class="flex min-w-0 items-center justify-end gap-2">
          <button
            v-if="result"
            class="flex shrink-0 items-center gap-1 rounded-xl border border-moss px-3 py-2.5 text-sm text-moss transition hover:bg-moss/10 disabled:opacity-50 sm:px-4"
            :disabled="explanationLoading"
            @click="loadExplanation"
          >
            <Loader2 v-if="explanationLoading" class="h-4 w-4 animate-spin" />
            <Sparkles v-else class="h-4 w-4" />
            {{ explanation ? "重新生成解析" : "AI 解析" }}
          </button>
          <button
            v-if="!result"
            class="flex shrink-0 items-center gap-1 rounded-xl bg-ink px-4 py-2.5 text-sm text-white transition hover:bg-ink/90 disabled:opacity-50 sm:px-5"
            :disabled="submitting"
            @click="submit"
          >
            <Loader2 v-if="submitting" class="h-4 w-4 animate-spin" />
            提交答案
          </button>
          <!--
            下一题不依赖"是否已提交"：没作答也能往下走，相当于跳过（0 分）。
            跳过不发提交请求，所以不会生成作答记录，也不会进错题本；进度照记。
          -->
          <button
            class="flex shrink-0 items-center gap-1 rounded-xl px-3 py-2.5 text-sm transition sm:px-4"
            :class="
              result
                ? 'bg-ink text-white hover:bg-ink/90'
                : 'border border-mist bg-white/80 text-ink hover:border-moss'
            "
            data-testid="skip-next"
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
