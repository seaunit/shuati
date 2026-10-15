<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from "vue";
import { useRoute } from "vue-router";
import { Check, Loader2, Pencil, Plus, Trash2, Upload, X } from "lucide-vue-next";
import { api } from "@/api/client";
import { useAuthStore } from "@/stores/auth";

interface Bank {
  id: number;
  name: string;
  description: string | null;
  is_public: boolean;
}

interface Unit {
  id: number;
  bank_id: number;
  name: string;
  sort: number;
  question_count: number;
  off_count: number;
}

interface Option {
  key: string;
  text: string;
}

interface Question {
  id: number;
  unit_id: number;
  type: string;
  content: string;
  status: string;
  difficulty?: string | null;
  options?: Option[] | null;
  answer?: string | null;
  key_points?: string[] | null;
  explanation?: string | null;
  tags?: string[] | null;
}

type QuestionType = "SINGLE" | "MULTI" | "SHORT";

const OPTION_KEYS = ["A", "B", "C", "D", "E", "F", "G", "H"];
const MAX_OPTIONS = 8;

const route = useRoute();
const bankId = Number(route.params.id);
const auth = useAuthStore();

const bank = ref<Bank | null>(null);
const bankName = ref("");
const bankDescription = ref("");
const units = ref<Unit[]>([]);
const questions = ref<Question[]>([]);
const loading = ref(true);
const busy = ref(false);
const notice = ref("");
const noticeError = ref("");

const newUnitName = ref("");
const editingUnitId = ref<number | null>(null);
const editingUnitName = ref("");
const questionFilter = ref<"ALL" | "ON" | "OFF">("ALL");

// 删题库要先过邮箱验证码，验完才执行
const deleteOpen = ref(false);
const deleteCode = ref("");
const deleteError = ref("");
const deleteSending = ref(false);
const deleteBusy = ref(false);
const deleteCooldown = ref(0);
let deleteTimer: number | null = null;

// 「新建空题库」：建完直接进它的维护页，接着就能导入或手工加题
const creatingBank = ref(false);
const newBankName = ref("");

// 手工题目表单：新增与编辑共用，编辑时记下题目 id
const formOpen = ref(false);
const editingQuestionId = ref<number | null>(null);
const formError = ref("");
const form = ref({
  unitId: null as number | null,
  type: "SINGLE" as QuestionType,
  content: "",
  options: ["", "", "", ""] as string[],
  selected: [] as string[],
  answer: "",
  keyPoints: "",
  difficulty: "MEDIUM" as "EASY" | "MEDIUM" | "HARD",
  explanation: "",
  tags: "",
});

const unitNameById = computed(() => {
  const map = new Map<number, string>();
  units.value.forEach((unit) => map.set(unit.id, unit.name));
  return map;
});

const totalQuestions = computed(() =>
  units.value.reduce((sum, unit) => sum + (unit.question_count ?? 0), 0),
);

const needsOptions = computed(() => form.value.type !== "SHORT");

function typeLabel(type: string) {
  if (type === "SINGLE") return "单选";
  if (type === "MULTI") return "多选";
  if (type === "SHORT") return "简答";
  return type;
}

function difficultyLabel(value: string | null | undefined) {
  if (value === "EASY") return "简单";
  if (value === "HARD") return "困难";
  return "中等";
}

function fail(e: unknown, fallback: string) {
  noticeError.value = e instanceof Error ? e.message : fallback;
}

async function loadBanks() {
  const banks = await api<Bank[]>("/api/manage/banks");
  bank.value = banks.find((item) => item.id === bankId) ?? null;
  if (bank.value) {
    bankName.value = bank.value.name;
    bankDescription.value = bank.value.description ?? "";
  }
}

async function loadUnits() {
  units.value = await api<Unit[]>(`/api/manage/banks/${bankId}/units`);
}

async function loadQuestions() {
  questions.value = await api<Question[]>(
    `/api/manage/banks/${bankId}/questions?status=${questionFilter.value}`,
  );
}

async function load() {
  loading.value = true;
  noticeError.value = "";
  try {
    await loadBanks();
    if (!bank.value) {
      noticeError.value = "题库不存在，或你没有维护权限";
      return;
    }
    await Promise.all([loadUnits(), loadQuestions()]);
  } catch (e) {
    fail(e, "加载失败");
  } finally {
    loading.value = false;
  }
}

async function createEmptyBank() {
  const name = newBankName.value.trim();
  if (!name) {
    noticeError.value = "请填写题库名称";
    return;
  }
  busy.value = true;
  noticeError.value = "";
  try {
    const created = await api<{ id: number }>("/api/manage/banks", {
      method: "POST",
      body: JSON.stringify({ name }),
    });
    creatingBank.value = false;
    newBankName.value = "";
    window.location.href = `/app/banks/${created.id}`;
  } catch (e) {
    fail(e, "新建题库失败");
  } finally {
    busy.value = false;
  }
}

