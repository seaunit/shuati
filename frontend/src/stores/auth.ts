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
        this.user = await api<User>("/api/me");
      } catch {
        this.user = null;
      } finally {
        this.loaded = true;
      }
    },
    async login(email: string, password: string) {
      this.user = await api<User>("/api/auth/login", {
        method: "POST",
        body: JSON.stringify({ email, password }),
      });
      this.loaded = true;
    },
    async register(email: string, password: string) {
      this.user = await api<User>("/api/auth/register", {
        method: "POST",
        body: JSON.stringify({ email, password }),
      });
      this.loaded = true;
    },
    async logout() {
      await api("/api/auth/logout", { method: "POST" });
      this.user = null;
      this.loaded = true;
    },
  },
});
