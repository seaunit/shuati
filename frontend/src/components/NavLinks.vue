<script setup lang="ts">
import { useRoute } from "vue-router";
import {
  Home,
  Dumbbell,
  BookX,
  BarChart3,
  Settings,
  Coins,
  type LucideIcon,
} from "lucide-vue-next";

withDefaults(
  defineProps<{
    nav: { href: string; label: string; icon: string }[];
    variant?: "sidebar" | "bottom";
  }>(),
  { variant: "sidebar" },
);

const route = useRoute();

const icons: Record<string, LucideIcon> = {
  home: Home,
  practice: Dumbbell,
  "wrong-book": BookX,
  stats: BarChart3,
  pricing: Coins,
  admin: Settings,
};

function isActive(href: string) {
  if (href === "/app") {
    return route.path === "/app";
  }
  return route.path === href || route.path.startsWith(href + "/");
}
</script>

<template>
  <RouterLink
    v-for="item in nav"
    :key="item.href"
    :to="item.href"
    class="transition"
    :class="[
      isActive(item.href) ? 'bg-moss/15 font-medium text-moss' : 'text-ink/75 hover:bg-mist',
      variant === 'bottom'
        ? 'flex h-12 min-w-0 flex-col items-center justify-center gap-0.5 rounded-xl px-0.5 text-[10px] leading-none'
        : 'flex items-center gap-3 rounded-xl px-3 py-2 text-sm',
    ]"
  >
    <component
      :is="icons[item.icon] ?? Home"
      :class="[
        variant === 'bottom' ? 'h-[18px] w-[18px]' : 'h-4 w-4',
        isActive(item.href) ? 'text-moss' : 'text-oat',
      ]"
    />
    <span class="truncate">{{ item.label }}</span>
  </RouterLink>
</template>
