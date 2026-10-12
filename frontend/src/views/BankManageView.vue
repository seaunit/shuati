<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { useRoute } from "vue-router";
import { Check, Loader2, Pencil, Plus, Trash2, X } from "lucide-vue-next";
import { api } from "@/api/client";

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

interface Question {
  id: number;
  unit_id: number;
  type: string;
  content: string;
  status: string;
}

const route = useRoute();
const bankId = Number(route.params.id);

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

const unitNameById = computed(() => {
  const map = new Map<number, string>();
  units.value.forEach((unit) => map.set(unit.id, unit.name));
  return map;
});

function typeLabel(type: string) {
  if (type === "SINGLE") return "单选";
  if (type === "MULTI") return "多选";
  if (type === "SHORT") return "简答";
  return type;
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

async function deleteBank() {
  if (!window.confirm("删除题库后不可恢复，确定删除？")) return;
  noticeError.value = "";
  try {
    await api(`/api/manage/banks/${bankId}`, { method: "DELETE" });
    notice.value = "题库已删除";
    window.location.href = "/app";
  } catch (e) {
    fail(e, "删除失败");
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

onMounted(load);
</script>

<template>
  <div class="w-full">
    <header class="mb-6 flex flex-wrap items-start justify-between gap-3">
      <div class="min-w-0">
        <h1 class="text-2xl font-semibold tracking-tight text-ink">题库维护</h1>
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
            @click="deleteBank"
          >
            <Trash2 class="h-4 w-4" />
            删除题库
          </button>
        </div>
      </section>

      <section class="mt-6 rounded-2xl border border-mist bg-white/70 p-4 sm:p-5">
        <h2 class="mb-3 font-medium text-ink">单元</h2>
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
            <span class="min-w-0 flex-1 truncate text-sm text-ink">{{ question.content }}</span>
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
  </div>
</template>
