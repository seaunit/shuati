<script setup lang="ts">
import { onMounted, ref } from "vue";
import { Loader2, Upload } from "lucide-vue-next";
import { api } from "@/api/client";

interface ImportItem {
  unit: string;
  type: string;
  content: string;
  answer: string;
  difficulty?: string;
}

interface ImportTask {
  id: string;
  kind: string;
  phase: string;
  status: string;
  progress: number;
  total_chunks: number;
  done_chunks: number;
  bank_name: string;
  error: string | null;
  items?: ImportItem[] | null;
}

const kind = ref<"text" | "url" | "doc">("text");
const bankName = ref("");
const bankDescription = ref("");
const text = ref("");
const url = ref("");
const fileName = ref("");
const base64 = ref("");
const busy = ref(false);
const notice = ref("");
const tasks = ref<ImportTask[]>([]);
const currentTask = ref<ImportTask | null>(null);
const selected = ref<number[]>([]);
let pollTimer: number | null = null;

async function loadTasks() {
  try {
    tasks.value = await api<ImportTask[]>("/api/import/list");
  } catch {
    tasks.value = [];
  }
}

function onFile(event: Event) {
  const input = event.target as HTMLInputElement;
  const file = input.files?.[0];
  if (!file) return;
  fileName.value = file.name;
  const reader = new FileReader();
  reader.onload = () => {
    const value = String(reader.result ?? "");
    base64.value = value.includes(",") ? value.split(",")[1] : value;
  };
  reader.readAsDataURL(file);
}

async function submit() {
  if (!bankName.value.trim()) {
    notice.value = "请填写题库名称";
    return;
  }
  busy.value = true;
  notice.value = "";
  try {
    const body: Record<string, unknown> = {
      kind: kind.value,
      bankName: bankName.value.trim(),
      bankDescription: bankDescription.value.trim() || null,
    };
    if (kind.value === "text") body.text = text.value;
    if (kind.value === "url") body.url = url.value.trim();
    if (kind.value === "doc") {
      body.base64 = base64.value;
      body.fileName = fileName.value;
    }
    const response = await api<{ taskId: string }>("/api/import", {
      method: "POST",
      body: JSON.stringify(body),
    });
    notice.value = "任务已创建，正在后台解析…";
    await loadTasks();
    await openTask(response.taskId);
  } catch (e) {
    notice.value = e instanceof Error ? e.message : "创建任务失败";
  } finally {
    busy.value = false;
  }
}

async function openTask(taskId: string) {
  currentTask.value = await api<ImportTask>(`/api/import/task/${taskId}`);
  selected.value = (currentTask.value.items ?? []).map((_, index) => index);
  startPolling(taskId);
}

function startPolling(taskId: string) {
  stopPolling();
  pollTimer = window.setInterval(async () => {
    try {
      const task = await api<ImportTask>(`/api/import/task/${taskId}`);
      currentTask.value = task;
      if (task.phase === "READY" || task.phase === "DONE" || task.status === "FAILED") {
        stopPolling();
        await loadTasks();
      }
    } catch {
      stopPolling();
    }
  }, 2000);
}

function stopPolling() {
  if (pollTimer !== null) {
    window.clearInterval(pollTimer);
    pollTimer = null;
  }
}

async function confirmImport() {
  if (!currentTask.value) return;
  busy.value = true;
  try {
    await api(`/api/import/task/${currentTask.value.id}/import`, {
      method: "POST",
      body: JSON.stringify({ selectedIndexes: selected.value }),
    });
    notice.value = "已提交导入，正在写入题库…";
    startPolling(currentTask.value.id);
  } catch (e) {
    notice.value = e instanceof Error ? e.message : "导入失败";
  } finally {
    busy.value = false;
  }
}

onMounted(loadTasks);
</script>

