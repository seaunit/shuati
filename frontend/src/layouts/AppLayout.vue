<script setup lang="ts">
import { computed, onMounted } from "vue";
import { Coins, Leaf } from "lucide-vue-next";
import { useAuthStore } from "@/stores/auth";
import { useBillingStore } from "@/stores/billing";
import NavLinks from "@/components/NavLinks.vue";

const auth = useAuthStore();
const billing = useBillingStore();

const nav = computed(() => [
  { href: "/app", label: "首页", icon: "home" },
  { href: "/app/practice", label: "刷题", icon: "practice" },
  { href: "/app/wrong-book", label: "错题本", icon: "wrong-book" },
  { href: "/app/stats", label: "统计", icon: "stats" },
  { href: "/app/import", label: "导入题库", icon: "practice" },
  { href: "/app/pricing", label: "套餐与点数", icon: "pricing" },
  ...(auth.isAdmin ? [{ href: "/app/admin", label: "后台管理", icon: "admin" }] : []),
]);

async function signOut() {
  await auth.logout();
  window.location.href = "/login";
}

onMounted(() => {
  billing.load();
});
</script>

<template>
  <div class="flex min-h-screen">
    <aside
      class="sticky top-0 flex h-screen w-60 shrink-0 flex-col border-r border-mist bg-white/55 px-4 py-6 backdrop-blur"
    >
      <div class="flex items-center gap-2 px-2">
        <span class="flex h-9 w-9 items-center justify-center rounded-xl bg-moss/15 text-moss">
          <Leaf class="h-5 w-5" />
        </span>
        <div class="min-w-0">
          <p class="font-semibold text-ink">拾题</p>
          <p class="truncate text-xs text-oat">{{ auth.user?.email }}</p>
        </div>
      </div>

      <div class="mt-3 px-2">
        <span class="inline-flex items-center rounded-full bg-sand/40 px-2.5 py-0.5 text-xs text-oat">
          {{ auth.isAdmin ? "管理员" : "普通用户" }}
        </span>
      </div>

      <RouterLink
        v-if="billing.entitlements"
        to="/app/pricing"
        class="mt-3 flex items-center justify-between rounded-xl border border-mist bg-white/70 px-3 py-2 transition hover:border-moss"
      >
        <span class="flex items-center gap-2 text-xs text-oat">
          <Coins class="h-3.5 w-3.5 text-moss" />
          {{ billing.entitlements.planName }}
        </span>
        <span class="text-sm font-medium text-ink">{{ billing.entitlements.available }} 点</span>
      </RouterLink>

      <nav class="mt-6 flex-1 space-y-1">
        <NavLinks :nav="nav" />
      </nav>

      <div class="px-2">
        <button
          class="flex w-full items-center gap-3 rounded-xl px-3 py-2 text-sm text-ink/75 transition hover:bg-mist"
          @click="signOut"
        >
          退出登录
        </button>
      </div>
    </aside>

    <main class="flex-1 overflow-x-hidden p-6 lg:p-8">
      <RouterView />
    </main>
  </div>
</template>