async function saveBank() {
  if (!bankName.value.trim()) {
    noticeError.value = "题库名称不能为空";
    return;
  }
  busy.value = true;
  notice.value = "";
  noticeError.value = "";
  try {
    await api(`/api/manage/banks/${bankId}`, {
      method: "PATCH",
      body: JSON.stringify({
        name: bankName.value.trim(),
        description: bankDescription.value.trim(),
      }),
    });
    notice.value = "题库信息已保存";
    await loadBanks();
  } catch (e) {
    fail(e, "保存失败");
  } finally {
    busy.value = false;
  }
}

function stopDeleteCooldown() {
  if (deleteTimer !== null) {
    window.clearInterval(deleteTimer);
    deleteTimer = null;
  }
}

function startDeleteCooldown(seconds: number) {
  stopDeleteCooldown();
  deleteCooldown.value = Math.max(0, seconds);
  deleteTimer = window.setInterval(() => {
    deleteCooldown.value -= 1;
    if (deleteCooldown.value <= 0) {
      stopDeleteCooldown();
    }
  }, 1000);
}

function openDeleteDialog() {
  deleteOpen.value = true;
  deleteCode.value = "";
  deleteError.value = "";
  deleteCooldown.value = 0;
  stopDeleteCooldown();
  notice.value = "";
  noticeError.value = "";
}

function closeDeleteDialog() {
  deleteOpen.value = false;
  stopDeleteCooldown();
}

async function sendDeleteCode() {
  if (deleteCooldown.value > 0 || deleteSending.value) return;
  deleteSending.value = true;
  deleteError.value = "";
  try {
    const result = await api<{ cooldownSeconds: number; expiresInSeconds: number }>(
      `/api/manage/banks/${bankId}/delete-code`,
      { method: "POST" },
    );
    startDeleteCooldown(result.cooldownSeconds ?? 60);
  } catch (e) {
    deleteError.value = e instanceof Error ? e.message : "验证码发送失败";
  } finally {
    deleteSending.value = false;
  }
}

async function confirmDeleteBank() {
  if (!/^\d{6}$/.test(deleteCode.value.trim())) {
    deleteError.value = "请填写 6 位邮箱验证码";
    return;
  }
  deleteBusy.value = true;
  deleteError.value = "";
  try {
    await api(`/api/manage/banks/${bankId}/delete`, {
      method: "POST",
      body: JSON.stringify({ code: deleteCode.value.trim() }),
    });
    stopDeleteCooldown();
    window.location.href = "/app/banks";
  } catch (e) {
    deleteError.value = e instanceof Error ? e.message : "删除失败";
  } finally {
    deleteBusy.value = false;
  }
}

async function createUnit() {
  if (!newUnitName.value.trim()) {
    noticeError.value = "请填写单元名";
    return;
  }
  noticeError.value = "";
  try {
    await api("/api/manage/units", {
      method: "POST",
      body: JSON.stringify({ bankId, name: newUnitName.value.trim() }),
    });
    newUnitName.value = "";
    await loadUnits();
  } catch (e) {
    fail(e, "新建单元失败");
  }
}

function startRename(unit: Unit) {
  editingUnitId.value = unit.id;
  editingUnitName.value = unit.name;
}

async function renameUnit() {
  if (editingUnitId.value === null) return;
  const name = editingUnitName.value.trim();
  if (!name) {
    noticeError.value = "单元名不能为空";
    return;
  }
  noticeError.value = "";
  try {
    await api(`/api/manage/units/${editingUnitId.value}`, {
      method: "PATCH",
      body: JSON.stringify({ name }),
    });
    editingUnitId.value = null;
    await loadUnits();
  } catch (e) {
    fail(e, "重命名失败");
  }
}

async function deleteUnit(unit: Unit) {
  if (!window.confirm(`删除单元「${unit.name}」？`)) return;
  noticeError.value = "";
  try {
    await api(`/api/manage/units/${unit.id}`, { method: "DELETE" });
    await loadUnits();
  } catch (e) {
    fail(e, "删除单元失败");
  }
}

async function toggleQuestion(question: Question) {
  noticeError.value = "";
  try {
    await api(`/api/manage/questions/${question.id}/status`, {
      method: "PATCH",
      body: JSON.stringify({ status: question.status === "ON" ? "OFF" : "ON" }),
    });
    await loadQuestions();
  } catch (e) {
    fail(e, "修改状态失败");
  }
}

