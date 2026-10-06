<script setup lang="ts">
import { onMounted, ref, watch } from "vue";
import { api } from "@/api/client";

interface Bank {
  id: number;
  name: string;
  description: string | null;
  is_default: boolean;
  is_public: boolean;
  unit_count: number;
  question_count: number;
}

interface Unit {
  id: number;
  bank_id: number;
  name: string;
  sort: number;
  question_count: number;
}

interface Question {
  id: number;
  unit_id: number;
  type: string;
  content: string;
  answer: string;
  difficulty: string;
  status: string;
}

interface User {
  id: string;
  email: string;
  nickname: string | null;
  role: string;
  status: string;
  created_at: string;
}

interface Appeal {
  id: number;
  reason: string | null;
  status: string;
  admin_note: string | null;
  user_email: string;
  question_content: string;
  user_answer: string;
  verdict: string;
}

interface AiConfig {
  id: number;
  name: string;
  base_url: string;
  model: string;
  purpose: string;
  status: string;
  api_key_masked: string;
}

const TABS = [
  { id: "dashboard", label: "数据统计" },
  { id: "banks", label: "题库" },
  { id: "units", label: "单元" },
  { id: "questions", label: "题目" },
  { id: "users", label: "用户" },
  { id: "appeals", label: "申诉" },
  { id: "ai", label: "AI 配置" },
  { id: "billing", label: "计费" },
];

const tab = ref("dashboard");
const notice = ref("");

const overview = ref<Record<string, any> | null>(null);
const byUnit = ref<Record<string, any>[]>([]);
const aiUsage = ref<Record<string, any> | null>(null);

const banks = ref<Bank[]>([]);
const units = ref<Unit[]>([]);
const questions = ref<Question[]>([]);
const users = ref<User[]>([]);
const appeals = ref<Appeal[]>([]);
const aiConfig = ref<AiConfig | null>(null);
const billing = ref<Record<string, any> | null>(null);

const selectedBank = ref<number | null>(null);
const selectedUnit = ref<number | null>(null);
const appealStatus = ref("PENDING");

const bankForm = ref({ name: "", description: "" });
const unitForm = ref({ name: "", sort: 0 });
const questionForm = ref({
  type: "SINGLE",
  content: "",
  answer: "",
  options: '[{"key":"A","text":""},{"key":"B","text":""}]',
  keyPoints: "[]",
  difficulty: "MEDIUM",
  explanation: "",
});
const aiForm = ref({ name: "DeepSeek 默认", apiKey: "", model: "deepseek-flash", remark: "" });

async function loadDashboard() {
  overview.value = await api("/api/admin/stats/overview");
  byUnit.value = await api("/api/admin/stats/by-unit");
  aiUsage.value = await api("/api/admin/stats/ai-usage");
}

async function loadBanks() {
  banks.value = await api<Bank[]>("/api/admin/banks");
  if (!selectedBank.value && banks.value.length) {
    selectedBank.value = banks.value[0].id;
  }
}

async function loadUnits() {
  if (!selectedBank.value) {
    units.value = [];
    return;
  }
  units.value = await api<Unit[]>(`/api/admin/units?bankId=${selectedBank.value}`);
}

async function loadQuestions() {
  const params = new URLSearchParams();
  if (selectedUnit.value) params.set("unitId", String(selectedUnit.value));
  else if (selectedBank.value) params.set("bankId", String(selectedBank.value));
  questions.value = await api<Question[]>(`/api/admin/questions?${params.toString()}`);
}

async function loadUsers() {
  users.value = await api<User[]>("/api/admin/users");
}

async function loadAppeals() {
  appeals.value = await api<Appeal[]>(`/api/admin/appeals?status=${appealStatus.value}`);
}

async function loadAi() {
  const list = await api<AiConfig[]>("/api/admin/ai-config");
  aiConfig.value = list[0] ?? null;
  aiForm.value = {
    name: aiConfig.value?.name ?? "DeepSeek 默认",
    apiKey: "",
    model: aiConfig.value?.model ?? "deepseek-flash",
    remark: "",
  };
}

