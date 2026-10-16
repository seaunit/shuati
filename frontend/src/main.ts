import { createApp } from "vue";
import { createPinia } from "pinia";
import App from "./App.vue";
import router from "./router";
import "./styles/main.css";

createApp(App).use(createPinia()).use(router).mount("#app");

// 撤掉 index.html 里内联的首屏启动页。
// 时机很关键：要等路由首次就绪（包含登录态这类异步前置），撤早了会露出还没渲染的空壳，
// 那又变成"空白"了；撤晚了则白白挡着页面。
async function dismissBootSplash() {
  try {
    await router.isReady();
  } catch {
    // 首次导航失败（例如后端挂了）也要撤，否则启动页会一直挡着页面
  }
  requestAnimationFrame(() => {
    const splash = document.getElementById("boot-splash");
    if (!splash) return;
    splash.classList.add("boot-splash--done");
    window.setTimeout(() => splash.remove(), 260);
  });
}

void dismissBootSplash();
