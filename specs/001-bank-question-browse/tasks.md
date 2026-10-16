# Tasks: 题库题目浏览（列表对齐 + 检索）

**Feature**: `001-bank-question-browse` | **Plan**: [plan.md](./plan.md)

## T001 后端：题目列表接口支持关键词检索

- [x] `BankManageController#questions` 增加 `@RequestParam(defaultValue = "") String q`
- [x] `like` 输入的 `%` / `_` / `\` 转义，空串短路为不过滤
- [x] 保持 `requireManage` 权限与状态过滤

## T002 后端：检索相关 JUnit

- [x] 关键词命中的题目才返回
- [x] `%` 不是通配符（只命中真的含 `%` 的题干）
- [x] 空关键词返回该题库全部题目
- [x] 他人题库检索仍 403

## T003 前端：题目行固定对齐 + 省略号

- [x] 行容器 `sm:flex-nowrap`，题干 `sm:basis-0`
- [x] 题型 / 难度 / 单元 / 状态固定宽度，逐行对齐
- [x] 题干 `truncate` + `title` 悬浮看全文

## T004 前端：检索框

- [x] 「题目」区域标题行加带 `Search` 图标的检索输入框与清空按钮
- [x] 300ms 防抖后带 `q=` 重新拉取；与状态过滤叠加
- [x] 清空后恢复全部

## T005 前端：Playwright

- [x] 输入关键词 → 断言请求带 `q=` 且列表只剩匹配项
- [x] 清空 → 恢复全部
- [x] 长题干行不折行、按钮同行、无横向溢出

## T006 验证与交付

- [x] `mvn test` 全量通过
- [x] `npm run build` 通过
- [x] `npx playwright test tests/mobile-responsive.spec.ts` 全通过
- [x] 提交并推送（布局与检索同属一次改造，合并在一次提交）