async function loadBilling() {
  billing.value = await api("/api/admin/billing");
}

async function refresh() {
  notice.value = "";
  try {
    if (tab.value === "dashboard") await loadDashboard();
    if (tab.value === "banks") await loadBanks();
    if (tab.value === "units") await Promise.all([loadBanks(), loadUnits()]);
    if (tab.value === "questions") await Promise.all([loadBanks(), loadQuestions()]);
    if (tab.value === "users") await loadUsers();
    if (tab.value === "appeals") await loadAppeals();
    if (tab.value === "ai") await loadAi();
    if (tab.value === "billing") await loadBilling();
  } catch (e) {
    notice.value = e instanceof Error ? e.message : "加载失败";
  }
}

async function createBank() {
  await api("/api/admin/banks", { method: "POST", body: JSON.stringify(bankForm.value) });
  bankForm.value = { name: "", description: "" };
  await loadBanks();
}

async function deleteBank(id: number) {
  if (!confirm("确认删除该题库及其单元和题目？")) return;
  await api(`/api/admin/banks/${id}`, { method: "DELETE" });
  await loadBanks();
}

async function setDefaultBank(id: number) {
  await api(`/api/admin/banks/${id}/default`, { method: "POST" });
  await loadBanks();
}

async function createUnit() {
  if (!selectedBank.value) return;
  await api("/api/admin/units", {
    method: "POST",
    body: JSON.stringify({ ...unitForm.value, bankId: selectedBank.value }),
  });
  unitForm.value = { name: "", sort: 0 };
  await loadUnits();
}

async function deleteUnit(id: number) {
  await api(`/api/admin/units/${id}`, { method: "DELETE" });
  await loadUnits();
}

async function createQuestion() {
  if (!selectedUnit.value) {
    notice.value = "请先选择单元";
    return;
  }
  await api("/api/admin/questions", {
    method: "POST",
    body: JSON.stringify({
      unitId: selectedUnit.value,
      type: questionForm.value.type,
      content: questionForm.value.content,
      answer: questionForm.value.answer,
      options: JSON.parse(questionForm.value.options || "null"),
      keyPoints: JSON.parse(questionForm.value.keyPoints || "null"),
      difficulty: questionForm.value.difficulty,
      explanation: questionForm.value.explanation || null,
    }),
  });
  questionForm.value.content = "";
  questionForm.value.answer = "";
  await loadQuestions();
}

async function deleteQuestion(id: number) {
  await api(`/api/admin/questions/${id}`, { method: "DELETE" });
  await loadQuestions();
}

async function toggleQuestion(question: Question) {
  await api(`/api/admin/questions/${question.id}/status`, {
    method: "PATCH",
    body: JSON.stringify({ status: question.status === "ON" ? "OFF" : "ON" }),
  });
  await loadQuestions();
}

async function toggleUser(user: User) {
  await api(`/api/admin/users/${user.id}`, {
    method: "PATCH",
    body: JSON.stringify({ status: user.status === "ENABLED" ? "DISABLED" : "ENABLED" }),
  });
  await loadUsers();
}

async function toggleRole(user: User) {
  await api(`/api/admin/users/${user.id}`, {
    method: "PATCH",
    body: JSON.stringify({ role: user.role === "ADMIN" ? "USER" : "ADMIN" }),
  });
  await loadUsers();
}

async function grant(user: User) {
  const value = prompt("发放点数（负数表示扣减）", "100");
  if (!value) return;
  await api(`/api/admin/users/${user.id}/points`, {
    method: "POST",
    body: JSON.stringify({ action: "GRANT", points: Number(value) }),
  });
  notice.value = "点数已调整";
}

