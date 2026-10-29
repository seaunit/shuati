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

export interface PriceRule {
  purpose: string;
  points: number;
  unit: string;
  description: string;
}

export const useBillingStore = defineStore("billing", {
  state: () => ({
    entitlements: null as Entitlements | null,
    rules: [] as PriceRule[],
  }),
  actions: {
    async load() {
      try {
        // 游客没有点数，未登录时静默失败，不要触发跳转登录页
        const data = await api<{ entitlements: Entitlements; rules?: PriceRule[] }>(
          "/api/points",
          {},
          { redirectOn401: false },
        );
        this.entitlements = data.entitlements;
        this.rules = data.rules ?? [];
      } catch {
        this.entitlements = null;
        this.rules = [];
      }
    },
  },
});
