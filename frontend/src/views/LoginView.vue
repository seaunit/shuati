<script setup lang="ts">
import { onMounted, onUnmounted, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { Leaf, Loader2 } from "lucide-vue-next";
import { useAuthStore } from "@/stores/auth";
import { api } from "@/api/client";

const auth = useAuthStore();
const router = useRouter();
const route = useRoute();

const mode = ref<"login" | "register" | "forgot">("login");
const email = ref("");
const password = ref("");
const confirm = ref("");
const loading = ref(false);
const error = ref("");
const captchaTicket = ref("");
const captchaImage = ref("");
const captchaCode = ref("");
const captchaLoading = ref(false);
const emailCode = ref("");
const cooldown = ref(0);
const sendingCode = ref(false);
const notice = ref("");
let cooldownTimer: number | undefined;

async function loadCaptcha() {
  captchaLoading.value = true;
  try {
    const data = await api<{ ticket: string; imageBase64: string }>("/captcha/image");
    captchaTicket.value = data.ticket;
    captchaImage.value = data.imageBase64;
    captchaCode.value = "";
  } catch {
    captchaImage.value = "";
  } finally {
    captchaLoading.value = false;
  }
}

async function submit() {
  error.value = "";
  notice.value = "";
  if (!email.value.trim() || !password.value) {
    error.value = "请填写邮箱和密码";
    return;
  }
  if ((mode.value === "register" || mode.value === "forgot")
      && password.value !== confirm.value) {
    error.value = "两次输入的密码不一致";
    return;
  }
  if (mode.value === "login" && !captchaCode.value.trim()) {
    error.value = "请输入验证码";
    return;
  }
  if ((mode.value === "register" || mode.value === "forgot")
      && !emailCode.value.trim()) {
    error.value = "请输入邮箱验证码";
    return;
  }
  loading.value = true;
  try {
    const captcha = { ticket: captchaTicket.value, code: captchaCode.value.trim() };
    if (mode.value === "login") {
      await auth.login(email.value.trim(), password.value, captcha);
    } else if (mode.value === "forgot") {
      await auth.resetPassword(
        email.value.trim(),
        emailCode.value.trim(),
        password.value,
      );
      mode.value = "login";
      password.value = "";
      confirm.value = "";
      emailCode.value = "";
      notice.value = "密码已重置，请使用新密码登录";
      return;
    } else {
      await auth.register(email.value.trim(), password.value, emailCode.value.trim());
    }
    const next = typeof route.query.next === "string" ? route.query.next : "/app";
    await router.push(next);
  } catch (e) {
    error.value = e instanceof Error ? e.message : "操作失败，请重试";
    // 验证码一次性：失败后必须换一张，避免用户拿旧码反复试
    await loadCaptcha();
  } finally {
    loading.value = false;
  }
}

async function sendEmailCode() {
  error.value = "";
  notice.value = "";
  const requestedEmail = email.value.trim();
  if (!requestedEmail) {
    error.value = "请先填写邮箱";
    return;
  }
  if (!captchaCode.value.trim()) {
    error.value = "请先输入图中字符";
    return;
  }

  sendingCode.value = true;
  const path = mode.value === "forgot"
    ? "/api/auth/password-reset/email-code"
    : "/api/auth/register/email-code";
  try {
    const data = await api<{ cooldownSeconds: number; expiresInSeconds: number }>(
      path,
      {
        method: "POST",
        body: JSON.stringify({
          email: requestedEmail,
          captchaTicket: captchaTicket.value,
          captchaCode: captchaCode.value.trim(),
        }),
      },
    );
    if (email.value.trim() !== requestedEmail) {
      return;
    }
    emailCode.value = "";
    startCooldown(data.cooldownSeconds);
    await loadCaptcha();
  } catch (e) {
    if (email.value.trim() === requestedEmail) {
      const message = e instanceof Error ? e.message : "验证码发送失败";
      error.value = message;
      const seconds = Number(message.match(/请\s*(\d+)\s*秒后/)?.[1] ?? 0);
      if (seconds > 0) {
        startCooldown(seconds);
      }
    }
    await loadCaptcha();
  } finally {
    sendingCode.value = false;
  }
}

function startCooldown(seconds: number) {
  cooldown.value = Math.max(0, seconds);
  if (cooldownTimer) window.clearInterval(cooldownTimer);
  if (cooldown.value <= 0) {
    cooldownTimer = undefined;
    return;
  }
  cooldownTimer = window.setInterval(() => {
    cooldown.value -= 1;
    if (cooldown.value <= 0) {
      cooldown.value = 0;
      window.clearInterval(cooldownTimer);
      cooldownTimer = undefined;
    }
  }, 1000);
}

function resetEmailCode() {
  emailCode.value = "";
  cooldown.value = 0;
  if (cooldownTimer) {
    window.clearInterval(cooldownTimer);
    cooldownTimer = undefined;
  }
}

onMounted(loadCaptcha);
onUnmounted(() => {
  if (cooldownTimer) window.clearInterval(cooldownTimer);
});
watch(mode, () => {
  resetEmailCode();
  loadCaptcha();
});
watch(email, resetEmailCode);
</script>

<template>
  <main class="flex min-h-screen items-center justify-center px-4 py-8 sm:px-6">
    <div class="w-full max-w-md">
      <div class="mb-8 text-center">
        <div class="inline-flex h-12 w-12 items-center justify-center rounded-2xl bg-moss/15 text-moss">
          <Leaf class="h-6 w-6" />
        </div>
        <h1 class="mt-4 text-2xl font-semibold tracking-wide text-ink">拾题</h1>
        <p class="mt-1 text-sm text-oat">安静地练习，温柔地记住</p>
      </div>

      <form
        class="rounded-3xl border border-mist bg-white/70 p-5 shadow-sm backdrop-blur sm:p-8"
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
              notice = '';
            "
          >
            {{ item.label }}
          </button>
        </div>

        <button
          v-if="mode !== 'forgot'"
          type="button"
          class="-mt-3 mb-5 block text-sm text-moss transition hover:text-moss/80"
          @click="
            mode = 'forgot';
            error = '';
            notice = '';
          "
        >
          忘记密码
        </button>

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
          <span class="mb-1.5 block text-sm text-ink">
            {{ mode === "forgot" ? "新密码" : "密码" }}
          </span>
          <input
            v-model="password"
            type="password"
            class="w-full rounded-xl border border-mist bg-paper px-4 py-2.5 outline-none transition focus:border-moss"
            placeholder="至少 6 位"
          />
        </label>

        <label v-if="mode === 'register' || mode === 'forgot'" class="mb-5 block">
          <span class="mb-1.5 block text-sm text-ink">
            {{ mode === "forgot" ? "确认新密码" : "确认密码" }}
          </span>
          <input
            v-model="confirm"
            type="password"
            class="w-full rounded-xl border border-mist bg-paper px-4 py-2.5 outline-none transition focus:border-moss"
            placeholder="再次输入密码"
          />
        </label>

        <label class="mb-5 block">
          <span class="mb-1.5 block text-sm text-ink">验证码</span>
          <div class="flex items-center gap-3">
            <input
              v-model="captchaCode"
              maxlength="5"
              autocomplete="off"
              class="w-full flex-1 rounded-xl border border-mist bg-paper px-4 py-2.5 uppercase tracking-widest outline-none transition focus:border-moss"
              placeholder="请输入图中字符"
            />
            <button
              type="button"
              class="shrink-0 overflow-hidden rounded-xl border border-mist bg-paper transition hover:border-moss"
              title="点击刷新验证码"
              @click="loadCaptcha"
            >
              <img
                v-if="captchaImage"
                :src="captchaImage"
                alt="验证码"
                class="block h-[44px] w-[110px] object-cover sm:w-[120px]"
              />
              <span
                v-else
                class="flex h-[44px] w-[110px] items-center justify-center text-xs text-oat sm:w-[120px]"
              >
                {{ captchaLoading ? "加载中" : "点击刷新" }}
              </span>
            </button>
          </div>
        </label>

        <label v-if="mode === 'register' || mode === 'forgot'" class="mb-5 block">
          <span class="mb-1.5 block text-sm text-ink">邮箱验证码</span>
          <div class="flex items-center gap-3">
            <input
              v-model="emailCode"
              maxlength="6"
              inputmode="numeric"
              autocomplete="one-time-code"
              class="w-full flex-1 rounded-xl border border-mist bg-paper px-4 py-2.5 tracking-widest outline-none transition focus:border-moss"
              placeholder="6 位数字"
            />
            <button
              type="button"
              :disabled="cooldown > 0 || sendingCode"
              class="shrink-0 rounded-xl border border-moss px-4 py-2.5 text-sm text-moss transition hover:bg-moss/10 disabled:opacity-50"
              @click="sendEmailCode"
            >
              {{ cooldown > 0 ? `${cooldown} 秒后重发` : sendingCode ? "发送中…" : "发送验证码" }}
            </button>
          </div>
        </label>

        <p v-if="error" class="mb-4 rounded-xl bg-rose/10 px-4 py-2.5 text-sm text-rose">
          {{ error }}
        </p>
        <p v-if="notice" class="mb-4 rounded-xl bg-moss/10 px-4 py-2.5 text-sm text-ink">
          {{ notice }}
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
          <template v-else>
            {{ mode === "login" ? "登录" : mode === "register" ? "注册并登录" : "重置密码" }}
          </template>
        </button>
      </form>

      <p class="mt-6 text-center text-xs text-oat">注册即表示你已阅读并同意使用规则</p>
    </div>
  </main>
</template>
