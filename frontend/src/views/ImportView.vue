<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from "vue";
import { useRoute } from "vue-router";
import { ImagePlus, Loader2, Upload } from "lucide-vue-next";
import { api } from "@/api/client";
import { useAuthStore } from "@/stores/auth";

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
  latest_import_id?: string | null;
  latest_import_status?: string | null;
  latest_import_progress?: number | null;
  latest_created_count?: number | null;
  latest_skipped_count?: number | null;
  latest_target_bank_id?: number | null;
  latest_bank_id?: number | null;
  latest_import_error?: string | null;
}

interface Bank {
  id: number;
  name: string;
  is_public: boolean;
  owner_id: string | null;
}

const auth = useAuthStore();
const route = useRoute();
// 从题库管理页进来时带着 ?bankId=，这时直接锁定到那个题库，不用再选
const lockedBankId = Number(route.query.bankId) || null;
const kind = ref<"text" | "url" | "doc" | "screenshot">("text");
const bankName = ref("");
const bankDescription = ref("");
const text = ref("");
const url = ref("");
const fileName = ref("");
const base64 = ref("");
// 截图模式保留完整的 data URL（多模态接口要带 mime），不拆前缀
const screenshotDataUrl = ref("");
const screenshotName = ref("screenshot.png");
const busy = ref(false);
const notice = ref("");
const noticeError = ref("");
const tasks = ref<ImportTask[]>([]);
const currentTask = ref<ImportTask | null>(null);
const selected = ref<number[]>([]);
const banks = ref<Bank[]>([]);
const banksLoaded = ref(false);
// 解析结果的去向：新建题库，或导入到已有题库
const importMode = ref<"new" | "existing">("new");
const targetBankId = ref<number | null>(null);
let pollTimer: number | null = null;

// 只有自己的题库、以及（管理员的）公共题库可以写入；别人的私有题库不在列表里也导不进去
const importableBanks = computed(() =>
  banks.value.filter(
    (bank) => bank.owner_id === auth.user?.id || (bank.is_public && auth.isAdmin),
  ),
);

const lockedBank = computed(() =>
  lockedBankId === null ? null : (importableBanks.value.find((bank) => bank.id === lockedBankId) ?? null),
);
// 带了 bankId 但那个题库不在可导入列表里（后台可能已经被删或者不是自己的）
const lockedBankMissing = computed(
  () => lockedBankId !== null && banksLoaded.value && lockedBank.value === null,
);

async function loadBanks() {
  try {
    banks.value = await api<Bank[]>("/api/banks");
  } catch {
    banks.value = [];
  } finally {
    banksLoaded.value = true;
  }
  applyLockedBank();
}

function applyLockedBank() {
  if (!lockedBank.value) return;
  importMode.value = "existing";
  targetBankId.value = lockedBank.value.id;
  // 任务名沿用题库名，历史任务列表里能看出这批题导去了哪
  if (!bankName.value.trim()) {
    bankName.value = lockedBank.value.name;
  }
}

// 解析任务全局串行：自己已有任务在跑时就先别让我提交
const hasRunningTask = computed(() =>
  tasks.value.some(
    (task) =>
      task.status === "PENDING" ||
      task.status === "RUNNING" ||
      task.latest_import_status === "PENDING" ||
      task.latest_import_status === "RUNNING",
  ),
);
const latestImportRunning = computed(
  () =>
    currentTask.value?.latest_import_status === "PENDING" ||
    currentTask.value?.latest_import_status === "RUNNING",
);
const hasCompletedImport = computed(
  () => currentTask.value?.latest_import_status === "COMPLETED",
);
const latestImportSummary = computed(() => {
  if (!hasCompletedImport.value) return "";
  return `最近一次导入：新增 ${currentTask.value?.latest_created_count ?? 0} 道，跳过 ${
    currentTask.value?.latest_skipped_count ?? 0
  } 道重复题`;
});

const allIndexes = computed(() => (currentTask.value?.items ?? []).map((_, index) => index));

function selectAll() {
  selected.value = [...allIndexes.value];
}