async function resolveAppeal(appeal: Appeal, status: "RESOLVED" | "REJECTED") {
  await api(`/api/admin/appeals/${appeal.id}/resolve`, {
    method: "POST",
    body: JSON.stringify({ status, adminNote: "" }),
  });
  await loadAppeals();
}

async function saveAiConfig() {
  await api("/api/admin/ai-config", { method: "PUT", body: JSON.stringify(aiForm.value) });
  notice.value = "AI 配置已保存并启用";
  await loadAi();
}

async function testAi() {
  const payload: Record<string, unknown> = {
    baseUrl: aiConfig.value?.base_url ?? "https://api.deepseek.com",
    model: aiForm.value.model,
  };
  if (aiConfig.value) payload.id = aiConfig.value.id;
  if (aiForm.value.apiKey) payload.apiKey = aiForm.value.apiKey;
  const result = await api<{ success: boolean; latencyMs: number; error?: string }>(
    "/api/admin/ai-config/test",
    { method: "POST", body: JSON.stringify(payload) },
  );
  notice.value = result.success ? `连通正常（${result.latencyMs}ms）` : `失败：${result.error}`;
}

async function settleOrder(orderId: string, action: "PAID" | "CANCELLED") {
  await api(`/api/admin/orders/${orderId}/settle`, {
    method: "POST",
    body: JSON.stringify({ action }),
  });
  await loadBilling();
}

watch(selectedBank, async () => {
  selectedUnit.value = null;
  if (tab.value === "units") await loadUnits();
  if (tab.value === "questions") await loadQuestions();
});

watch(selectedUnit, async () => {
  if (tab.value === "questions") await loadQuestions();
});

onMounted(refresh);
watch(tab, refresh);
</script>

