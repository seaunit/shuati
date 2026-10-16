<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { useRouter } from "vue-router";
import { BookX } from "lucide-vue-next";
import { api } from "@/api/client";

interface WrongItem {
  record_id: number;
  question_id: number;
  verdict: string;
  score: number | null;
  last_at: string;
  type: string;
  content: string;
  difficulty: string;
  unit_name: string;
  bank_name: string;
}

const items = ref<WrongItem[]>([]);
const loading = ref(true);
const router = useRouter();
// 筛选：题库 + 判定。都在本地过滤，不额外打接口
const bankFilter = ref("ALL");
const verdictFilter = ref("ALL");

const bankOptions = computed(() => {
  const names = items.value.map((item) => item.bank_name).filter(Boolean);
  return Array.from(new Set(names));
});

const filtered = computed(() =>
  items.value.filter(
    (item) =>
      (bankFilter.value === "ALL" || item.bank_name === bankFilter.value) &&
      (verdictFilter.value === "ALL" || item.verdict === verdictFilter.value),
  ),
);

/** 直接拿这批错题开练：id 按当前列表顺序带过去，做对的题会自动从错题本移除 */
function startPractice(questionIds: number[]) {
  if (questionIds.length === 0) return;
  router.push({ path: "/app/practice", query: { ids: questionIds.join(",") } });
}

function verdictLabel(verdict: string) {
  if (verdict === "CORRECT") return "正确";
  if (verdict === "PARTIAL") return "部分正确";
  if (verdict === "WRONG") return "错误";
  return "待复核";
}

function typeLabel(type: string) {
  if (type === "SINGLE") return "单选题";
  if (type === "MULTI") return "多选题";
  if (type === "SHORT") return "简答题";
  return type;
}

onMounted(async () => {
  try {
    items.value = await api<WrongItem[]>("/api/wrong-book");
  } catch {
    items.value = [];
  } finally {
    loading.value = false;
  }
});
</script>

<template>
  <div class="w-full">
    <header class="mb-6">
      <h1 class="text-2xl font-semibold tracking-tight text-ink">错题本</h1>
      <p class="mt-1 text-sm text-oat">
        按最近一次作答结果统计，同一道题只保留最新一次；答对后自动移出
      </p>
    </header>

    <div v-if="loading" class="text-sm text-oat">加载中…</div>
    <div v-else-if="items.length === 0" class="rounded-2xl border border-mist bg-white/70 p-10 text-center">
      <BookX class="mx-auto h-8 w-8 text-oat" />
      <p class="mt-3 text-sm text-oat">还没有错题，继续保持</p>
    </div>
    <div v-else class="space-y-4">
      <div class="space-y-2 rounded-2xl border border-mist bg-white/70 p-3">
        <div class="flex flex-wrap items-center gap-2" data-testid="wrong-filter-bank">
          <span class="text-xs text-oat">题库</span>
          <button
            class="rounded-lg border px-2.5 py-1 text-xs transition"
            :class="
              bankFilter === 'ALL'
                ? 'border-moss bg-moss/10 text-moss'
                : 'border-mist bg-white/70 text-ink'
            "
            @click="bankFilter = 'ALL'"
          >
            全部
          </button>
          <button
            v-for="name in bankOptions"
            :key="name"
            class="rounded-lg border px-2.5 py-1 text-xs transition"
            :class="
              bankFilter === name
                ? 'border-moss bg-moss/10 text-moss'
                : 'border-mist bg-white/70 text-ink'
            "
            @click="bankFilter = name"
          >
            {{ name }}
          </button>
        </div>
        <div class="flex flex-wrap items-center gap-2" data-testid="wrong-filter-verdict">
          <span class="text-xs text-oat">判定</span>
          <button
            v-for="item in [
              { key: 'ALL', label: '全部' },
              { key: 'WRONG', label: '错误' },
              { key: 'PARTIAL', label: '部分正确' },
            ]"
            :key="item.key"
            class="rounded-lg border px-2.5 py-1 text-xs transition"
            :class="
              verdictFilter === item.key
                ? 'border-moss bg-moss/10 text-moss'
                : 'border-mist bg-white/70 text-ink'
            "
            @click="verdictFilter = item.key"
          >
            {{ item.label }}
          </button>
        </div>
        <p class="text-xs text-oat">共 {{ items.length }} 道错题，当前筛选出 {{ filtered.length }} 道</p>
        <button
          class="mt-1 w-full rounded-xl bg-ink px-4 py-2.5 text-sm text-white transition hover:bg-ink/90 disabled:opacity-50"
          :disabled="filtered.length === 0"
          @click="startPractice(filtered.map((item) => item.question_id))"
        >
          练这批错题（{{ filtered.length }} 道）
        </button>
      </div>

      <div
        v-if="filtered.length === 0"
        class="rounded-2xl border border-mist bg-white/70 p-10 text-center text-sm text-oat"
      >
        没有符合筛选条件的错题
      </div>

      <article
        v-for="item in filtered"
        :key="item.record_id"
        class="cursor-pointer rounded-2xl border border-mist bg-white/70 p-4 transition hover:border-moss"
        @click="startPractice([item.question_id])"
      >
        <div class="flex flex-wrap items-center gap-2 text-xs">
          <span class="rounded-full bg-mist px-2 py-0.5 text-oat">{{ item.bank_name }}</span>
          <span class="rounded-full bg-mist px-2 py-0.5 text-oat">{{ item.unit_name }}</span>
          <span class="rounded-full bg-mist px-2 py-0.5 text-oat">{{ typeLabel(item.type) }}</span>
          <span
            class="rounded-full px-2 py-0.5"
            :class="item.verdict === 'PARTIAL' ? 'bg-sand/50 text-ink' : 'bg-rose/15 text-rose'"
          >
            {{ verdictLabel(item.verdict) }}
          </span>
        </div>
        <p class="mt-3 whitespace-pre-wrap text-sm leading-relaxed text-ink">{{ item.content }}</p>
      </article>
    </div>
  </div>
</template>