async function deleteQuestion(question: Question) {
  if (!window.confirm("删除题目后，该题的历史练习记录也会一并删除，确定？")) return;
  noticeError.value = "";
  try {
    await api(`/api/manage/questions/${question.id}`, { method: "DELETE" });
    await loadQuestions();
    await loadUnits();
  } catch (e) {
    fail(e, "删除题目失败");
  }
}

async function changeFilter(value: "ALL" | "ON" | "OFF") {
  questionFilter.value = value;
  await loadQuestions().catch((e) => fail(e, "加载题目失败"));
}

function resetForm() {
  form.value = {
    // 单元选填，默认不选 → 后端放进「未分类」兜底单元
    unitId: null,
    type: "SINGLE",
    content: "",
    options: ["", "", "", ""],
    selected: [],
    answer: "",
    keyPoints: "",
    difficulty: "MEDIUM",
    explanation: "",
    tags: "",
  };
  formError.value = "";
}

function openCreateForm() {
  editingQuestionId.value = null;
  resetForm();
  formOpen.value = true;
  noticeError.value = "";
}

function openEditForm(question: Question) {
  editingQuestionId.value = question.id;
  const type = (question.type as QuestionType) ?? "SINGLE";
  const options = (question.options ?? []).map((item) => item.text);
  resetForm();
  form.value.unitId = question.unit_id;
  form.value.type = type;
  form.value.content = question.content ?? "";
  if (type !== "SHORT") {
    // 空行补足到至少 4 个输入框，方便继续加选项
    form.value.options = [...options, "", "", "", ""].slice(0, Math.max(4, options.length));
  }
  form.value.selected = (question.answer ?? "").split("").filter(Boolean);
  form.value.answer = type === "SHORT" ? (question.answer ?? "") : "";
  form.value.keyPoints = (question.key_points ?? []).join("\n");
  form.value.difficulty = (question.difficulty as "EASY" | "MEDIUM" | "HARD") ?? "MEDIUM";
  form.value.explanation = question.explanation ?? "";
  form.value.tags = (question.tags ?? []).join(", ");
  formOpen.value = true;
  noticeError.value = "";
}

function closeForm() {
  formOpen.value = false;
  editingQuestionId.value = null;
  formError.value = "";
}

function setQuestionType(type: QuestionType) {
  form.value.type = type;
  formError.value = "";
}

function addOption() {
  if (form.value.options.length >= MAX_OPTIONS) return;
  form.value.options.push("");
}

function removeOption(index: number) {
  if (form.value.options.length <= 2) return;
  const key = OPTION_KEYS[index];
  form.value.options.splice(index, 1);
  form.value.selected = form.value.selected.filter((item) => item !== key);
}

function toggleAnswer(key: string) {
  if (form.value.type === "SINGLE") {
    form.value.selected = [key];
    return;
  }
  form.value.selected = form.value.selected.includes(key)
    ? form.value.selected.filter((item) => item !== key)
    : [...form.value.selected, key].sort();
}

function buildPayload() {
  const content = form.value.content.trim();
  if (!content) {
    formError.value = "题干不能为空";
    return null;
  }
  const payload: Record<string, unknown> = {
    bankId,
    // 不选单元就传 null，后端会落到默认单元
    unitId: form.value.unitId,
    type: form.value.type,
    content,
    difficulty: form.value.difficulty,
    explanation: form.value.explanation.trim(),
    tags: form.value.tags
      .split(/[,，]/)
      .map((item) => item.trim())
      .filter(Boolean),
  };
  if (form.value.type === "SHORT") {
    const answer = form.value.answer.trim();
    if (!answer) {
      formError.value = "简答题要填参考答案";
      return null;
    }
    payload.answer = answer;
    // 评分要点不再要求填写：新建时留空（判分时由 AI 自行提炼），
    // 编辑时保留题目原有的要点，避免把导入/后台已有的数据静默清空
    const keptKeyPoints = form.value.keyPoints
      .split("\n")
      .map((item) => item.trim())
      .filter(Boolean);
    payload.keyPoints = keptKeyPoints.length > 0 ? keptKeyPoints : null;
    payload.options = null;
  } else {
    const options = form.value.options.map((item) => item.trim());
    // 默认给了 4 个输入框，末尾没填的直接丢掉；中间留空才算填错
    while (options.length > 2 && options[options.length - 1] === "") {
      options.pop();
    }
    if (options.some((item) => !item)) {
      formError.value = "选项内容不能留空，没用的选项请删掉";
      return null;
    }
    if (options.length < 2) {
      formError.value = "选择题至少要有两个选项";
      return null;
    }
    if (form.value.selected.length === 0) {
      formError.value = "请选择正确答案";
      return null;
    }
    payload.answer = form.value.selected.join("");
    payload.options = options.map((text, index) => ({ key: OPTION_KEYS[index], text }));
    payload.keyPoints = null;
  }
  formError.value = "";
  return payload;
}

