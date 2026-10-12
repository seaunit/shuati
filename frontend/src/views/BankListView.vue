<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { Loader2, Lock, Plus, Settings } from "lucide-vue-next";
import { api } from "@/api/client";
import { useAuthStore } from "@/stores/auth";

interface Bank {
  id: number;
  name: string;
  description: string | null;
  is_public: boolean;
  unit_count: number;
  question_count: number;
}

const auth = useAuthStore();
const banks = ref<Bank[]>([]);
const loading = ref(true);
const busy = ref(false);
const creating = ref(false);
const newBankName = ref("");
const newBankDescription = ref("");
const error = ref("");

// 列表本身就只返回「我有权限维护的题库」，所以这里不再二次过滤
const ownBanks = computed(() => banks.value.filter((bank) => !bank.is_public));
const publicBanks = computed(() => banks.value.filter((bank) => bank.is_public));

async function load() {
  loading.value = true;
  error.value = "";
  try {
    banks.value = await api<Bank[]>("/api/manage/banks");
  } catch (e) {
    error.value = e instanceof Error ? e.message : "加载失败";
  } finally {
    loading.value = false;
  }
}

async function createBank() {
  const name = newBankName.value.trim();
  if (!name) {
    error.value = "请填写题库名称";
    return;
  }
  busy.value = true;
  error.value = "";
  try {
    const created = await api<{ id: number }>("/api/manage/banks", {
      method: "POST",
      body: JSON.stringify({
        name,
        description: newBankDescription.value.trim() || null,
      }),
    });
    creating.value = false;
    newBankName.value = "";
    newBankDescription.value = "";
    window.location.href = `/app/banks/${created.id}`;
  } catch (e) {
    error.value = e instanceof Error ? e.message : "新建题库失败";
  } finally {
    busy.value = false;
  }
}

onMounted(load);
</script>

<template>
  <div class="w-full">
    <header class="mb-6 flex flex-wrap items-start justify-between gap-3">
      <div class="min-w-0">
        <h1 class="text-2xl font-semibold tracking-tight text-ink">题库管理</h1>
        <p class="mt-1 text-sm text-oat">
          {{ auth.isAdmin ? "自己的题库，以及公共题库" : "管理自己的题库：导入题目或手工新增题目" }}
        </p>
      </div>
      <button
        v-if="!creating"
        class="flex shrink-0 items-center gap-1.5 rounded-xl bg-ink px-4 py-2 text-sm text-white transition hover:bg-ink/90"
        @click="creating = true"
      >
        <Plus class="h-4 w-4" />
        新建空题库
      </button>
    </header>

    <p v-if="error" class="mb-4 rounded-xl bg-rose/10 px-4 py-2.5 text-sm text-rose">{{ error }}</p>

    <section v-if="creating" class="mb-6 rounded-2xl border border-mist bg-white/70 p-4 sm:p-5">
      <h2 class="mb-3 font-medium text-ink">新建空题库</h2>
      <div class="grid gap-3 sm:grid-cols-2">
        <label class="block">
          <span class="mb-1.5 block text-sm text-ink">名称</span>
          <input
            v-model="newBankName"
            placeholder="例如：计算机网络-冲刺"
            class="w-full rounded-xl border border-mist bg-paper px-4 py-2.5 text-sm outline-none focus:border-moss"
            @keyup.enter="createBank"
          />
        </label>
        <label class="block">
          <span class="mb-1.5 block text-sm text-ink">描述（可选）</span>
          <input
            v-model="newBankDescription"
            class="w-full rounded-xl border border-mist bg-paper px-4 py-2.5 text-sm outline-none focus:border-moss"
          />
        </label>
      </div>
      <p class="mt-2 text-xs text-oat">建好之后可以往里面导入题目，也可以手工新增题目。</p>
      <div class="mt-4 flex flex-wrap gap-2">
        <button
          class="flex items-center gap-1.5 rounded-xl bg-ink px-4 py-2 text-sm text-white transition hover:bg-ink/90 disabled:opacity-50"
          :disabled="busy"
          @click="createBank"
        >
          <Loader2 v-if="busy" class="h-4 w-4 animate-spin" />
          <Plus v-else class="h-4 w-4" />
          创建
        </button>
        <button
          class="rounded-xl border border-mist px-4 py-2 text-sm text-ink transition hover:border-moss"
          @click="creating = false; newBankName = ''; newBankDescription = '';"
        >
          取消
        </button>
      </div>
    </section>

    <div v-if="loading" class="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
      <div
        v-for="i in 3"
        :key="i"
        class="h-32 animate-pulse rounded-2xl border border-mist bg-white/60"
      />
    </div>

    <template v-else-if="banks.length === 0">
      <div class="rounded-2xl border border-mist bg-white/70 p-10 text-center">
        <p class="text-sm text-oat">还没有自己的题库</p>
        <button
          v-if="!creating"
          class="mt-4 inline-flex items-center gap-1.5 rounded-xl bg-ink px-4 py-2 text-sm text-white transition hover:bg-ink/90"
          @click="creating = true"
        >
          <Plus class="h-4 w-4" />
          新建空题库
        </button>
      </div>
    </template>

    <template v-else>
      <section>
        <h2 class="mb-3 font-medium text-ink">我的题库</h2>
        <div class="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          <RouterLink
            v-for="bank in ownBanks"
            :key="bank.id"
            :to="`/app/banks/${bank.id}`"
            class="rounded-2xl border border-mist bg-white/70 p-4 transition hover:border-moss"
          >
            <div class="flex items-start justify-between gap-2">
              <p class="min-w-0 truncate font-medium text-ink">{{ bank.name }}</p>
              <Settings class="h-4 w-4 shrink-0 text-oat" />
            </div>
            <p class="mt-1 line-clamp-2 min-h-[2.5rem] text-xs text-oat">
              {{ bank.description || "暂无描述" }}
            </p>
            <p class="mt-3 text-xs text-oat">
              {{ bank.unit_count }} 个单元 · {{ bank.question_count }} 道题
            </p>
          </RouterLink>
        </div>
        <p v-if="ownBanks.length === 0" class="text-sm text-oat">
          还没有自己的题库，先新建一个空题库再往里导入题目。
        </p>
      </section>

      <section v-if="publicBanks.length" class="mt-8">
        <h2 class="mb-3 font-medium text-ink">公共题库</h2>
        <p class="mb-3 flex items-center gap-1.5 text-xs text-oat">
          <Lock class="h-3.5 w-3.5" />
          公共题库由管理员维护
        </p>
        <div class="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          <RouterLink
            v-for="bank in publicBanks"
            :key="bank.id"
            :to="`/app/banks/${bank.id}`"
            class="rounded-2xl border border-mist bg-white/70 p-4 transition hover:border-moss"
          >
            <p class="min-w-0 truncate font-medium text-ink">{{ bank.name }}</p>
            <p class="mt-1 line-clamp-2 min-h-[2.5rem] text-xs text-oat">
              {{ bank.description || "暂无描述" }}
            </p>
            <p class="mt-3 text-xs text-oat">
              {{ bank.unit_count }} 个单元 · {{ bank.question_count }} 道题
            </p>
          </RouterLink>
        </div>
      </section>
    </template>
  </div>
</template>
