import { createRouter, createWebHistory } from "vue-router";
import { useAuthStore } from "@/stores/auth";

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: "/", redirect: "/app" },
    {
      path: "/login",
      component: () => import("@/views/LoginView.vue"),
      meta: { public: true },
    },
    // 合规页面：公开可访问，不需要登录（Waffo 生产审核会检查）
    { path: "/terms", component: () => import("@/views/TermsView.vue") },
    { path: "/privacy", component: () => import("@/views/PrivacyView.vue") },
    {
      path: "/app",
      component: () => import("@/layouts/AppLayout.vue"),
      children: [
        // 游客模式：首页的公共题库允许未登录浏览
        { path: "", component: () => import("@/views/HomeView.vue") },
        {
          path: "practice",
          component: () => import("@/views/PracticeView.vue"),
          meta: { requiresAuth: true },
        },
        {
          path: "wrong-book",
          component: () => import("@/views/WrongBookView.vue"),
          meta: { requiresAuth: true },
        },
        {
          path: "stats",
          component: () => import("@/views/StatsView.vue"),
          meta: { requiresAuth: true },
        },
        {
          path: "import",
          component: () => import("@/views/ImportView.vue"),
          meta: { requiresAuth: true },
        },
        {
          path: "pricing",
          component: () => import("@/views/PricingView.vue"),
          meta: { requiresAuth: true },
        },
        {
          path: "admin",
          component: () => import("@/views/AdminView.vue"),
          meta: { requiresAuth: true, admin: true },
        },
      ],
    },
  ],
});

router.beforeEach(async (to) => {
  const auth = useAuthStore();
  if (!auth.loaded) {
    await auth.load();
  }
  if (to.meta.requiresAuth && !auth.user) {
    return { path: "/login", query: { next: to.fullPath } };
  }
  if (to.meta.admin && !auth.isAdmin) {
    return { path: "/app" };
  }
  if (to.path === "/login" && auth.user) {
    return { path: "/app" };
  }
  return true;
});

export default router;