async function submitQuestion() {
  const payload = buildPayload();
  if (!payload) return;
  busy.value = true;
  notice.value = "";
  noticeError.value = "";
  try {
    if (editingQuestionId.value === null) {
      await api("/api/manage/questions", { method: "POST", body: JSON.stringify(payload) });
      notice.value = "题目已新增";
    } else {
      await api(`/api/manage/questions/${editingQuestionId.value}`, {
        method: "PUT",
        body: JSON.stringify(payload),
      });
      notice.value = "题目已保存";
    }
    closeForm();
    await Promise.all([loadQuestions(), loadUnits()]);
  } catch (e) {
    fail(e, "保存题目失败");
  } finally {
    busy.value = false;
  }
}

function onKeydown(event: KeyboardEvent) {
  if (event.key !== "Escape") return;
  if (deleteOpen.value) {
    closeDeleteDialog();
    return;
  }
  if (formOpen.value) {
    closeForm();
  }
}

// 弹窗打开时锁住页面滚动，避免背景跟着滚
watch([formOpen, deleteOpen], ([form, remove]) => {
  document.body.style.overflow = form || remove ? "hidden" : "";
});

onMounted(() => {
  load();
  window.addEventListener("keydown", onKeydown);
});

onBeforeUnmount(() => {
  window.removeEventListener("keydown", onKeydown);
  stopDeleteCooldown();
  document.body.style.overflow = "";
});
</script>

