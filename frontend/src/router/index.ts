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
    {
      path: "/app",
      component: () => import("@/layouts/AppLayout.vue"),
      children: [
        { path: "", component: () => import("@/views/HomeView.vue") },
        { path: "practice", component: () => import("@/views/PracticeView.vue") },
        { path: "wrong-book", component: () => import("@/views/WrongBookView.vue") },
        { path: "stats", component: () => import("@/views/StatsView.vue") },
        { path: "import", component: () => import("@/views/ImportView.vue") },
        { path: "pricing", component: () => import("@/views/PricingView.vue") },
        { path: "admin", component: () => import("@/views/AdminView.vue"), meta: { admin: true } },
      ],
    },
  ],
});

router.beforeEach(async (to) => {
  const auth = useAuthStore();
  if (!auth.loaded) {
    await auth.load();
  }
  if (!to.meta.public && !auth.user) {
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
