/**
 * SPA 路由级的标题与描述。
 *
 * 百度对 JS 渲染支持有限，收录主要靠后端吐的静态页（`/`、`/bank/**`）；
 * 这里管的是浏览器标签页与能执行 JS 的爬虫，顺便避免所有页面共用同一个标题。
 */
interface RouteMeta {
  title: string;
  description: string;
}

const DEFAULT: RouteMeta = {
  title: "拾题 - 在线刷题与 AI 解析",
  description:
    "拾题提供软考等考试的真题题库，选择题本地判分、简答题 AI 判分，还能把自己的资料一键变成可以刷的题库。",
};

const ROUTES: Record<string, RouteMeta> = {
  "/app": {
    title: "题库 - 拾题",
    description: "浏览公共题库与自己导入的题库，按单元开始练习。",
  },
  "/app/practice": {
    title: "刷题 - 拾题",
    description: "在线刷题：选择题本地判分，简答题与伪代码交给 AI 判分并给出解析。",
  },
  "/app/wrong-book": {
    title: "错题本 - 拾题",
    description: "汇总做错的题目，按题库与单元回看，支持重新练习。",
  },
  "/app/stats": {
    title: "练习统计 - 拾题",
    description: "按题库、单元查看练习量与正确率，掌握度一目了然。",
  },
  "/app/import": {
    title: "导入题目 - 拾题",
    description: "粘贴正文、贴网页链接或上传文档，AI 解析成可刷的题库。",
  },
  "/app/banks": {
    title: "题库管理 - 拾题",
    description: "管理自己的题库：新建空题库、导入题目或手工新增题目。",
  },
  "/app/pricing": {
    title: "套餐与点数 - 拾题",
    description: "点数包与套餐说明，充多少用多少。",
  },
  "/login": {
    title: "登录 / 注册 - 拾题",
    description: "登录拾题，继续你的刷题进度。",
  },
  "/terms": { title: "服务条款 - 拾题", description: "拾题服务条款。" },
  "/privacy": { title: "隐私政策 - 拾题", description: "拾题隐私政策。" },
};

function resolve(path: string): RouteMeta {
  if (ROUTES[path]) {
    return ROUTES[path];
  }
  // /app/banks/12 这类详情页按前缀归到列表页的文案
  const prefix = Object.keys(ROUTES)
    .filter((key) => key !== "/" && path.startsWith(key + "/"))
    .sort((a, b) => b.length - a.length)[0];
  return prefix ? ROUTES[prefix] : DEFAULT;
}

export function applyRouteMeta(path: string) {
  const meta = resolve(path);
  document.title = meta.title;
  const tag = document.querySelector('meta[name="description"]');
  if (tag) {
    tag.setAttribute("content", meta.description);
  }
}
