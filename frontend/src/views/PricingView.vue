<script setup lang="ts">
import { onMounted, ref } from "vue";
import { Check, Sparkles } from "lucide-vue-next";
import { api } from "@/api/client";

type Period = "MONTHLY" | "QUARTERLY" | "YEARLY";

interface Plan {
  code: string;
  name: string;
  tagline: string | null;
  description: string | null;
  price_monthly_cents: number;
  price_quarterly_cents: number;
  price_yearly_cents: number;
  monthly_points: number;
  max_banks: number | null;
  max_questions: number | null;
  features: string[];
  currency: string;
}

interface Pack {
  code: string;
  name: string;
  price_cents: number;
  points: number;
  bonus_points: number;
  currency: string;
}

interface Rule {
  purpose: string;
  points: number;
  unit: string;
}

interface Entitlements {
  planCode: string;
  planName: string;
  monthlyQuota: number;
  monthlyLeft: number;
  bonusBalance: number;
  available: number;
}

interface Ledger {
  id: number;
  delta: number;
  balance_after: number;
  reason: string;
  created_at: string;
}

interface Order {
  id: string;
  kind: string;
  item_code: string;
  period: string | null;
  amount_cents: number;
  status: string;
  created_at: string;
}

const PERIODS: { key: Period; label: string; suffix: string }[] = [
  { key: "MONTHLY", label: "月付", suffix: "/月" },
  { key: "QUARTERLY", label: "季付", suffix: "/季" },
  { key: "YEARLY", label: "年付", suffix: "/年" },
];

const PURPOSE_LABEL: Record<string, string> = {
  JUDGE: "简答题 / 伪代码 AI 判分",
  EXPLAIN: "选择题 AI 解析",
  EXTRACT: "AI 解析生成题库",
  TEST: "连通性测试",
};

const REASON_LABEL: Record<string, string> = {
  SIGNUP_BONUS: "注册赠送",
  MONTHLY_GRANT: "月度额度发放",
  PLAN_CHANGE: "套餐变更",
  PACK_PURCHASE: "加量包到账",
  ADMIN_ADJUST: "管理员调整",
  AI_JUDGE: "AI 判分",
  AI_EXPLAIN: "AI 解析",
  AI_EXTRACT: "AI 生成题库",
  AI_JUDGE_FAILED: "判分失败退回",
  AI_EXPLAIN_FAILED: "解析失败退回",
  AI_EXTRACT_FAILED: "生成失败退回",
  AI_EXTRACT_PARTIAL_FAILED: "部分内容失败退回",
};

const period = ref<Period>("YEARLY");
const plans = ref<Plan[]>([]);
const packs = ref<Pack[]>([]);
const rules = ref<Rule[]>([]);
const entitlements = ref<Entitlements | null>(null);
const ledger = ref<Ledger[]>([]);
const orders = ref<Order[]>([]);
const notice = ref("");
const busy = ref("");

function money(cents: number, currency: string) {
  if (!cents) return "定制";
  const value = cents / 100;
  const symbol = currency === "USD" ? "$" : currency === "CNY" ? "¥" : `${currency} `;
  return symbol + (Number.isInteger(value) ? value : value.toFixed(2));
}

function priceOf(plan: Plan) {
  if (period.value === "MONTHLY") return plan.price_monthly_cents;
  if (period.value === "QUARTERLY") return plan.price_quarterly_cents;
  return plan.price_yearly_cents;
}

async function load() {
  const [catalog, points, myOrders] = await Promise.all([
    api<{ plans: Plan[]; packs: Pack[]; rules: Rule[] }>("/api/plans"),
    api<{ entitlements: Entitlements; ledger: Ledger[] }>("/api/points"),
    api<Order[]>("/api/orders"),
  ]);
  plans.value = catalog.plans ?? [];
  packs.value = catalog.packs ?? [];
  rules.value = catalog.rules ?? [];
  entitlements.value = points.entitlements;
  ledger.value = points.ledger ?? [];
  orders.value = myOrders ?? [];
}

async function buy(kind: "PLAN" | "PACK", itemCode: string) {
  busy.value = kind + ":" + itemCode;
  notice.value = "";
  try {
    const order = await api<{ checkoutUrl?: string; checkoutError?: string }>("/api/orders", {
      method: "POST",
      body: JSON.stringify({ kind, itemCode, period: kind === "PLAN" ? period.value : null }),
    });
    await load();
    if (order?.checkoutUrl) {
      // 按官方建议：新标签页打开收银台，保留当前页面状态
      window.open(order.checkoutUrl, "_blank", "noopener,noreferrer");
      notice.value = "已打开收银台，完成支付后会自动开通。";
    } else if (order?.checkoutError) {
      notice.value = `订单已创建，但收银台创建失败：${order.checkoutError}`;
    } else {
      notice.value = "订单已创建。当前尚未接入在线支付，请联系管理员确认收款后开通。";
    }
  } catch (e) {
    notice.value = e instanceof Error ? e.message : "下单失败";
  } finally {
    busy.value = "";
  }
}

