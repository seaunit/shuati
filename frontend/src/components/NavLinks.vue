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

defineProps<{ nav: { href: string; label: string; icon: string }[] }>();

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
    class="flex items-center gap-3 rounded-xl px-3 py-2 text-sm transition"
    :class="isActive(item.href) ? 'bg-moss/15 font-medium text-moss' : 'text-ink/75 hover:bg-mist'"
  >
    <component
      :is="icons[item.icon] ?? Home"
      class="h-4 w-4"
      :class="isActive(item.href) ? 'text-moss' : 'text-oat'"
    />
    {{ item.label }}
  </RouterLink>
</template>
