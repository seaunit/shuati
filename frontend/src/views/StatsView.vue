<script setup lang="ts">
import { onMounted, ref } from "vue";
import { api } from "@/api/client";

interface Stats {
  overall: { total: number; correct: number; partial: number; wrong: number; pending: number };
  byUnit: { bank_name: string; unit_name: string; total: number; correct: number }[];
}

interface Session {
  id: number;
  scope_name: string;
  total_questions: number;
  answered: number;
  correct: number;
  score: number | null;
  status: string;
  started_at: string;
}

const stats = ref<Stats | null>(null);
const sessions = ref<Session[]>([]);

function formatTime(value: string) {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "";
  const pad = (n: number) => String(n).padStart(2, "0");
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

function statusLabel(status: string) {
  if (status === "COMPLETED") return "已完成";
  if (status === "ABANDONED") return "已放弃";
  return "进行中";
}

onMounted(async () => {
  try {
    stats.value = await api<Stats>("/api/stats/me");
  } catch {
    stats.value = { overall: { total: 0, correct: 0, partial: 0, wrong: 0, pending: 0 }, byUnit: [] };
  }
  try {
    sessions.value = await api<Session[]>("/api/practice/sessions");
  } catch {
    sessions.value = [];
  }
});
</script>

<template>
  <div class="w-full">
    <header class="mb-6">
      <h1 class="text-2xl font-semibold tracking-tight text-ink">我的统计</h1>
      <p class="mt-1 text-sm text-oat">按题库 → 单元维度汇总</p>
    </header>

    <div v-if="!stats" class="flex h-64 items-center justify-center text-sm text-oat">加载中…</div>
    <template v-else>
      <div class="grid grid-cols-2 gap-3 sm:grid-cols-5">
        <div class="rounded-2xl border border-mist bg-white/70 p-4 text-center shadow-sm">
          <p class="text-2xl font-semibold text-ink">{{ stats.overall.total }}</p>
          <p class="mt-1 text-xs text-oat">总刷题数</p>
        </div>
        <div class="rounded-2xl border border-mist bg-white/70 p-4 text-center shadow-sm">
          <p class="text-2xl font-semibold text-ink">{{ stats.overall.correct }}</p>
          <p class="mt-1 text-xs text-oat">答对</p>
        </div>
        <div class="rounded-2xl border border-mist bg-white/70 p-4 text-center shadow-sm">
          <p class="text-2xl font-semibold text-ink">{{ stats.overall.partial }}</p>
          <p class="mt-1 text-xs text-oat">部分正确</p>
        </div>
        <div class="rounded-2xl border border-mist bg-white/70 p-4 text-center shadow-sm">
          <p class="text-2xl font-semibold text-ink">{{ stats.overall.wrong }}</p>
          <p class="mt-1 text-xs text-oat">答错</p>
        </div>
        <div class="rounded-2xl border border-mist bg-white/70 p-4 text-center shadow-sm">
          <p class="text-2xl font-semibold text-ink">
            {{ stats.overall.total ? Math.round((stats.overall.correct / stats.overall.total) * 100) : 0 }}%
          </p>
          <p class="mt-1 text-xs text-oat">正确率</p>
        </div>
      </div>

      <div class="mt-6 overflow-x-auto rounded-2xl border border-mist bg-white/70 shadow-sm">
        <div class="border-b border-mist px-5 py-3 text-sm font-medium text-ink">单元明细</div>
        <div v-if="stats.byUnit.length === 0" class="p-10 text-center text-sm text-oat">还没有作答记录</div>
        <table v-else class="w-full min-w-[42rem] text-sm">
          <thead>
            <tr class="text-left text-xs text-oat">
              <th class="px-5 py-2.5 font-normal">题库</th>
              <th class="px-3 py-2.5 font-normal">单元</th>
              <th class="px-3 py-2.5 text-right font-normal">作答</th>
              <th class="px-3 py-2.5 text-right font-normal">答对</th>
              <th class="px-5 py-2.5 text-right font-normal">正确率</th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="(unit, index) in stats.byUnit"
              :key="index"
              class="border-t border-mist/60 text-ink/80"
            >
              <td class="px-5 py-2.5">{{ unit.bank_name }}</td>
              <td class="px-3 py-2.5">{{ unit.unit_name }}</td>
              <td class="px-3 py-2.5 text-right">{{ unit.total }}</td>
              <td class="px-3 py-2.5 text-right">{{ unit.correct }}</td>
              <td class="px-5 py-2.5 text-right">
                {{ unit.total ? Math.round((unit.correct / unit.total) * 100) : 0 }}%
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <div class="mt-6 overflow-x-auto rounded-2xl border border-mist bg-white/70 shadow-sm">
        <div class="border-b border-mist px-5 py-3 text-sm font-medium text-ink">我的练习记录</div>
        <div v-if="sessions.length === 0" class="p-10 text-center text-sm text-oat">还没有练习记录</div>
        <table v-else class="w-full min-w-[42rem] text-sm">
          <thead>
            <tr class="text-left text-xs text-oat">
              <th class="px-5 py-2.5 font-normal">范围</th>
              <th class="px-3 py-2.5 font-normal">时间</th>
              <th class="px-3 py-2.5 text-right font-normal">得分</th>
              <th class="px-3 py-2.5 text-right font-normal">答对/总题</th>
              <th class="px-5 py-2.5 text-right font-normal">状态</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="session in sessions" :key="session.id" class="border-t border-mist/60 text-ink/80">
              <td class="px-5 py-2.5">{{ session.scope_name }}</td>
              <td class="px-3 py-2.5 text-ink/60">{{ formatTime(session.started_at) }}</td>
              <td class="px-3 py-2.5 text-right">{{ session.score ?? "-" }}</td>
              <td class="px-3 py-2.5 text-right">{{ session.correct }}/{{ session.total_questions }}</td>
              <td class="px-5 py-2.5 text-right">{{ statusLabel(session.status) }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </template>
  </div>
</template>