onMounted(() => {
  load().catch(() => (notice.value = "加载失败，请刷新重试"));
});
</script>

<template>
  <div class="w-full space-y-8">
    <header>
      <h1 class="text-2xl font-semibold tracking-tight text-ink">套餐与点数</h1>
      <p class="mt-1 text-sm text-oat">公共题库与选择题判分永久免费，AI 能力按点数计费</p>
    </header>

    <section v-if="entitlements" class="grid gap-4 rounded-2xl border border-mist bg-white/70 p-5 sm:grid-cols-3">
      <div>
        <p class="text-xs text-oat">当前套餐</p>
        <p class="mt-1 font-medium text-ink">{{ entitlements.planName }}</p>
      </div>
      <div>
        <p class="text-xs text-oat">可用点数</p>
        <p class="mt-1 font-medium text-ink">{{ entitlements.available }} 点</p>
      </div>
      <div>
        <p class="text-xs text-oat">加量包余额</p>
        <p class="mt-1 font-medium text-ink">{{ entitlements.bonusBalance }} 点</p>
      </div>
    </section>

    <p v-if="notice" class="rounded-xl bg-moss/10 px-4 py-3 text-sm text-ink">{{ notice }}</p>

    <section v-if="plans.length > 0">
      <div class="mb-4 flex flex-col items-start justify-between gap-3 sm:flex-row sm:items-center">
        <h2 class="font-medium text-ink">选择套餐</h2>
        <div class="grid w-full grid-cols-3 rounded-xl bg-mist p-1 sm:w-auto">
          <button
            v-for="item in PERIODS"
            :key="item.key"
            class="rounded-lg px-3 py-1.5 text-sm transition"
            :class="period === item.key ? 'bg-white text-ink shadow-sm' : 'text-oat'"
            @click="period = item.key"
          >
            {{ item.label }}
          </button>
        </div>
      </div>

      <div class="grid gap-4 lg:grid-cols-4">
        <div
          v-for="plan in plans"
          :key="plan.code"
          class="flex flex-col rounded-2xl border bg-white/70 p-5"
          :class="entitlements?.planCode === plan.code ? 'border-moss' : 'border-mist'"
        >
          <div class="flex items-center justify-between">
            <h3 class="font-medium text-ink">{{ plan.name }}</h3>
            <span
              v-if="entitlements?.planCode === plan.code"
              class="rounded-full bg-moss/15 px-2 py-0.5 text-xs text-moss"
            >
              当前
            </span>
          </div>
          <p class="mt-1 text-xs text-oat">{{ plan.tagline ?? plan.description ?? "" }}</p>
          <p class="mt-4 text-2xl font-semibold text-ink">
            {{ money(priceOf(plan), plan.currency) }}
            <span v-if="priceOf(plan) > 0" class="text-sm font-normal text-oat">
              {{ PERIODS.find((p) => p.key === period)?.suffix }}
            </span>
          </p>
          <ul class="mt-4 flex-1 space-y-2 text-sm text-ink/80">
            <li v-for="feature in plan.features ?? []" :key="feature" class="flex gap-2">
              <Check class="mt-0.5 h-3.5 w-3.5 shrink-0 text-moss" />
              <span>{{ feature }}</span>
            </li>
            <li class="flex gap-2 text-xs text-oat">
              <Check class="mt-0.5 h-3.5 w-3.5 shrink-0 text-oat" />
              <span>
                题库上限 {{ plan.max_banks === null ? "不限" : plan.max_banks + " 个" }} /
                题量上限 {{ plan.max_questions === null ? "不限" : plan.max_questions + " 题" }}
              </span>
            </li>
          </ul>
          <button
            class="mt-5 w-full rounded-xl bg-ink py-2 text-sm text-white transition hover:bg-ink/90 disabled:opacity-50"
            :disabled="entitlements?.planCode === plan.code || priceOf(plan) <= 0 || busy !== ''"
            @click="buy('PLAN', plan.code)"
          >
            {{
              entitlements?.planCode === plan.code
                ? "使用中"
                : priceOf(plan) <= 0
                  ? "联系管理员"
                  : busy === "PLAN:" + plan.code
                    ? "提交中…"
                    : "选择该套餐"
            }}
          </button>
        </div>
      </div>
    </section>

    <section>
      <h2 class="mb-4 font-medium text-ink">加量包</h2>
      <div class="grid gap-4 sm:grid-cols-3">
        <div
          v-for="pack in packs"
          :key="pack.code"
          class="flex items-center justify-between gap-4 rounded-2xl border border-mist bg-white/70 p-4 sm:p-5"
        >
          <div>
            <p class="font-medium text-ink">{{ pack.name }}</p>
            <p class="mt-1 text-sm text-oat">{{ pack.points + pack.bonus_points }} 点</p>
            <p class="mt-0.5 text-xs text-oat">点数长期有效，不随周期重置</p>
          </div>
          <button
            class="shrink-0 rounded-xl border border-ink px-3 py-1.5 text-sm text-ink transition hover:bg-ink hover:text-white disabled:opacity-50"
            :disabled="busy !== ''"
            @click="buy('PACK', pack.code)"
          >
            {{ money(pack.price_cents, pack.currency) }}
          </button>
        </div>
      </div>
    </section>

    <section>
      <h2 class="mb-4 font-medium text-ink">AI 点数消耗</h2>
      <div class="overflow-x-auto rounded-2xl border border-mist bg-white/70">
        <table class="w-full min-w-[30rem] text-sm">
          <thead class="bg-mist/60 text-left text-xs text-oat">
            <tr>
              <th class="px-4 py-2.5 font-normal">功能</th>
              <th class="px-4 py-2.5 font-normal">消耗</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="rule in rules" :key="rule.purpose" class="border-t border-mist">
              <td class="px-4 py-2.5 text-ink">{{ PURPOSE_LABEL[rule.purpose] ?? rule.purpose }}</td>
              <td class="px-4 py-2.5 text-ink/80">
                <span class="inline-flex items-center gap-1.5">
                  <Sparkles class="h-3.5 w-3.5 text-moss" />
                  {{ rule.unit === "PER_10K_CHARS" ? rule.points + " 点 / 1 万字" : rule.points + " 点 / 次" }}
                </span>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <p class="mt-2 text-xs text-oat">选择题判分在本地完成，不消耗点数；AI 调用失败会自动退回点数。</p>
    </section>

    <section class="grid gap-6 lg:grid-cols-2">
      <div>
        <h2 class="mb-4 font-medium text-ink">我的订单</h2>
        <p v-if="orders.length === 0" class="rounded-2xl border border-mist bg-white/70 px-4 py-6 text-center text-sm text-oat">
          暂无订单
        </p>
        <div v-else class="space-y-2">
          <div
            v-for="order in orders"
            :key="order.id"
            class="flex items-center justify-between rounded-xl border border-mist bg-white/70 px-4 py-3 text-sm"
          >
            <div>
              <p class="text-ink">
                {{ order.kind === "PACK" ? "加量包" : "套餐" }} · {{ order.item_code }}
                {{ order.period ? " · " + order.period : "" }}
              </p>
              <p class="mt-0.5 text-xs text-oat">{{ new Date(order.created_at).toLocaleString("zh-CN") }}</p>
            </div>
            <div class="text-right">
              <p class="text-ink">{{ money(order.amount_cents, order.kind === "PLAN" ? "USD" : "CNY") }}</p>
              <p class="mt-0.5 text-xs text-oat">
                {{
                  order.status === "PAID"
                    ? "已开通"
                    : order.status === "PENDING"
                      ? "待确认收款"
                      : order.status === "CANCELLED"
                        ? "已取消"
                        : "已退款"
                }}
              </p>
            </div>
          </div>
        </div>
      </div>

      <div>
        <h2 class="mb-4 font-medium text-ink">点数明细</h2>
        <p v-if="ledger.length === 0" class="rounded-2xl border border-mist bg-white/70 px-4 py-6 text-center text-sm text-oat">
          暂无记录
        </p>
        <div v-else class="max-h-80 space-y-2 overflow-y-auto pr-1">
          <div
            v-for="item in ledger"
            :key="item.id"
            class="flex items-center justify-between rounded-xl border border-mist bg-white/70 px-4 py-2.5 text-sm"
          >
            <div>
              <p class="text-ink">{{ REASON_LABEL[item.reason] ?? item.reason }}</p>
              <p class="mt-0.5 text-xs text-oat">{{ new Date(item.created_at).toLocaleString("zh-CN") }}</p>
            </div>
            <div class="text-right">
              <p :class="item.delta >= 0 ? 'text-moss' : 'text-ink'">
                {{ item.delta >= 0 ? "+" : "" }}{{ item.delta }}
              </p>
              <p class="mt-0.5 text-xs text-oat">余额 {{ item.balance_after }}</p>
            </div>
          </div>
        </div>
      </div>
    </section>
  </div>
</template>