<template>
  <div class="mx-auto max-w-6xl">
    <header class="mb-6">
      <h1 class="text-2xl font-semibold tracking-tight text-ink">后台管理</h1>
      <p class="mt-1 text-sm text-oat">题库、题目、用户、AI 与计费</p>
    </header>

    <div class="mb-5 flex flex-wrap gap-2">
      <button
        v-for="item in TABS"
        :key="item.id"
        class="rounded-xl px-3 py-1.5 text-sm transition"
        :class="tab === item.id ? 'bg-moss/15 font-medium text-moss' : 'text-ink/75 hover:bg-mist'"
        @click="tab = item.id"
      >
        {{ item.label }}
      </button>
    </div>

    <p v-if="notice" class="mb-4 rounded-xl bg-moss/10 px-4 py-2.5 text-sm text-ink">{{ notice }}</p>

    <section v-if="tab === 'dashboard'" class="space-y-6">
      <div v-if="overview" class="grid grid-cols-2 gap-3 sm:grid-cols-4">
        <div class="rounded-2xl border border-mist bg-white/70 p-4">
          <p class="text-xs text-oat">用户数</p>
          <p class="mt-1 text-2xl font-semibold text-ink">{{ overview.userCount }}</p>
        </div>
        <div class="rounded-2xl border border-mist bg-white/70 p-4">
          <p class="text-xs text-oat">题目数</p>
          <p class="mt-1 text-2xl font-semibold text-ink">{{ overview.questionCount }}</p>
        </div>
        <div class="rounded-2xl border border-mist bg-white/70 p-4">
          <p class="text-xs text-oat">作答记录</p>
          <p class="mt-1 text-2xl font-semibold text-ink">{{ overview.answerCount }}</p>
        </div>
        <div class="rounded-2xl border border-mist bg-white/70 p-4">
          <p class="text-xs text-oat">今日活跃</p>
          <p class="mt-1 text-2xl font-semibold text-ink">{{ overview.todayActiveUsers }}</p>
        </div>
        <div class="rounded-2xl border border-mist bg-white/70 p-4">
          <p class="text-xs text-oat">AI 调用</p>
          <p class="mt-1 text-2xl font-semibold text-ink">{{ overview.ai_calls }}</p>
        </div>
        <div class="rounded-2xl border border-mist bg-white/70 p-4">
          <p class="text-xs text-oat">Token 用量</p>
          <p class="mt-1 text-2xl font-semibold text-ink">{{ overview.total_tokens }}</p>
        </div>
        <div class="rounded-2xl border border-mist bg-white/70 p-4">
          <p class="text-xs text-oat">答对 / 答错</p>
          <p class="mt-1 text-2xl font-semibold text-ink">
            {{ overview.verdicts.CORRECT }} / {{ overview.verdicts.WRONG }}
          </p>
        </div>
        <div v-if="aiUsage" class="rounded-2xl border border-mist bg-white/70 p-4">
          <p class="text-xs text-oat">AI 失败次数</p>
          <p class="mt-1 text-2xl font-semibold text-ink">{{ aiUsage.summary.failed_calls }}</p>
        </div>
      </div>
      <div class="overflow-hidden rounded-2xl border border-mist bg-white/70">
        <div class="border-b border-mist px-5 py-3 text-sm font-medium text-ink">按单元统计</div>
        <table class="w-full text-sm">
          <thead>
            <tr class="text-left text-xs text-oat">
              <th class="px-5 py-2.5 font-normal">题库</th>
              <th class="px-3 py-2.5 font-normal">单元</th>
              <th class="px-3 py-2.5 text-right font-normal">作答</th>
              <th class="px-5 py-2.5 text-right font-normal">答对</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(row, index) in byUnit" :key="index" class="border-t border-mist/60">
              <td class="px-5 py-2.5">{{ row.bank_name }}</td>
              <td class="px-3 py-2.5">{{ row.unit_name }}</td>
              <td class="px-3 py-2.5 text-right">{{ row.total }}</td>
              <td class="px-5 py-2.5 text-right">{{ row.correct }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>

    <section v-else-if="tab === 'banks'" class="space-y-4">
      <div class="flex flex-wrap items-end gap-2 rounded-2xl border border-mist bg-white/70 p-4">
        <input v-model="bankForm.name" class="rounded-xl border border-mist bg-paper px-3 py-2 text-sm" placeholder="新题库名称" />
        <input v-model="bankForm.description" class="rounded-xl border border-mist bg-paper px-3 py-2 text-sm" placeholder="描述（可选）" />
        <button class="rounded-xl bg-ink px-4 py-2 text-sm text-white" @click="createBank">新建公共题库</button>
      </div>
      <div class="overflow-hidden rounded-2xl border border-mist bg-white/70">
        <table class="w-full text-sm">
          <thead class="bg-mist/60 text-left text-xs text-oat">
            <tr>
              <th class="px-5 py-2.5 font-normal">名称</th>
              <th class="px-3 py-2.5 font-normal">类型</th>
              <th class="px-3 py-2.5 text-right font-normal">单元</th>
              <th class="px-3 py-2.5 text-right font-normal">题目</th>
              <th class="px-5 py-2.5 text-right font-normal">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="bank in banks" :key="bank.id" class="border-t border-mist">
              <td class="px-5 py-2.5 text-ink">{{ bank.name }}</td>
              <td class="px-3 py-2.5 text-oat">{{ bank.is_public ? "公共" : "私有" }}</td>
              <td class="px-3 py-2.5 text-right">{{ bank.unit_count }}</td>
              <td class="px-3 py-2.5 text-right">{{ bank.question_count }}</td>
              <td class="px-5 py-2.5 text-right">
                <button class="mr-2 text-xs text-moss" @click="setDefaultBank(bank.id)">设为默认</button>
                <button class="text-xs text-rose" @click="deleteBank(bank.id)">删除</button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>

    <section v-else-if="tab === 'units'" class="space-y-4">
      <select v-model.number="selectedBank" class="rounded-xl border border-mist bg-paper px-3 py-2 text-sm">
        <option :value="null" disabled>选择题库</option>
        <option v-for="bank in banks" :key="bank.id" :value="bank.id">{{ bank.name }}</option>
      </select>
      <div class="flex gap-2 rounded-2xl border border-mist bg-white/70 p-4">
        <input v-model="unitForm.name" class="rounded-xl border border-mist bg-paper px-3 py-2 text-sm" placeholder="单元名称" />
        <input v-model.number="unitForm.sort" type="number" class="w-24 rounded-xl border border-mist bg-paper px-3 py-2 text-sm" placeholder="排序" />
        <button class="rounded-xl bg-ink px-4 py-2 text-sm text-white" @click="createUnit">新建单元</button>
      </div>
      <div class="space-y-2">
        <div v-for="unit in units" :key="unit.id" class="flex items-center justify-between rounded-xl border border-mist bg-white/70 px-4 py-3 text-sm">
          <span class="text-ink">{{ unit.name }}</span>
          <span class="text-oat">{{ unit.question_count }} 题</span>
          <button class="text-xs text-rose" @click="deleteUnit(unit.id)">删除</button>
        </div>
      </div>
    </section>

    <section v-else-if="tab === 'questions'" class="space-y-4">
      <div class="flex flex-wrap gap-2">
        <select v-model.number="selectedBank" class="rounded-xl border border-mist bg-paper px-3 py-2 text-sm">
          <option :value="null" disabled>选择题库</option>
          <option v-for="bank in banks" :key="bank.id" :value="bank.id">{{ bank.name }}</option>
        </select>
        <select v-model.number="selectedUnit" class="rounded-xl border border-mist bg-paper px-3 py-2 text-sm">
          <option :value="null">全部单元</option>
          <option v-for="unit in units" :key="unit.id" :value="unit.id">{{ unit.name }}</option>
        </select>
      </div>
      <div class="space-y-2 rounded-2xl border border-mist bg-white/70 p-4">
        <div class="flex gap-2">
          <select v-model="questionForm.type" class="rounded-xl border border-mist bg-paper px-3 py-2 text-sm">
            <option value="SINGLE">单选</option>
            <option value="MULTI">多选</option>
            <option value="SHORT">简答</option>
          </select>
          <select v-model="questionForm.difficulty" class="rounded-xl border border-mist bg-paper px-3 py-2 text-sm">
            <option value="EASY">简单</option>
            <option value="MEDIUM">中等</option>
            <option value="HARD">困难</option>
          </select>
        </div>
        <textarea v-model="questionForm.content" rows="2" class="w-full rounded-xl border border-mist bg-paper px-3 py-2 text-sm" placeholder="题干" />
        <input v-model="questionForm.answer" class="w-full rounded-xl border border-mist bg-paper px-3 py-2 text-sm" placeholder="答案（选择题如 A / AB；简答题为参考答案）" />
        <input v-model="questionForm.options" class="w-full rounded-xl border border-mist bg-paper px-3 py-2 font-mono text-xs" placeholder='选项 JSON' />
        <input v-model="questionForm.keyPoints" class="w-full rounded-xl border border-mist bg-paper px-3 py-2 font-mono text-xs" placeholder='评分要点 JSON' />
        <button class="rounded-xl bg-ink px-4 py-2 text-sm text-white" @click="createQuestion">新增题目</button>
      </div>
      <div class="space-y-2">
        <div v-for="question in questions" :key="question.id" class="rounded-xl border border-mist bg-white/70 p-3 text-sm">
          <p class="whitespace-pre-wrap text-ink">{{ question.content }}</p>
          <div class="mt-2 flex items-center gap-3 text-xs text-oat">
            <span>{{ question.type }} · {{ question.difficulty }} · {{ question.status }}</span>
            <button class="text-moss" @click="toggleQuestion(question)">
              {{ question.status === "ON" ? "下架" : "上架" }}
            </button>
            <button class="text-rose" @click="deleteQuestion(question.id)">删除</button>
          </div>
        </div>
      </div>
    </section>

    <section v-else-if="tab === 'users'" class="overflow-hidden rounded-2xl border border-mist bg-white/70">
      <table class="w-full text-sm">
        <thead class="bg-mist/60 text-left text-xs text-oat">
          <tr>
            <th class="px-5 py-2.5 font-normal">邮箱</th>
            <th class="px-3 py-2.5 font-normal">角色</th>
            <th class="px-3 py-2.5 font-normal">状态</th>
            <th class="px-5 py-2.5 text-right font-normal">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="user in users" :key="user.id" class="border-t border-mist">
            <td class="px-5 py-2.5 text-ink">{{ user.email }}</td>
            <td class="px-3 py-2.5 text-oat">{{ user.role }}</td>
            <td class="px-3 py-2.5 text-oat">{{ user.status }}</td>
            <td class="px-5 py-2.5 text-right">
              <button class="mr-2 text-xs text-moss" @click="toggleRole(user)">切换角色</button>
              <button class="mr-2 text-xs text-moss" @click="toggleUser(user)">启停</button>
              <button class="text-xs text-moss" @click="grant(user)">点数</button>
            </td>
          </tr>
        </tbody>
      </table>
    </section>

    <section v-else-if="tab === 'appeals'" class="space-y-4">
      <select v-model="appealStatus" class="rounded-xl border border-mist bg-paper px-3 py-2 text-sm" @change="loadAppeals">
        <option value="PENDING">待处理</option>
        <option value="RESOLVED">已处理</option>
        <option value="REJECTED">已驳回</option>
        <option value="ALL">全部</option>
      </select>
      <div class="space-y-2">
        <div v-for="appeal in appeals" :key="appeal.id" class="rounded-xl border border-mist bg-white/70 p-4 text-sm">
          <p class="text-xs text-oat">{{ appeal.user_email }} · {{ appeal.verdict }}</p>
          <p class="mt-1 whitespace-pre-wrap text-ink">{{ appeal.question_content }}</p>
          <p class="mt-2 text-ink/75">考生答案：{{ appeal.user_answer }}</p>
          <p class="mt-1 text-ink/75">申诉理由：{{ appeal.reason }}</p>
          <div v-if="appeal.status === 'PENDING'" class="mt-2 flex gap-2">
            <button class="rounded-lg bg-moss px-3 py-1.5 text-xs text-white" @click="resolveAppeal(appeal, 'RESOLVED')">通过</button>
            <button class="rounded-lg border border-mist px-3 py-1.5 text-xs text-ink" @click="resolveAppeal(appeal, 'REJECTED')">驳回</button>
          </div>
        </div>
      </div>
    </section>

    <section v-else-if="tab === 'ai'" class="space-y-4">
      <div class="space-y-4 rounded-2xl border border-mist bg-white/70 p-5">
        <div class="flex items-center justify-between">
          <h2 class="font-medium text-ink">AI 配置</h2>
          <span
            v-if="aiConfig"
            class="rounded-full px-2.5 py-0.5 text-xs"
            :class="aiConfig.status === 'ENABLED' ? 'bg-moss/15 text-moss' : 'bg-rose/15 text-rose'"
          >
            {{ aiConfig.status === "ENABLED" ? "已启用" : "未启用" }}
          </span>
        </div>

        <label class="block">
          <span class="mb-1.5 block text-sm text-ink">配置名称</span>
          <input
            v-model="aiForm.name"
            class="w-full rounded-xl border border-mist bg-paper px-3 py-2 text-sm outline-none focus:border-moss"
            placeholder="DeepSeek 默认"
          />
        </label>

        <label class="block">
          <span class="mb-1.5 block text-sm text-ink">API Key</span>
          <input
            v-model="aiForm.apiKey"
            class="w-full rounded-xl border border-mist bg-paper px-3 py-2 text-sm outline-none focus:border-moss"
            :placeholder="
              aiConfig
                ? `留空表示不修改，当前 ${aiConfig.api_key_masked}`
                : '请输入 DeepSeek API Key'
            "
          />
        </label>

        <label class="block">
          <span class="mb-1.5 block text-sm text-ink">模型</span>
          <input
            v-model="aiForm.model"
            class="w-full rounded-xl border border-mist bg-paper px-3 py-2 text-sm outline-none focus:border-moss"
            placeholder="deepseek-flash"
          />
        </label>

        <div class="flex items-center gap-2">
          <button
            class="rounded-xl bg-ink px-4 py-2 text-sm text-white transition hover:bg-ink/90"
            @click="saveAiConfig"
          >
            保存修改
          </button>
          <button
            class="rounded-xl border border-moss px-4 py-2 text-sm text-moss transition hover:bg-moss/10"
            @click="testAi"
          >
            测试连通
          </button>
        </div>

        <p class="text-xs text-oat">
          Base URL 固定为 https://api.deepseek.com；保存后会自动启用该配置。
        </p>
      </div>
    </section>

    <section v-else-if="tab === 'billing' && billing" class="space-y-6">
      <div class="grid grid-cols-2 gap-3 sm:grid-cols-5">
        <div class="rounded-2xl border border-mist bg-white/70 p-4">
          <p class="text-xs text-oat">账户数</p>
          <p class="mt-1 text-xl font-semibold text-ink">{{ billing.stats.userCount }}</p>
        </div>
        <div class="rounded-2xl border border-mist bg-white/70 p-4">
          <p class="text-xs text-oat">未消费点数</p>
          <p class="mt-1 text-xl font-semibold text-ink">{{ billing.stats.outstandingPoints }}</p>
        </div>
        <div class="rounded-2xl border border-mist bg-white/70 p-4">
          <p class="text-xs text-oat">已消耗点数</p>
          <p class="mt-1 text-xl font-semibold text-ink">{{ billing.stats.consumedPoints }}</p>
        </div>
        <div class="rounded-2xl border border-mist bg-white/70 p-4">
          <p class="text-xs text-oat">已收款</p>
          <p class="mt-1 text-xl font-semibold text-ink">¥{{ (billing.stats.paidRevenueCents / 100).toFixed(2) }}</p>
        </div>
        <div class="rounded-2xl border border-mist bg-white/70 p-4">
          <p class="text-xs text-oat">待处理订单</p>
          <p class="mt-1 text-xl font-semibold text-ink">{{ billing.stats.pendingOrders }}</p>
        </div>
      </div>
      <div class="overflow-hidden rounded-2xl border border-mist bg-white/70">
        <div class="border-b border-mist px-5 py-3 text-sm font-medium text-ink">订单</div>
        <table class="w-full text-sm">
          <thead class="bg-mist/60 text-left text-xs text-oat">
            <tr>
              <th class="px-5 py-2.5 font-normal">类型</th>
              <th class="px-3 py-2.5 font-normal">商品</th>
              <th class="px-3 py-2.5 text-right font-normal">金额</th>
              <th class="px-3 py-2.5 font-normal">状态</th>
              <th class="px-5 py-2.5 text-right font-normal">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="order in billing.orders" :key="order.id" class="border-t border-mist">
              <td class="px-5 py-2.5">{{ order.kind }}</td>
              <td class="px-3 py-2.5">{{ order.item_code }}</td>
              <td class="px-3 py-2.5 text-right">¥{{ (order.amount_cents / 100).toFixed(2) }}</td>
              <td class="px-3 py-2.5">{{ order.status }}</td>
              <td class="px-5 py-2.5 text-right">
                <template v-if="order.status === 'PENDING'">
                  <button class="mr-2 text-xs text-moss" @click="settleOrder(order.id, 'PAID')">已收款</button>
                  <button class="text-xs text-rose" @click="settleOrder(order.id, 'CANCELLED')">取消</button>
                </template>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>
  </div>
</template>
