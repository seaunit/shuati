<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { Download, QrCode, ShieldCheck } from "lucide-vue-next";
// 走构建产物拿这张图：文件名带内容指纹，换图就是换 URL，不会被缓存糊弄
import qrCard from "@/assets/download-qr.png";

/**
 * 安卓包下载页（公开页，不需要登录）。
 *
 * 版本号只从服务端清单 `/download/latest.json` 读，页面里 MUST NOT 手写版本号
 * （宪法 v1.3.0「客户端版本号与分发」）。清单由 `mobile/scripts/publish-apk.mjs` 生成，
 * 和 APK 一起放到服务器 `/download/` 目录。
 */
interface ReleaseManifest {
  version: string;
  versionCode: number;
  sizeMb: number;
  sha256: string;
  publishedAt: string;
  url: string;
}

// 静态产物放 /apk/（页面路由是 /download，两者不能撞车）
const APK_URL = "/apk/shuati-android.apk";
const manifest = ref<ReleaseManifest | null>(null);
const manifestMissing = ref(false);

const publishedDate = computed(() => {
  if (!manifest.value?.publishedAt) return "";
  return manifest.value.publishedAt.slice(0, 10);
});

onMounted(async () => {
  try {
    const response = await fetch("/apk/latest.json", { cache: "no-store" });
    if (!response.ok) throw new Error(String(response.status));
    manifest.value = (await response.json()) as ReleaseManifest;
  } catch {
    // 清单还没发布（刚上线/正在发版）时页面照样能用，只是不显示版本号
    manifestMissing.value = true;
  }
});
</script>

<template>
  <main class="mx-auto w-full max-w-3xl px-4 py-10 sm:px-6">
    <header class="flex items-center gap-3">
      <img src="/favicon.svg" alt="拾题" class="h-12 w-12 rounded-xl" />
      <div>
        <h1 class="text-2xl font-semibold tracking-tight text-ink">下载拾题安卓版</h1>
        <p class="mt-1 text-sm text-oat">题库、AI 判分、AI 解析、错题本，手机上接着刷。</p>
      </div>
    </header>

    <section class="mt-8 rounded-2xl border border-mist bg-white/70 p-5 sm:p-6">
      <div class="flex flex-wrap items-center justify-between gap-4">
        <div>
          <p class="text-sm text-ink">
            <span v-if="manifest">当前版本 v{{ manifest.version }}</span>
            <span v-else>安卓安装包</span>
          </p>
          <p class="mt-1 text-xs text-oat">
            <template v-if="manifest">
              {{ manifest.sizeMb }} MB · 发布于 {{ publishedDate }} · Android 8.0 及以上
            </template>
            <template v-else-if="manifestMissing">
              版本清单暂时读取不到，下面按钮仍是最新安装包
            </template>
          </p>
        </div>
        <a
          :href="APK_URL"
          download
          data-testid="download-apk"
          class="flex shrink-0 items-center gap-2 rounded-xl bg-ink px-5 py-3 text-sm font-medium text-white transition hover:bg-ink/90"
        >
          <Download class="h-4 w-4" />
          下载 APK
        </a>
      </div>
      <p v-if="manifest" class="mt-4 break-all rounded-xl bg-mist/60 px-3 py-2 text-xs text-oat">
        SHA256：{{ manifest.sha256 }}
      </p>
    </section>

    <section class="mt-6 grid gap-4 sm:grid-cols-[auto_1fr] sm:items-center">
      <div class="rounded-2xl border border-mist bg-white p-4 text-center">
        <!--
          用 PNG 而不是 SVG 展示：手机长按图片能存进相册，SVG 存不下来。
          产品名与标语已经画在图里了（保存/转发出去都认得），页面不再重复写一遍。
        -->
        <img
          :src="qrCard"
          alt="扫码打开拾题下载页"
          data-testid="download-qr"
          class="mx-auto w-40"
        />
        <a
          :href="qrCard"
          download="shuati-download-qr.png"
          data-testid="save-qr"
          class="mt-3 inline-flex items-center gap-1.5 rounded-full border border-mist px-3 py-1.5 text-xs text-ink transition hover:border-moss"
        >
          <Download class="h-3.5 w-3.5 text-moss" />
          保存二维码
        </a>
      </div>
      <div>
        <p class="flex items-center gap-2 text-sm font-medium text-ink">
          <QrCode class="h-4 w-4 text-moss" />
          手机扫码打开本页
        </p>
        <p class="mt-2 text-sm leading-relaxed text-oat">
          用手机相机或微信扫一扫，会在手机上打开这个下载页；长按二维码可以存到相册，转给朋友也能扫。
        </p>
      </div>
    </section>

    <section class="mt-6 rounded-2xl border border-mist bg-white/70 p-5 sm:p-6">
      <h2 class="flex items-center gap-2 text-sm font-medium text-ink">
        <ShieldCheck class="h-4 w-4 text-moss" />
        安装说明
      </h2>
      <ol class="mt-3 list-decimal space-y-2 pl-5 text-sm leading-relaxed text-oat">
        <li>点击「下载 APK」，下载完成后在通知里点开安装（扫码的话就是在本页点这个按钮）。</li>
        <li>系统提示"未知来源"时，允许本次安装即可（这是我们直接分发的安装包，未上应用商店）。</li>
        <li>如果之前装过旧版本，请先卸载再安装：包名相同但签名不同，覆盖安装会失败。</li>
      </ol>
    </section>

    <section class="mt-6 rounded-2xl border border-mist bg-white/70 p-5 sm:p-6">
      <h2 class="text-sm font-medium text-ink">常见问题</h2>
      <dl class="mt-3 space-y-3 text-sm leading-relaxed">
        <div>
          <dt class="text-ink">会一直更新吗？</dt>
          <dd class="text-oat">
            App 打开的就是网页端，功能更新不用重新下载安装包；安装包只在改动客户端本身
            （图标、启动页、版本号）时才需要重新下载。
          </dd>
        </div>
        <div>
          <dt class="text-ink">iOS 呢？</dt>
          <dd class="text-oat">iOS 版还在准备中，用同一份前端打包，上线后会在这里补齐下载入口。</dd>
        </div>
        <div>
          <dt class="text-ink">装不上怎么办？</dt>
          <dd class="text-oat">
            先确认已卸载旧版本；仍然失败就换浏览器重新下载（微信内置浏览器有时会拦截 APK）。
          </dd>
        </div>
      </dl>
    </section>

    <p class="mt-8 text-center text-xs text-oat">
      <RouterLink to="/app" class="text-moss hover:underline">返回拾题</RouterLink>
    </p>
  </main>
</template>
