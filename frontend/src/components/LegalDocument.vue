<script setup lang="ts">
import { Leaf } from "lucide-vue-next";

export interface LegalSection {
  title: string;
  paragraphs?: string[];
  bullets?: string[];
}

defineProps<{
  title: string;
  updated: string;
  sections: LegalSection[];
}>();
</script>

<template>
  <main class="min-h-screen px-4 py-10 sm:px-6">
    <div class="mx-auto w-full max-w-3xl">
      <header class="mb-6 flex items-center justify-between gap-4">
        <RouterLink to="/app" class="flex min-w-0 items-center gap-2">
          <span class="flex h-9 w-9 shrink-0 items-center justify-center rounded-xl bg-moss/15 text-moss">
            <Leaf class="h-5 w-5" />
          </span>
          <span class="truncate font-semibold text-ink">拾题</span>
        </RouterLink>
        <RouterLink to="/app" class="shrink-0 text-sm text-moss transition hover:text-moss/80">
          返回应用
        </RouterLink>
      </header>

      <article class="rounded-3xl border border-mist bg-white/70 p-5 shadow-sm sm:p-8">
        <h1 class="text-2xl font-semibold tracking-tight text-ink">{{ title }}</h1>
        <p class="mt-1 text-xs text-oat">最后更新：{{ updated }}</p>

        <div class="mt-6 space-y-6">
          <section v-for="(section, index) in sections" :key="index">
            <h2 class="text-sm font-medium text-ink">{{ section.title }}</h2>
            <p
              v-for="(paragraph, i) in section.paragraphs ?? []"
              :key="'p' + i"
              class="mt-2 text-sm leading-relaxed text-ink/80"
            >
              {{ paragraph }}
            </p>
            <ul v-if="section.bullets?.length" class="mt-2 space-y-1.5">
              <li
                v-for="(bullet, i) in section.bullets"
                :key="'b' + i"
                class="flex gap-2 text-sm leading-relaxed text-ink/80"
              >
                <span class="mt-[7px] h-1 w-1 shrink-0 rounded-full bg-oat" />
                <span>{{ bullet }}</span>
              </li>
            </ul>
          </section>
        </div>

        <p class="mt-8 border-t border-mist pt-4 text-xs leading-relaxed text-oat">
          本页内容如有疑问，请联系我们：
          <a class="text-moss transition hover:text-moss/80" href="mailto:sealevel666@163.com">sealevel666@163.com</a>
        </p>
      </article>
    </div>
  </main>
</template>
