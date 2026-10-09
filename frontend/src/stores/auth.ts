import { defineStore } from "pinia";
import { api } from "@/api/client";

export interface User {
  id: string;
  email: string;
  nickname: string | null;
  role: string;
  status: string;
}

export const useAuthStore = defineStore("auth", {
  state: () => ({
    user: null as User | null,
    loaded: false,
  }),
  getters: {
    isAdmin: (state) => state.user?.role === "ADMIN",
  },
  actions: {
    async load() {
      try {
        // 未登录是游客模式的正常状态，不能触发跳转登录页
        this.user = await api<User>("/api/me", {}, { redirectOn401: false });
      } catch {
        this.user = null;
      } finally {
        this.loaded = true;
      }
    },
    async login(
      email: string,
      password: string,
      captcha: { ticket: string; code: string },
    ) {
      this.user = await api<User>("/api/auth/login", {
        method: "POST",
        body: JSON.stringify({
          email,
          password,
          captchaTicket: captcha.ticket,
          captchaCode: captcha.code,
        }),
      });
      this.loaded = true;
    },
    async register(
      email: string,
      password: string,
      emailCode: string,
    ) {
      this.user = await api<User>("/api/auth/register", {
        method: "POST",
        body: JSON.stringify({
          email,
          password,
          emailCode,
        }),
      });
      this.loaded = true;
    },
    async resetPassword(email: string, emailCode: string, newPassword: string) {
      await api<null>("/api/auth/password-reset", {
        method: "POST",
        body: JSON.stringify({ email, emailCode, newPassword }),
      });
    },
    async logout() {
      await api("/api/auth/logout", { method: "POST" });
      this.user = null;
      this.loaded = true;
    },
  },
});
