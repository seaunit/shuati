<script setup lang="ts">
import { ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { Leaf, Loader2 } from "lucide-vue-next";
import { useAuthStore } from "@/stores/auth";

const auth = useAuthStore();
const router = useRouter();
const route = useRoute();

const mode = ref<"login" | "register">("login");
const email = ref("");
const password = ref("");
const confirm = ref("");
const loading = ref(false);
const error = ref("");

async function submit() {
  error.value = "";
  if (!email.value.trim() || !password.value) {
    error.value = "请填写邮箱和密码";
    return;
  }
  if (mode.value === "register" && password.value !== confirm.value) {
    error.value = "两次输入的密码不一致";
    return;
  }
  loading.value = true;
  try {
    if (mode.value === "login") {
      await auth.login(email.value.trim(), password.value);
    } else {
      await auth.register(email.value.trim(), password.value);
    }
    const next = typeof route.query.next === "string" ? route.query.next : "/app";
    await router.push(next);
  } catch (e) {
    error.value = e instanceof Error ? e.message : "操作失败，请重试";
  } finally {
    loading.value = false;
  }
}
</script>

<template>
  <main class="flex min-h-screen items-center justify-center px-6">
    <div class="w-full max-w-md">
      <div class="mb-8 text-center">
        <div class="inline-flex h-12 w-12 items-center justify-center rounded-2xl bg-moss/15 text-moss">
          <Leaf class="h-6 w-6" />
        </div>
        <h1 class="mt-4 text-2xl font-semibold tracking-wide text-ink">拾题</h1>
        <p class="mt-1 text-sm text-oat">安静地练习，温柔地记住</p>
      </div>

      <form
        class="rounded-3xl border border-mist bg-white/70 p-8 shadow-sm backdrop-blur"
        @submit.prevent="submit"
      >
        <div class="mb-6 flex rounded-xl bg-mist p-1">
          <button
            v-for="item in [
              { key: 'login', label: '登录' },
              { key: 'register', label: '注册' },
            ]"
            :key="item.key"
            type="button"
            class="flex-1 rounded-lg py-2 text-sm transition"
            :class="mode === item.key ? 'bg-white text-ink shadow-sm' : 'text-oat'"
            @click="
              mode = item.key as 'login' | 'register';
              error = '';
            "
          >
            {{ item.label }}
          </button>
        </div>

        <label class="mb-5 block">
          <span class="mb-1.5 block text-sm text-ink">邮箱</span>
          <input
            v-model="email"
            type="email"
            class="w-full rounded-xl border border-mist bg-paper px-4 py-2.5 outline-none transition focus:border-moss"
            placeholder="you@example.com"
          />
        </label>

        <label class="mb-5 block">
          <span class="mb-1.5 block text-sm text-ink">密码</span>
          <input
            v-model="password"
            type="password"
            class="w-full rounded-xl border border-mist bg-paper px-4 py-2.5 outline-none transition focus:border-moss"
            placeholder="至少 6 位"
          />
        </label>

        <label v-if="mode === 'register'" class="mb-5 block">
          <span class="mb-1.5 block text-sm text-ink">确认密码</span>
          <input
            v-model="confirm"
            type="password"
            class="w-full rounded-xl border border-mist bg-paper px-4 py-2.5 outline-none transition focus:border-moss"
            placeholder="再次输入密码"
          />
        </label>

        <p v-if="error" class="mb-4 rounded-xl bg-rose/10 px-4 py-2.5 text-sm text-rose">
          {{ error }}
        </p>

        <button
          type="submit"
          :disabled="loading"
          class="flex w-full items-center justify-center gap-2 rounded-xl bg-ink py-2.5 text-sm text-white transition hover:bg-ink/90 disabled:opacity-60"
        >
          <template v-if="loading">
            <Loader2 class="h-4 w-4 animate-spin" />
            请稍候…
          </template>
          <template v-else>{{ mode === "login" ? "登录" : "注册并登录" }}</template>
        </button>
      </form>

      <p class="mt-6 text-center text-xs text-oat">注册即表示你已阅读并同意使用规则</p>
    </div>
  </main>
</template>
