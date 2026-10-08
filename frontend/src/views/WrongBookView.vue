<script setup lang="ts">
import { onMounted, ref } from "vue";
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
      <p class="mt-1 text-sm text-oat">按最近一次作答结果统计</p>
    </header>

    <div v-if="loading" class="text-sm text-oat">加载中…</div>
    <div v-else-if="items.length === 0" class="rounded-2xl border border-mist bg-white/70 p-10 text-center">
      <BookX class="mx-auto h-8 w-8 text-oat" />
      <p class="mt-3 text-sm text-oat">还没有错题，继续保持</p>
    </div>
    <div v-else class="space-y-3">
      <article
        v-for="item in items"
        :key="item.record_id"
        class="rounded-2xl border border-mist bg-white/70 p-4"
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