function selectNone() {
  selected.value = [];
}

function invertSelection() {
  const chosen = new Set(selected.value);
  selected.value = allIndexes.value.filter((index) => !chosen.has(index));
}

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

function applyScreenshot(value: string, name: string) {
  screenshotDataUrl.value = value;
  screenshotName.value = name;
  noticeError.value = "";
}

function onScreenshotFile(event: Event) {
  const input = event.target as HTMLInputElement;
  const file = input.files?.[0];
  if (!file) return;
  if (!file.type.startsWith("image/")) {
    noticeError.value = "请选择图片文件（PNG / JPG / WebP）";
    return;
  }
  if (file.size > 5 * 1024 * 1024) {
    noticeError.value = "截图太大了，请压到 5MB 以内";
    return;
  }
  const reader = new FileReader();
  reader.onload = () => applyScreenshot(String(reader.result ?? ""), file.name);
  reader.readAsDataURL(file);
}

/** 只在这个模式下接管粘贴；正文模式里 Ctrl+V 仍然正常粘字 */
function onPaste(event: ClipboardEvent) {
  if (kind.value !== "screenshot") return;
  const items = event.clipboardData?.items ?? [];
  for (const item of items) {
    if (!item.type.startsWith("image/")) continue;
    const file = item.getAsFile();
    if (!file) break;
    event.preventDefault();
    if (file.size > 5 * 1024 * 1024) {
      noticeError.value = "截图太大了，请压到 5MB 以内";
      break;
    }
    const reader = new FileReader();
    reader.onload = () => applyScreenshot(String(reader.result ?? ""), "screenshot.png");
    reader.readAsDataURL(file);
    break;
  }
}

async function submit() {
  if (hasRunningTask.value) {
    noticeError.value = "已有解析任务正在进行中，请等它完成或失败后再提交新任务";
    return;
  }
  if (kind.value === "screenshot" && !screenshotDataUrl.value) {
    noticeError.value = "请先 Ctrl+V 粘贴截图，或点击选择一张图片";
    return;
  }
  busy.value = true;
  notice.value = "";
  noticeError.value = "";
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
    if (kind.value === "screenshot") {
      body.base64 = screenshotDataUrl.value;
      body.fileName = screenshotName.value;
    }
    const response = await api<{ taskId: string }>("/api/import", {
      method: "POST",
      body: JSON.stringify(body),
    });
    notice.value = "任务已创建，正在后台解析…";
    await loadTasks();
    await openTask(response.taskId);
  } catch (e) {
    noticeError.value = e instanceof Error ? e.message : "创建任务失败";
  } finally {
    busy.value = false;
  }
}

async function openTask(taskId: string) {
  currentTask.value = await api<ImportTask>(`/api/import/task/${taskId}`);
  selected.value = (currentTask.value.items ?? []).map((_, index) => index);
  if (lockedBank.value) {
    applyLockedBank();
  } else if (
    currentTask.value.latest_target_bank_id &&
    importableBanks.value.some(
      (bank) => bank.id === currentTask.value?.latest_target_bank_id,
    )
  ) {
    importMode.value = "existing";
    targetBankId.value = currentTask.value.latest_target_bank_id;
  } else {
    // 任务没写题库名、又确实有可导入的题库时，默认按「导入到已有题库」预选，省一次点击
    importMode.value =
      !currentTask.value.bank_name?.trim() && importableBanks.value.length > 0 ? "existing" : "new";
    targetBankId.value = importMode.value === "existing" ? importableBanks.value[0].id : null;
  }
  startPolling(taskId);
}