<template>
  <div class="w-full">
    <header class="mb-6 flex flex-wrap items-start justify-between gap-3">
      <div class="min-w-0">
        <h1 class="text-2xl font-semibold tracking-tight text-ink">题库管理</h1>
        <p class="mt-1 truncate text-sm text-oat">
          {{ bank?.name ?? "…" }}
          <span v-if="bank?.is_public" class="ml-1 rounded-md bg-mist px-1.5 py-0.5 text-xs">
            公共题库
          </span>
        </p>
      </div>
      <RouterLink to="/app" class="shrink-0 text-sm text-moss transition hover:text-moss/80">
        返回题库
      </RouterLink>
    </header>

    <div v-if="loading" class="flex h-40 items-center justify-center text-sm text-oat">加载中…</div>

    <template v-else-if="bank">
      <p v-if="notice" class="mb-4 rounded-xl bg-moss/10 px-4 py-2.5 text-sm text-ink">{{ notice }}</p>
      <p v-if="noticeError" class="mb-4 rounded-xl bg-rose/10 px-4 py-2.5 text-sm text-rose">
        {{ noticeError }}
      </p>

      <section class="rounded-2xl border border-mist bg-white/70 p-4 sm:p-5">
        <h2 class="mb-3 font-medium text-ink">题库信息</h2>
        <div class="grid gap-3 sm:grid-cols-2">
          <label class="block">
            <span class="mb-1.5 block text-sm text-ink">名称</span>
            <input
              v-model="bankName"
              class="w-full rounded-xl border border-mist bg-paper px-4 py-2.5 text-sm outline-none focus:border-moss"
            />
          </label>
          <label class="block">
            <span class="mb-1.5 block text-sm text-ink">描述（可选）</span>
            <input
              v-model="bankDescription"
              class="w-full rounded-xl border border-mist bg-paper px-4 py-2.5 text-sm outline-none focus:border-moss"
            />
          </label>
        </div>
        <div class="mt-4 flex flex-wrap gap-2">
          <button
            class="flex items-center gap-1.5 rounded-xl bg-ink px-4 py-2 text-sm text-white transition hover:bg-ink/90 disabled:opacity-50"
            :disabled="busy"
            @click="saveBank"
          >
            <Loader2 v-if="busy" class="h-4 w-4 animate-spin" />
            <Check v-else class="h-4 w-4" />
            保存
          </button>
          <button
            class="flex items-center gap-1.5 rounded-xl border border-rose/40 px-4 py-2 text-sm text-rose transition hover:bg-rose/10"
            @click="openDeleteDialog"
          >
            <Trash2 class="h-4 w-4" />
            删除题库
          </button>
        </div>

        <div class="mt-5 border-t border-mist pt-4">
          <h3 class="text-sm font-medium text-ink">新建空题库</h3>
          <p class="mt-1 text-xs text-oat">
            建好之后就能往里面导入题目，或者手工新增题目。
          </p>
          <div class="mt-3 flex flex-wrap gap-2">
            <template v-if="creatingBank">
              <input
                v-model="newBankName"
                placeholder="新题库名称"
                class="min-w-0 flex-1 rounded-xl border border-mist bg-paper px-4 py-2 text-sm outline-none focus:border-moss"
                @keyup.enter="createEmptyBank"
              />
              <button
                class="shrink-0 rounded-xl bg-ink px-4 py-2 text-sm text-white transition hover:bg-ink/90 disabled:opacity-50"
                :disabled="busy"
                @click="createEmptyBank"
              >
                创建
              </button>
              <button
                class="shrink-0 rounded-xl border border-mist px-4 py-2 text-sm text-ink transition hover:border-moss"
                @click="creatingBank = false; newBankName = '';"
              >
                取消
              </button>
            </template>
            <button
              v-else
              class="flex items-center gap-1.5 rounded-xl border border-moss px-4 py-2 text-sm text-moss transition hover:bg-moss/10"
              @click="creatingBank = true"
            >
              <Plus class="h-4 w-4" />
              新建空题库
            </button>
          </div>
        </div>
      </section>

      <section class="mt-6 rounded-2xl border border-mist bg-white/70 p-4 sm:p-5">
        <div class="mb-3 flex flex-wrap items-center justify-between gap-2">
          <h2 class="font-medium text-ink">单元</h2>
          <div class="flex flex-wrap gap-2">
            <RouterLink
              :to="`/app/import?bankId=${bankId}`"
              class="flex items-center gap-1.5 rounded-xl border border-moss px-3 py-2 text-sm text-moss transition hover:bg-moss/10"
            >
              <Upload class="h-4 w-4" />
              导入题目
            </RouterLink>
            <button
              class="flex items-center gap-1.5 rounded-xl bg-ink px-3 py-2 text-sm text-white transition hover:bg-ink/90"
              @click="openCreateForm"
            >
              <Plus class="h-4 w-4" />
              新增题目
            </button>
          </div>
        </div>
        <div class="flex flex-wrap gap-2">
          <input
            v-model="newUnitName"
            placeholder="新单元名称"
            class="min-w-0 flex-1 rounded-xl border border-mist bg-paper px-4 py-2 text-sm outline-none focus:border-moss"
            @keyup.enter="createUnit"
          />
          <button
            class="flex shrink-0 items-center gap-1.5 rounded-xl border border-moss px-4 py-2 text-sm text-moss transition hover:bg-moss/10"
            @click="createUnit"
          >
            <Plus class="h-4 w-4" />
            新建单元
          </button>
        </div>

        <div v-if="units.length === 0" class="mt-4 text-sm text-oat">还没有单元</div>
        <div v-else class="mt-4 space-y-1">
          <div
            v-for="unit in units"
            :key="unit.id"
            class="flex flex-wrap items-center gap-2 rounded-xl border border-mist bg-paper px-3 py-2"
          >
            <template v-if="editingUnitId === unit.id">
              <input
                v-model="editingUnitName"
                aria-label="单元名称"
                class="min-w-0 flex-1 rounded-lg border border-mist bg-white px-3 py-1.5 text-sm outline-none focus:border-moss"
                @keyup.enter="renameUnit"
              />
              <button
                class="flex h-8 w-8 items-center justify-center rounded-lg border border-moss text-moss"
                title="保存"
                @click="renameUnit"
              >
                <Check class="h-4 w-4" />
              </button>
              <button
                class="flex h-8 w-8 items-center justify-center rounded-lg border border-mist text-oat"
                title="取消"
                @click="editingUnitId = null"
              >
                <X class="h-4 w-4" />
              </button>
            </template>
            <template v-else>
              <span class="min-w-0 flex-1 truncate text-sm text-ink">{{ unit.name }}</span>
              <span class="shrink-0 text-xs text-oat">
                {{ unit.question_count }} 题<span v-if="unit.off_count">
                  （{{ unit.off_count }} 已下架）</span
                >
              </span>
              <button
                class="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg border border-mist text-ink transition hover:border-moss"
                title="重命名"
                @click="startRename(unit)"
              >
                <Pencil class="h-3.5 w-3.5" />
              </button>
              <button
                class="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg border border-mist text-rose transition hover:border-rose"
                title="删除单元"
                @click="deleteUnit(unit)"
              >
                <Trash2 class="h-3.5 w-3.5" />
              </button>
            </template>
          </div>
        </div>
      </section>

      <!-- 题目表单走弹窗：遮罩可点，Esc 也能关 -->
      <button
        v-if="formOpen"
        class="fixed inset-0 z-[45] bg-ink/25 backdrop-blur-[2px]"
        aria-label="关闭题目表单"
        @click="closeForm"
      />
      <section
        v-if="formOpen"
        role="dialog"
        aria-modal="true"
        :aria-label="editingQuestionId === null ? '新增题目' : '编辑题目'"
        class="fixed inset-x-0 top-0 z-50 mx-auto my-4 max-h-[92vh] w-[calc(100%-2rem)] max-w-2xl overflow-y-auto rounded-2xl border border-mist bg-paper p-4 shadow-2xl sm:p-5"
      >
        <div class="mb-4 flex items-center justify-between gap-3">
          <h2 class="font-medium text-ink">
            {{ editingQuestionId === null ? "新增题目" : "编辑题目" }}
          </h2>
          <button
            class="flex h-8 w-8 items-center justify-center rounded-lg border border-mist text-oat transition hover:border-rose hover:text-rose"
            aria-label="关闭弹窗"
            title="关闭"
            @click="closeForm"
          >
            <X class="h-4 w-4" />
          </button>
        </div>

        <div class="grid gap-3 sm:grid-cols-2">
          <label class="block">
            <span class="mb-1.5 block text-sm text-ink">
              所属单元<span class="text-oat">（可选，不选就放进默认单元）</span>
            </span>
            <select
              v-model="form.unitId"
              aria-label="所属单元"
              class="w-full rounded-xl border border-mist bg-paper px-4 py-2.5 text-sm outline-none focus:border-moss"
            >
              <option :value="null">默认单元（未分类）</option>
              <option v-for="unit in units" :key="unit.id" :value="unit.id">{{ unit.name }}</option>
            </select>
          </label>
          <div>
            <span class="mb-1.5 block text-sm text-ink">题型</span>
            <div class="flex flex-wrap gap-2">
              <button
                v-for="item in [
                  { key: 'SINGLE', label: '单选' },
                  { key: 'MULTI', label: '多选' },
                  { key: 'SHORT', label: '简答' },
                ]"
                :key="item.key"
                type="button"
                class="rounded-xl border px-3 py-2 text-sm transition"
                :class="
                  form.type === item.key
                    ? 'border-moss bg-moss/10 text-moss'
                    : 'border-mist bg-white/70 text-ink/75 hover:border-moss/60'
                "
                @click="setQuestionType(item.key as 'SINGLE' | 'MULTI' | 'SHORT')"
              >
                {{ item.label }}
              </button>
            </div>
          </div>
        </div>

        <label class="mt-3 block">
          <span class="mb-1.5 block text-sm text-ink">题干</span>
          <textarea
            v-model="form.content"
            rows="4"
            class="w-full rounded-xl border border-mist bg-paper px-4 py-3 text-sm outline-none focus:border-moss"
            placeholder="支持 Markdown 语法"
          />
        </label>

        <div v-if="needsOptions" class="mt-3">
          <div class="mb-2 flex items-center justify-between">
            <span class="text-sm text-ink">选项与答案</span>
            <button
              type="button"
              class="flex items-center gap-1 rounded-lg border border-mist px-2.5 py-1 text-xs text-ink transition hover:border-moss disabled:opacity-50"
              :disabled="form.options.length >= MAX_OPTIONS"
              @click="addOption"
            >
              <Plus class="h-3.5 w-3.5" />
              增加选项
            </button>
          </div>
          <div class="space-y-2">
            <div
              v-for="(_, index) in form.options"
              :key="index"
              class="flex items-center gap-2 rounded-xl border border-mist bg-paper px-3 py-2"
            >
              <button
                type="button"
                :aria-label="`选项 ${OPTION_KEYS[index]}`"
                :aria-pressed="form.selected.includes(OPTION_KEYS[index])"
                class="flex h-7 w-7 shrink-0 items-center justify-center rounded-lg border text-xs transition"
                :class="
                  form.selected.includes(OPTION_KEYS[index])
                    ? 'border-moss bg-moss/15 font-medium text-moss'
                    : 'border-mist bg-white text-oat hover:border-moss'
                "
                :title="`把 ${OPTION_KEYS[index]} 设为正确答案`"
                @click="toggleAnswer(OPTION_KEYS[index])"
              >
                {{ OPTION_KEYS[index] }}
              </button>
              <input
                v-model="form.options[index]"
                :placeholder="`选项 ${OPTION_KEYS[index]} 内容`"
                class="min-w-0 flex-1 rounded-lg border border-mist bg-white px-3 py-1.5 text-sm outline-none focus:border-moss"
              />
              <button
                type="button"
                class="flex h-7 w-7 shrink-0 items-center justify-center rounded-lg border border-mist text-rose transition hover:border-rose disabled:opacity-40"
                :disabled="form.options.length <= 2"
                title="删除该选项"
                @click="removeOption(index)"
              >
                <X class="h-3.5 w-3.5" />
              </button>
            </div>
          </div>
          <p class="mt-2 text-xs text-oat">
            点选项前面的字母设为正确答案{{ form.type === "MULTI" ? "，多选可点多个" : "" }}。当前答案：{{
              form.selected.join("") || "未选"
            }}
          </p>
        </div>

        <div v-else class="mt-3 grid gap-3">
          <label class="block">
            <span class="mb-1.5 block text-sm text-ink">参考答案</span>
            <textarea
              v-model="form.answer"
              rows="3"
              class="w-full rounded-xl border border-mist bg-paper px-4 py-3 text-sm outline-none focus:border-moss"
              placeholder="用于 AI 批改时对照的参考答案"
            />
          </label>
        </div>

        <div class="mt-3 grid gap-3 sm:grid-cols-2">
          <div>
            <span class="mb-1.5 block text-sm text-ink">难度</span>
            <div class="flex flex-wrap gap-2">
              <button
                v-for="item in [
                  { key: 'EASY', label: '简单' },
                  { key: 'MEDIUM', label: '中等' },
                  { key: 'HARD', label: '困难' },
                ]"
                :key="item.key"
                type="button"
                class="rounded-xl border px-3 py-2 text-sm transition"
                :class="
                  form.difficulty === item.key
                    ? 'border-moss bg-moss/10 text-moss'
                    : 'border-mist bg-white/70 text-ink/75 hover:border-moss/60'
                "
                @click="form.difficulty = item.key as 'EASY' | 'MEDIUM' | 'HARD'"
              >
                {{ item.label }}
              </button>
            </div>
          </div>
          <label class="block">
            <span class="mb-1.5 block text-sm text-ink">标签（可选，逗号分隔）</span>
            <input
              v-model="form.tags"
              class="w-full rounded-xl border border-mist bg-paper px-4 py-2.5 text-sm outline-none focus:border-moss"
            />
          </label>
        </div>

        <label class="mt-3 block">
          <span class="mb-1.5 block text-sm text-ink">解析（可选）</span>
          <textarea
            v-model="form.explanation"
            rows="3"
            class="w-full rounded-xl border border-mist bg-paper px-4 py-3 text-sm outline-none focus:border-moss"
          />
        </label>

        <p v-if="formError" class="mt-3 rounded-xl bg-rose/10 px-4 py-2.5 text-sm text-rose">
          {{ formError }}
        </p>

        <!-- 表单比视口高时，操作栏吸在弹窗底部，不用滚到最后才能保存 -->
        <div
          class="sticky -bottom-4 -mx-4 -mb-4 mt-4 flex flex-wrap gap-2 border-t border-mist bg-paper px-4 pb-4 pt-3 sm:-bottom-5 sm:-mx-5 sm:-mb-5 sm:px-5 sm:pb-5"
        >
          <button
            class="flex items-center gap-1.5 rounded-xl bg-ink px-4 py-2 text-sm text-white transition hover:bg-ink/90 disabled:opacity-50"
            :disabled="busy"
            @click="submitQuestion"
          >
            <Loader2 v-if="busy" class="h-4 w-4 animate-spin" />
            <Check v-else class="h-4 w-4" />
            {{ editingQuestionId === null ? "保存题目" : "保存修改" }}
          </button>
          <button
            class="rounded-xl border border-mist px-4 py-2 text-sm text-ink transition hover:border-moss"
            @click="closeForm"
          >
            取消
          </button>
        </div>
      </section>

      <section class="mt-6 rounded-2xl border border-mist bg-white/70 p-4 sm:p-5">
        <div class="mb-3 flex flex-wrap items-center justify-between gap-2">
          <h2 class="font-medium text-ink">题目</h2>
          <div class="flex gap-1">
            <button
              v-for="item in [
                { key: 'ALL', label: '全部' },
                { key: 'ON', label: '已上架' },
                { key: 'OFF', label: '已下架' },
              ]"
              :key="item.key"
              class="rounded-lg border px-2.5 py-1 text-xs transition"
              :class="
                questionFilter === item.key
                  ? 'border-moss bg-moss/10 text-moss'
                  : 'border-mist bg-white/70 text-ink'
              "
              @click="changeFilter(item.key as 'ALL' | 'ON' | 'OFF')"
            >
              {{ item.label }}
            </button>
          </div>
        </div>

        <div v-if="questions.length === 0" class="text-sm text-oat">没有符合条件的题目</div>
        <div v-else class="max-h-[32rem] space-y-1 overflow-y-auto pr-1">
          <div
            v-for="question in questions"
            :key="question.id"
            class="flex flex-wrap items-center gap-2 rounded-xl border border-mist bg-paper px-3 py-2"
          >
            <span class="shrink-0 rounded-md bg-mist px-1.5 py-0.5 text-xs text-oat">
              {{ typeLabel(question.type) }}
            </span>
            <span class="shrink-0 text-xs text-oat">{{ difficultyLabel(question.difficulty) }}</span>
            <span class="min-w-0 flex-1 basis-full truncate text-sm text-ink sm:basis-auto">
              {{ question.content }}
            </span>
            <span class="shrink-0 text-xs text-oat">
              {{ unitNameById.get(question.unit_id) ?? "—" }}
            </span>
            <span
              class="shrink-0 rounded-md px-1.5 py-0.5 text-xs"
              :class="question.status === 'ON' ? 'bg-moss/10 text-moss' : 'bg-mist text-oat'"
            >
              {{ question.status === "ON" ? "已上架" : "已下架" }}
            </span>
            <button
              class="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg border border-mist text-ink transition hover:border-moss"
              title="编辑题目"
              @click="openEditForm(question)"
            >
              <Pencil class="h-3.5 w-3.5" />
            </button>
            <button
              class="shrink-0 rounded-lg border border-mist px-2 py-1 text-xs text-ink transition hover:border-moss"
              @click="toggleQuestion(question)"
            >
              {{ question.status === "ON" ? "下架" : "上架" }}
            </button>
            <button
              class="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg border border-mist text-rose transition hover:border-rose"
              title="删除题目"
              @click="deleteQuestion(question)"
            >
              <Trash2 class="h-3.5 w-3.5" />
            </button>
          </div>
        </div>
      </section>
    </template>

    <!-- 删除题库：不可逆操作，先发邮箱验证码再删 -->
    <button
      v-if="deleteOpen"
      class="fixed inset-0 z-[45] bg-ink/25 backdrop-blur-[2px]"
      aria-label="关闭删除确认"
      @click="closeDeleteDialog"
    />
    <section
      v-if="deleteOpen"
      role="dialog"
      aria-modal="true"
      aria-label="删除题库"
      class="fixed inset-x-0 top-0 z-50 mx-auto my-4 max-h-[92vh] w-[calc(100%-2rem)] max-w-md overflow-y-auto rounded-2xl border border-mist bg-paper p-4 shadow-2xl sm:p-5"
    >
      <div class="mb-3 flex items-center justify-between gap-3">
        <h2 class="font-medium text-ink">删除题库</h2>
        <button
          class="flex h-8 w-8 items-center justify-center rounded-lg border border-mist text-oat transition hover:border-rose hover:text-rose"
          aria-label="关闭弹窗"
          title="关闭"
          @click="closeDeleteDialog"
        >
          <X class="h-4 w-4" />
        </button>
      </div>

      <p class="text-sm text-ink">即将删除「{{ bank?.name }}」</p>
      <p class="mt-2 text-sm text-oat">
        题库下的 <span class="text-ink">{{ units.length }}</span> 个单元、<span
          class="text-ink"
          >{{ totalQuestions }}</span
        >
        道题会一并删除，这些题目的练习记录也会消失，删除后无法恢复。
      </p>

      <div class="mt-4">
        <span class="mb-1.5 block text-sm text-ink">邮箱验证码</span>
        <div class="flex gap-2">
          <input
            v-model="deleteCode"
            aria-label="邮箱验证码"
            inputmode="numeric"
            maxlength="6"
            placeholder="6 位验证码"
            class="min-w-0 flex-1 rounded-xl border border-mist bg-paper px-4 py-2.5 text-sm outline-none focus:border-moss"
            @keyup.enter="confirmDeleteBank"
          />
          <button
            class="shrink-0 rounded-xl border border-moss px-3 py-2 text-sm text-moss transition hover:bg-moss/10 disabled:opacity-50"
            :disabled="deleteCooldown > 0 || deleteSending"
            @click="sendDeleteCode"
          >
            {{ deleteCooldown > 0 ? deleteCooldown + " 秒后重发" : "发送验证码" }}
          </button>
        </div>
        <p class="mt-1.5 text-xs text-oat">验证码会发到 {{ auth.user?.email ?? "当前账号邮箱" }}</p>
      </div>

      <p v-if="deleteError" class="mt-3 rounded-xl bg-rose/10 px-4 py-2.5 text-sm text-rose">
        {{ deleteError }}
      </p>

      <div class="mt-4 flex flex-wrap gap-2 border-t border-mist pt-4">
        <button
          class="flex items-center gap-1.5 rounded-xl bg-rose px-4 py-2 text-sm text-white transition hover:bg-rose/90 disabled:opacity-50"
          :disabled="deleteBusy"
          @click="confirmDeleteBank"
        >
          <Loader2 v-if="deleteBusy" class="h-4 w-4 animate-spin" />
          <Trash2 v-else class="h-4 w-4" />
          确认删除
        </button>
        <button
          class="rounded-xl border border-mist px-4 py-2 text-sm text-ink transition hover:border-moss"
          @click="closeDeleteDialog"
        >
          取消
        </button>
      </div>
    </section>
  </div>
</template>
