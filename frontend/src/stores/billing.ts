import { defineStore } from "pinia";
import { api } from "@/api/client";

export interface Entitlements {
  planCode: string;
  planName: string;
  planExpiresAt: string | null;
  monthlyQuota: number;
  monthlyUsed: number;
  monthlyLeft: number;
  bonusBalance: number;
  available: number;
  lifetimeUsed: number;
}

export const useBillingStore = defineStore("billing", {
  state: () => ({
    entitlements: null as Entitlements | null,
  }),
  actions: {
    async load() {
      try {
        // 游客没有点数，未登录时静默失败，不要触发跳转登录页
        const data = await api<{ entitlements: Entitlements }>(
          "/api/points",
          {},
          { redirectOn401: false },
        );
        this.entitlements = data.entitlements;
      } catch {
        this.entitlements = null;
      }
    },
  },
});