function startPolling(taskId: string) {
  stopPolling();
  pollTimer = window.setInterval(async () => {
    try {
      const task = await api<ImportTask>(`/api/import/task/${taskId}`);
      currentTask.value = task;
      const parsing = task.status === "PENDING" || task.status === "RUNNING";
      const importing =
        task.latest_import_status === "PENDING" || task.latest_import_status === "RUNNING";
      if (!parsing && !importing) {
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
  if (lockedBankMissing.value) {
    noticeError.value = "要导入的题库不存在，或你没有权限往里加题";
    return;
  }
  if (importMode.value === "existing" && !targetBankId.value) {
    noticeError.value = "请选择要导入到的题库";
    return;
  }
  if (importMode.value === "new" && !currentTask.value.bank_name?.trim()) {
    noticeError.value = "新建题库需要先在左侧填写题库名称";
    return;
  }
  busy.value = true;
  noticeError.value = "";
  const reimport = hasCompletedImport.value;
  try {
    const response = await api<{ jobId: string }>(`/api/import/task/${currentTask.value.id}/import`, {
      method: "POST",
      body: JSON.stringify({
        selectedIndexes: selected.value,
        targetBankId: importMode.value === "existing" ? targetBankId.value : null,
      }),
    });
    currentTask.value.latest_import_id = response?.jobId ?? currentTask.value.latest_import_id;
    currentTask.value.latest_import_status = "PENDING";
    currentTask.value.latest_import_progress = 0;
    currentTask.value.latest_created_count = 0;
    currentTask.value.latest_skipped_count = 0;
    notice.value = reimport ? "已提交再次导入，正在写入题库…" : "已提交导入，正在写入题库…";
    startPolling(currentTask.value.id);
  } catch (e) {
    noticeError.value = e instanceof Error ? e.message : "导入失败";
  } finally {
    busy.value = false;
  }
}

onMounted(() => {
  loadTasks();
  loadBanks();
  window.addEventListener("paste", onPaste);
});
onUnmounted(() => {
  window.removeEventListener("paste", onPaste);
});
</script>

<template>
  <div class="w-full">
    <header class="mb-6">
      <h1 class="text-2xl font-semibold tracking-tight text-ink">导入题目</h1>
      <p class="mt-1 text-sm text-oat">
        <template v-if="lockedBank">解析后导入到「{{ lockedBank.name }}」</template>
        <template v-else>文档、正文或网页链接，AI 解析成可刷的题库</template>
      </p>
    </header>

    <div class="grid gap-6 lg:grid-cols-2">
      <section class="rounded-2xl border border-mist bg-white/70 p-4 sm:p-5">
        <div class="mb-4 flex rounded-xl bg-mist p-1">
          <button
            v-for="item in [
              { key: 'text', label: '粘贴正文' },
              { key: 'url', label: '网页链接' },
              { key: 'doc', label: '上传文档' },
              { key: 'screenshot', label: '截图识别' },
            ]"
            :key="item.key"
            class="flex-1 rounded-lg py-2 text-sm transition"
            :class="kind === item.key ? 'bg-white text-ink shadow-sm' : 'text-oat'"
            @click="kind = item.key as 'text' | 'url' | 'doc' | 'screenshot'"
          >
            {{ item.label }}
          </button>
        </div>

        <div
          v-if="lockedBank"
          class="mb-4 rounded-xl border border-moss/30 bg-moss/10 px-4 py-2.5 text-sm text-ink"
        >
          导入到：<span class="font-medium">{{ lockedBank.name }}</span>
          <RouterLink :to="`/app/banks/${lockedBank.id}`" class="ml-2 text-moss hover:text-moss/80">
            返回题库管理
          </RouterLink>
        </div>
        <p
          v-else-if="lockedBankMissing"
          class="mb-4 rounded-xl bg-rose/10 px-4 py-2.5 text-sm text-rose"
        >
          要导入的题库不存在，或你没有往它里面加题的权限。
        </p>
        <template v-else>
          <label class="mb-4 block">
            <span class="mb-1.5 block text-sm text-ink">
              题库名称<span class="text-oat">（解析后导入到已有题库时可留空）</span>
            </span>
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
        </template>

        <textarea
          v-if="kind === 'text'"
          v-model="text"
          rows="8"
          class="w-full rounded-xl border border-mist bg-paper px-4 py-3 text-sm outline-none focus:border-moss"
          placeholder="粘贴题目正文，每 5000 字切块解析…"
        />
        <div v-else-if="kind === 'url'">
          <input
            v-model="url"
            class="w-full rounded-xl border border-mist bg-paper px-4 py-2.5 text-sm outline-none focus:border-moss"
            placeholder="https://example.com/questions"
          />
          <p class="mt-2 rounded-xl bg-mist/60 px-3 py-2 text-xs leading-relaxed text-oat">
            只抓取 robots.txt 允许爬取的网页。如果该网页不允许爬取，会提示你改用「截图识别」：
            在浏览器里打开该页面、截图后粘贴即可。
          </p>
        </div>
        <label
          v-else-if="kind === 'doc'"
          class="flex cursor-pointer flex-col items-center gap-2 rounded-xl border border-dashed border-mist bg-paper px-4 py-8 text-sm text-oat"
        >
          <Upload class="h-6 w-6" />
          <span>{{ fileName || "选择 docx / doc / pptx / pdf 文件" }}</span>
          <input class="hidden" type="file" accept=".docx,.doc,.pptx,.pdf" @change="onFile" />
        </label>
        <div v-else class="space-y-3">
          <label
            class="flex cursor-pointer flex-col items-center gap-2 rounded-xl border border-dashed border-mist bg-paper px-4 py-6 text-sm text-oat transition hover:border-moss"
          >
            <ImagePlus class="h-6 w-6" />
            <span>Ctrl+V 粘贴截图，或点击选择图片</span>
            <span class="text-xs">PNG / JPG / WebP，单张 ≤ 5MB；识别后交给 AI 拆成题目</span>
            <input
              class="hidden"
              type="file"
              accept="image/png,image/jpeg,image/webp"
              @change="onScreenshotFile"
            />
          </label>
          <div v-if="screenshotDataUrl" class="relative">
            <img
              :src="screenshotDataUrl"
              alt="待识别截图"
              data-testid="screenshot-preview"
              class="max-h-64 w-full rounded-xl border border-mist bg-white object-contain"
            />
            <button
              type="button"
              class="absolute right-2 top-2 rounded-lg bg-ink/80 px-2 py-1 text-xs text-white"
              @click="screenshotDataUrl = ''"
            >
              移除
            </button>
          </div>
        </div>

        <button
          class="mt-5 flex w-full items-center justify-center gap-2 rounded-xl bg-ink py-2.5 text-sm text-white transition hover:bg-ink/90 disabled:opacity-60"
          :disabled="busy || hasRunningTask"
          @click="submit"
        >
          <Loader2 v-if="busy" class="h-4 w-4 animate-spin" />
          {{ hasRunningTask ? "有任务解析中…" : "开始解析" }}
        </button>
        <p v-if="notice" class="mt-3 rounded-xl bg-moss/10 px-4 py-2.5 text-sm text-ink">{{ notice }}</p>
        <p v-if="noticeError" class="mt-3 rounded-xl bg-rose/10 px-4 py-2.5 text-sm text-rose">{{ noticeError }}</p>
        <p v-else-if="hasRunningTask" class="mt-3 text-xs text-oat">
          同一时间只解析一个任务，等当前任务完成或失败后即可提交下一个。
        </p>
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
          <p
            v-if="latestImportSummary"
            class="mt-3 rounded-xl bg-moss/10 px-4 py-2.5 text-sm text-ink"
          >
            {{ latestImportSummary }}
          </p>
          <p
            v-else-if="latestImportRunning"
            class="mt-3 rounded-xl bg-mist px-4 py-2.5 text-sm text-oat"
          >
            最近一次导入进行中，{{ currentTask.latest_import_progress ?? 0 }}%
          </p>
          <p
            v-else-if="currentTask.latest_import_status === 'FAILED' && currentTask.latest_import_error"
            class="mt-2 whitespace-pre-wrap text-xs text-rose"
          >
            {{ currentTask.latest_import_error }}
          </p>
          <div v-if="currentTask.items?.length" class="mt-4">
            <div class="flex flex-wrap items-center justify-between gap-2">
              <p class="text-xs text-oat">
                已选 <span class="font-medium text-ink">{{ selected.length }}</span> /
                {{ allIndexes.length }} 道题
              </p>
              <div class="flex items-center gap-1">
                <button
                  type="button"
                  class="rounded-lg border border-mist bg-white/70 px-2.5 py-1 text-xs text-ink transition hover:border-moss"
                  @click="selectAll"
                >
                  全选
                </button>
                <button
                  type="button"
                  class="rounded-lg border border-mist bg-white/70 px-2.5 py-1 text-xs text-ink transition hover:border-moss"
                  @click="selectNone"
                >
                  全不选
                </button>
                <button
                  type="button"
                  class="rounded-lg border border-mist bg-white/70 px-2.5 py-1 text-xs text-ink transition hover:border-moss"
                  @click="invertSelection"
                >
                  反选
                </button>
              </div>
            </div>
            <div class="mt-2 max-h-96 space-y-2 overflow-y-auto pr-1">
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
          </div>
          <div
            v-if="currentTask.items?.length"
            class="mt-4 rounded-xl border border-mist bg-paper p-3"
          >
            <p class="text-xs text-oat">导入到</p>
            <p v-if="lockedBank" class="mt-1.5 text-sm font-medium text-ink">
              {{ lockedBank.name }}
            </p>
            <div v-if="!lockedBank" class="mt-2 flex flex-wrap gap-2">
              <button
                type="button"
                class="rounded-lg border px-3 py-1.5 text-xs transition"
                :class="
                  importMode === 'new'
                    ? 'border-moss bg-moss/10 text-moss'
                    : 'border-mist bg-white/70 text-ink'
                "
                @click="importMode = 'new'"
              >
                新建题库{{ currentTask.bank_name?.trim() ? `「${currentTask.bank_name}」` : "" }}
              </button>
              <button
                type="button"
                class="rounded-lg border px-3 py-1.5 text-xs transition disabled:opacity-50"
                :class="
                  importMode === 'existing'
                    ? 'border-moss bg-moss/10 text-moss'
                    : 'border-mist bg-white/70 text-ink'
                "
                :disabled="importableBanks.length === 0"
                @click="
                  importMode = 'existing';
                  targetBankId = importableBanks[0]?.id ?? null;
                "
              >
                导入到已有题库
              </button>
            </div>
            <select
              v-if="!lockedBank && importMode === 'existing'"
              v-model="targetBankId"
              class="mt-2 w-full rounded-xl border border-mist bg-paper px-3 py-2 text-sm outline-none focus:border-moss"
            >
              <option :value="null">请选择题库…</option>
              <option v-for="bank in importableBanks" :key="bank.id" :value="bank.id">
                {{ bank.name }}<template v-if="bank.is_public">（公共题库）</template>
              </option>
            </select>
            <p v-if="!lockedBank && importMode === 'existing'" class="mt-1.5 text-xs text-oat">
              {{
                importableBanks.length === 0
                  ? "你还没有自己的题库；公共题库只有管理员能往里加题。"
                  : "只能导入到自己的题库，公共题库由管理员维护。"
              }}
            </p>
          </div>

          <button
            v-if="currentTask.items?.length"
            class="mt-4 w-full rounded-xl bg-moss py-2.5 text-sm text-white transition hover:bg-moss/90 disabled:opacity-60"
            :disabled="busy || selected.length === 0 || latestImportRunning"
            @click="confirmImport"
          >
            {{
              latestImportRunning
                ? "正在再次导入…"
                : hasCompletedImport
                  ? `再次导入选中的 ${selected.length} 道题`
                  : `导入选中的 ${selected.length} 道题`
            }}
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
            <span class="min-w-0 truncate text-ink">
              {{ task.bank_name?.trim() || "未命名任务" }}
            </span>
            <span class="shrink-0 text-xs text-oat">
              {{ task.latest_import_status || task.status }} ·
              {{ task.latest_import_progress ?? task.progress }}%
            </span>
          </button>
        </div>
      </section>
    </div>
  </div>
</template>
