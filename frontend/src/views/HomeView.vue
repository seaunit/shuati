<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { useRouter } from "vue-router";
import { BookOpen, CheckCircle2 } from "lucide-vue-next";
import { api } from "@/api/client";

interface Bank {
  id: number;
  name: string;
  description: string | null;
  is_default: boolean;
  is_public: boolean;
  unit_count: number;
  question_count: number;
  answered_count: number;
}

interface Unit {
  id: number;
  bank_id: number;
  name: string;
  sort: number;
  question_count: number;
  answered_count: number;
}

const router = useRouter();
const banks = ref<Bank[]>([]);
const units = ref<Unit[]>([]);
const bankId = ref<number | null>(null);
const loading = ref(true);
const unitsLoading = ref(false);

const currentBank = computed(() => banks.value.find((b) => b.id === bankId.value));
const totalQuestions = computed(() =>
  units.value.reduce((sum, unit) => sum + (unit.question_count ?? 0), 0),
);
const totalAnswered = computed(() =>
  units.value.reduce((sum, unit) => sum + (unit.answered_count ?? 0), 0),
);
const bankPercent = computed(() =>
  totalQuestions.value ? Math.round((totalAnswered.value / totalQuestions.value) * 100) : 0,
);

function percent(unit: Unit) {
  return unit.question_count ? Math.round((unit.answered_count / unit.question_count) * 100) : 0;
}

async function loadUnits(id: number) {
  unitsLoading.value = true;
  try {
    units.value = await api<Unit[]>(`/api/banks/${id}/units`);
  } catch {
    units.value = [];
  } finally {
    unitsLoading.value = false;
  }
}

function selectBank(id: number) {
  bankId.value = id;
  loadUnits(id);
}

onMounted(async () => {
  try {
    banks.value = await api<Bank[]>("/api/banks");
    const first = banks.value.find((b) => b.is_default) ?? banks.value[0];
    if (first) {
      bankId.value = first.id;
      await loadUnits(first.id);
    }
  } finally {
    loading.value = false;
  }
});
</script>

<template>
  <div class="mx-auto max-w-6xl">
    <header class="mb-6">
      <h1 class="text-2xl font-semibold tracking-tight text-ink">题库</h1>
      <p class="mt-1 text-sm text-oat">选择题库后，按单元开始练习</p>
    </header>

    <div v-if="loading" class="space-y-4">
      <div class="h-10 animate-pulse rounded-xl border border-mist bg-white/50" />
      <div class="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <div
          v-for="i in 4"
          :key="i"
          class="h-36 animate-pulse rounded-2xl border border-mist bg-white/60"
        />
      </div>
    </div>

    <div v-else-if="banks.length === 0" class="rounded-2xl border border-mist bg-white/70 p-10 text-center">
      <BookOpen class="mx-auto h-8 w-8 text-oat" />
      <p class="mt-3 text-sm text-oat">还没有可练习的题库</p>
    </div>

    <template v-else>
      <div class="flex flex-wrap gap-2">
        <button
          v-for="bank in banks"
          :key="bank.id"
          class="rounded-xl border px-4 py-2 text-sm transition"
          :class="
            bankId === bank.id
              ? 'border-moss bg-moss/10 text-moss'
              : 'border-mist bg-white/70 text-ink/75 hover:border-moss/60'
          "
          @click="selectBank(bank.id)"
        >
          {{ bank.name }}
          <span v-if="bank.is_default" class="ml-1 text-xs text-oat">默认</span>
        </button>
      </div>

      <section v-if="currentBank" class="mt-6 rounded-2xl border border-mist bg-white/70 p-5">
        <div class="flex flex-wrap items-center justify-between gap-3">
          <div>
            <h2 class="font-medium text-ink">{{ currentBank.name }}</h2>
            <p class="mt-1 text-xs text-oat">
              {{ currentBank.description || "共 " + totalQuestions + " 题" }}
            </p>
          </div>
          <div class="text-right">
            <p class="text-sm text-ink">{{ totalAnswered }} / {{ totalQuestions }}</p>
            <p class="text-xs text-oat">{{ bankPercent }}% 已完成</p>
          </div>
        </div>
        <div class="mt-3 h-2 overflow-hidden rounded-full bg-mist">
          <div class="h-full rounded-full bg-moss" :style="{ width: bankPercent + '%' }" />
        </div>
        <button
          class="mt-4 rounded-xl bg-ink px-4 py-2 text-sm text-white transition hover:bg-ink/90 disabled:opacity-50"
          :disabled="totalQuestions === 0"
          @click="router.push(`/app/practice?bankId=${currentBank.id}`)"
        >
          开始练习本库
        </button>
      </section>

      <section class="mt-6">
        <h2 class="mb-3 font-medium text-ink">单元</h2>
        <div v-if="unitsLoading" class="text-sm text-oat">加载中…</div>
        <div
          v-else-if="units.length === 0"
          class="rounded-2xl border border-mist bg-white/70 p-8 text-center text-sm text-oat"
        >
          该题库还没有单元
        </div>
        <div v-else class="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
          <button
            v-for="unit in units"
            :key="unit.id"
            class="rounded-2xl border border-mist bg-white/70 p-4 text-left transition hover:border-moss"
            @click="router.push(`/app/practice?unitId=${unit.id}`)"
          >
            <div class="flex items-center justify-between">
              <p class="text-sm font-medium text-ink">{{ unit.name }}</p>
              <CheckCircle2
                v-if="unit.question_count && unit.answered_count >= unit.question_count"
                class="h-4 w-4 text-moss"
              />
            </div>
            <div class="mt-3 h-1.5 overflow-hidden rounded-full bg-mist">
              <div class="h-full rounded-full bg-moss" :style="{ width: percent(unit) + '%' }" />
            </div>
            <p class="mt-2 text-xs text-oat">{{ unit.answered_count }} / {{ unit.question_count }} 题</p>
          </button>
        </div>
      </section>
    </template>
  </div>
</template>