<template>
  <div class="mx-auto max-w-6xl">
    <header class="mb-6">
      <h1 class="text-2xl font-semibold tracking-tight text-ink">导入题库</h1>
      <p class="mt-1 text-sm text-oat">文档、正文或网页链接，AI 解析成可刷的题库</p>
    </header>

    <div class="grid gap-6 lg:grid-cols-2">
      <section class="rounded-2xl border border-mist bg-white/70 p-4 sm:p-5">
        <div class="mb-4 flex rounded-xl bg-mist p-1">
          <button
            v-for="item in [
              { key: 'text', label: '粘贴正文' },
              { key: 'url', label: '网页链接' },
              { key: 'doc', label: '上传文档' },
            ]"
            :key="item.key"
            class="flex-1 rounded-lg py-2 text-sm transition"
            :class="kind === item.key ? 'bg-white text-ink shadow-sm' : 'text-oat'"
            @click="kind = item.key as 'text' | 'url' | 'doc'"
          >
            {{ item.label }}
          </button>
        </div>

        <label class="mb-4 block">
          <span class="mb-1.5 block text-sm text-ink">题库名称</span>
          <input
            v-model="bankName"
            class="w-full rounded-xl border border-mist bg-paper px-4 py-2.5 text-sm outline-none focus:border-moss"
            placeholder="例如：软考真题"
          />
        </label>
        <label class="mb-4 block">
          <span class="mb-1.5 block text-sm text-ink">描述（可选）</span>
          <input
            v-model="bankDescription"
            class="w-full rounded-xl border border-mist bg-paper px-4 py-2.5 text-sm outline-none focus:border-moss"
          />
        </label>

        <textarea
          v-if="kind === 'text'"
          v-model="text"
          rows="8"
          class="w-full rounded-xl border border-mist bg-paper px-4 py-3 text-sm outline-none focus:border-moss"
          placeholder="粘贴题目正文，每 5000 字切块解析…"
        />
        <input
          v-else-if="kind === 'url'"
          v-model="url"
          class="w-full rounded-xl border border-mist bg-paper px-4 py-2.5 text-sm outline-none focus:border-moss"
          placeholder="https://example.com/questions"
        />
        <label
          v-else
          class="flex cursor-pointer flex-col items-center gap-2 rounded-xl border border-dashed border-mist bg-paper px-4 py-8 text-sm text-oat"
        >
          <Upload class="h-6 w-6" />
          <span>{{ fileName || "选择 docx / doc / pptx / pdf 文件" }}</span>
          <input class="hidden" type="file" accept=".docx,.doc,.pptx,.pdf" @change="onFile" />
        </label>

        <button
          class="mt-5 flex w-full items-center justify-center gap-2 rounded-xl bg-ink py-2.5 text-sm text-white transition hover:bg-ink/90 disabled:opacity-60"
          :disabled="busy"
          @click="submit"
        >
          <Loader2 v-if="busy" class="h-4 w-4 animate-spin" />
          开始解析
        </button>
        <p v-if="notice" class="mt-3 rounded-xl bg-moss/10 px-4 py-2.5 text-sm text-ink">{{ notice }}</p>
      </section>

      <section class="rounded-2xl border border-mist bg-white/70 p-4 sm:p-5">
        <h2 class="mb-3 font-medium text-ink">解析结果</h2>
        <div v-if="!currentTask" class="text-sm text-oat">还没有任务</div>
        <template v-else>
          <p class="text-sm text-ink">
            {{ currentTask.bank_name }} · {{ currentTask.status }}
            <span class="text-oat">
              {{ currentTask.done_chunks }}/{{ currentTask.total_chunks }} 块，{{ currentTask.progress }}%
            </span>
          </p>
          <p v-if="currentTask.error" class="mt-2 whitespace-pre-wrap text-xs text-rose">{{ currentTask.error }}</p>
          <div v-if="currentTask.items?.length" class="mt-4 max-h-96 space-y-2 overflow-y-auto pr-1">
            <label
              v-for="(item, index) in currentTask.items"
              :key="index"
              class="flex gap-3 rounded-xl border border-mist bg-paper p-3 text-sm"
            >
              <input v-model="selected" type="checkbox" :value="index" class="mt-1" />
              <span class="min-w-0">
                <span class="text-xs text-oat">{{ item.unit }} · {{ item.type }}</span>
                <span class="mt-1 block whitespace-pre-wrap text-ink">{{ item.content }}</span>
              </span>
            </label>
          </div>
          <button
            v-if="currentTask.phase === 'READY'"
            class="mt-4 w-full rounded-xl bg-moss py-2.5 text-sm text-white transition hover:bg-moss/90 disabled:opacity-60"
            :disabled="busy || selected.length === 0"
            @click="confirmImport"
          >
            导入选中的 {{ selected.length }} 道题
          </button>
        </template>

        <h3 class="mb-2 mt-6 font-medium text-ink">历史任务</h3>
        <div class="space-y-1">
          <button
            v-for="task in tasks"
            :key="task.id"
            class="flex w-full items-center justify-between gap-3 rounded-xl px-3 py-2 text-left text-sm transition hover:bg-mist"
            @click="openTask(task.id)"
          >
            <span class="min-w-0 truncate text-ink">{{ task.bank_name }}</span>
            <span class="shrink-0 text-xs text-oat">{{ task.status }} · {{ task.progress }}%</span>
          </button>
        </div>
      </section>
    </div>
  </div>
</template>
