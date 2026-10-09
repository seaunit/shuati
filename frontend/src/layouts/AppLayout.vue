<script setup lang="ts">
import { computed, onMounted, ref, watch } from "vue";
import { useRoute } from "vue-router";
import { Coins, Ellipsis, Leaf, LogIn, LogOut, X } from "lucide-vue-next";
import { useAuthStore } from "@/stores/auth";
import { useBillingStore } from "@/stores/billing";
import NavLinks from "@/components/NavLinks.vue";

const route = useRoute();
const auth = useAuthStore();
const billing = useBillingStore();
const moreOpen = ref(false);

const nav = computed(() => [
  { href: "/app", label: "首页", icon: "home" },
  { href: "/app/practice", label: "刷题", icon: "practice" },
  { href: "/app/wrong-book", label: "错题本", icon: "wrong-book" },
  { href: "/app/stats", label: "统计", icon: "stats" },
  { href: "/app/import", label: "导入题库", icon: "practice" },
  { href: "/app/pricing", label: "套餐与点数", icon: "pricing" },
  ...(auth.isAdmin ? [{ href: "/app/admin", label: "后台管理", icon: "admin" }] : []),
]);
const primaryNav = computed(() => nav.value.slice(0, 4));
const secondaryNav = computed(() => nav.value.slice(4));

watch(
  () => route.fullPath,
  () => {
    moreOpen.value = false;
  },
);

async function signOut() {
  await auth.logout();
  window.location.href = "/login";
}

onMounted(() => {
  if (auth.user) {
    billing.load();
  }
});
</script>

<template>
  <div class="min-h-screen lg:flex">
    <header
      class="sticky top-0 z-40 flex h-14 items-center justify-between border-b border-mist/80 bg-paper/90 px-4 backdrop-blur-xl lg:hidden"
    >
      <RouterLink to="/app" class="flex min-w-0 items-center gap-2">
        <span class="flex h-9 w-9 shrink-0 items-center justify-center rounded-xl bg-moss/15 text-moss">
          <Leaf class="h-5 w-5" />
        </span>
        <span class="truncate font-semibold text-ink">拾题</span>
      </RouterLink>

      <div class="flex items-center gap-2">
        <RouterLink
          v-if="!auth.user"
          to="/login"
          class="flex h-9 items-center rounded-full border border-moss px-3 text-xs text-moss transition hover:bg-moss/10"
        >
          登录 / 注册
        </RouterLink>
        <template v-else>
          <RouterLink
            v-if="billing.entitlements"
            to="/app/pricing"
            class="flex h-9 items-center gap-1.5 rounded-full border border-mist bg-white/75 px-3 text-xs text-ink"
          >
            <Coins class="h-3.5 w-3.5 text-moss" />
            <span class="font-medium">{{ billing.entitlements.available }}</span>
            <span class="text-oat">点</span>
          </RouterLink>
          <button
            class="flex h-9 w-9 items-center justify-center rounded-full border border-mist bg-white/75 text-ink"
            aria-label="打开更多菜单"
            @click="moreOpen = true"
          >
            <Ellipsis class="h-5 w-5" />
          </button>
        </template>
      </div>
    </header>

    <aside
      data-testid="desktop-sidebar"
      class="sticky top-0 hidden h-screen w-60 shrink-0 flex-col border-r border-mist bg-white/55 px-4 py-6 backdrop-blur lg:flex"
    >
      <div class="flex items-center gap-2 px-2">
        <span class="flex h-9 w-9 items-center justify-center rounded-xl bg-moss/15 text-moss">
          <Leaf class="h-5 w-5" />
        </span>
        <div class="min-w-0">
          <p class="font-semibold text-ink">拾题</p>
          <p class="truncate text-xs text-oat">{{ auth.user?.email ?? "未登录" }}</p>
        </div>
      </div>

      <div class="mt-3 px-2">
        <span class="inline-flex items-center rounded-full bg-sand/40 px-2.5 py-0.5 text-xs text-oat">
          {{ !auth.user ? "游客模式" : auth.isAdmin ? "管理员" : "普通用户" }}
        </span>
      </div>

      <RouterLink
        v-if="auth.user && billing.entitlements"
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
          v-if="auth.user"
          class="flex w-full items-center gap-3 rounded-xl px-3 py-2 text-sm text-ink/75 transition hover:bg-mist"
          @click="signOut"
        >
          退出登录
        </button>
        <RouterLink
          v-else
          to="/login"
          class="flex w-full items-center gap-3 rounded-xl bg-ink px-3 py-2 text-sm text-white transition hover:bg-ink/90"
        >
          登录 / 注册
        </RouterLink>
      </div>
    </aside>

    <main
      class="min-h-[calc(100vh-3.5rem)] min-w-0 flex-1 overflow-x-hidden px-4 pb-28 pt-5 sm:px-5 lg:min-h-screen lg:p-8"
    >
      <RouterView />
    </main>

    <nav
      data-testid="mobile-nav"
      class="fixed inset-x-3 z-40 grid grid-cols-5 rounded-2xl border border-mist bg-white/95 p-1.5 shadow-[0_10px_35px_rgba(63,74,90,0.14)] backdrop-blur-xl lg:hidden"
      :style="{ bottom: 'max(0.75rem, env(safe-area-inset-bottom))' }"
      aria-label="移动端主导航"
    >
      <NavLinks :nav="primaryNav" variant="bottom" />
      <button
        v-if="auth.user"
        class="flex h-12 min-w-0 flex-col items-center justify-center gap-0.5 rounded-xl px-0.5 text-[10px] leading-none transition"
        :class="moreOpen ? 'bg-moss/15 font-medium text-moss' : 'text-ink/75'"
        @click="moreOpen = true"
      >
        <Ellipsis class="h-[18px] w-[18px]" />
        <span>更多</span>
      </button>
      <RouterLink
        v-else
        to="/login"
        class="flex h-12 min-w-0 flex-col items-center justify-center gap-0.5 rounded-xl px-0.5 text-[10px] leading-none text-moss transition"
      >
        <LogIn class="h-[18px] w-[18px]" />
        <span>登录</span>
      </RouterLink>
    </nav>

    <div v-if="moreOpen" class="fixed inset-0 z-50 lg:hidden">
      <button
        class="absolute inset-0 bg-ink/20 backdrop-blur-[2px]"
        aria-label="关闭更多菜单"
        @click="moreOpen = false"
      />
      <section
        class="absolute inset-x-0 bottom-0 rounded-t-3xl border border-b-0 border-mist bg-paper px-5 pb-[max(1.5rem,env(safe-area-inset-bottom))] pt-4 shadow-2xl"
      >
        <div class="mx-auto mb-4 h-1 w-10 rounded-full bg-mist" />
        <div class="flex items-start justify-between gap-4">
          <div class="min-w-0">
            <p class="truncate font-medium text-ink">{{ auth.user?.email }}</p>
            <p class="mt-1 text-xs text-oat">{{ auth.isAdmin ? "管理员" : "普通用户" }}</p>
          </div>
          <button
            class="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-white/80 text-ink"
            aria-label="关闭更多菜单"
            @click="moreOpen = false"
          >
            <X class="h-4 w-4" />
          </button>
        </div>

        <div class="mt-4 grid gap-1">
          <NavLinks :nav="secondaryNav" />
        </div>

        <button
          class="mt-4 flex w-full items-center gap-3 rounded-xl border border-mist bg-white/70 px-4 py-3 text-left text-sm text-ink"
          @click="signOut"
        >
          <LogOut class="h-4 w-4 text-oat" />
          退出登录
        </button>
      </section>
    </div>
  </div>
</template>
