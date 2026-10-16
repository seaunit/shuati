# Implementation Plan: 题库题目浏览（列表对齐 + 检索）

**Feature**: `001-bank-question-browse` | **Spec**: [spec.md](./spec.md)

## 技术方案

### 1. 列表行布局（前端，纯样式）

`frontend/src/views/BankManageView.vue` 的题目行当前是
`flex flex-wrap`，题干用 `flex-1 basis-full sm:basis-auto`。
`basis-auto` 让题干的假设主轴尺寸等于 max-content（超长），
而 flex 容器的换行判定发生在收缩之前 —— 于是按钮被挤到第二行。

改法：

- 行容器加 `sm:flex-nowrap`，桌面下根本不允许换行；
- 题干的桌面基准改成 `sm:basis-0`（`flex-1` + 基准 0 才能既吸收剩余空间又被截断）；
- 四个元数据列给固定宽度（题型 / 难度 / 单元 / 状态），逐行对齐；
- 题干加 `title`，截断后仍可悬浮看全文；
- 移动端保持现状（题干独占一行、元数据与按钮在下一行）。

### 2. 题干检索（后端 + 前端）

后端 `BankManageController.questions` 增加可选 `q`：

```sql
select q.* from question q join unit u on u.id = q.unit_id
 where u.bank_id = ?
   and (? not in ('ON','OFF') or q.status = ?)
   and (? = '' or q.content like concat('%', ?, '%'))
 order by q.id desc limit 500
```

- 权限仍走 `requireManage(id)`，不放宽任何边界；
- `%` / `_` / `\` 在 Java 侧转义后再拼 `like`，避免用户输入变成通配符；
- 空串直接短路成「不过滤」。

前端在「题目」区域标题行加检索框（lucide `Search` 图标 + 清空按钮），
输入做 300ms 防抖后重新拉取列表；状态过滤与关键词同时生效。

## 影响面

| 位置 | 变更 | 风险 |
| --- | --- | --- |
| `BankManageController#questions` | 新增可选参数 `q` | 低：参数可选，老调用不受影响 |
| `BankManageView.vue` | 行样式 + 检索框 | 低：不涉及数据写入 |
| DB | 无迁移 | 无 |

## 数据变更

无（只读检索，无迁移）。

## 测试策略

- 后端 JUnit（`BankManageQuestionApiTest`）：
  关键词命中题干、`%` 不被当通配符、空关键词返回全部、他人题库仍 403。
- 前端 Playwright（`mobile-responsive.spec.ts`）：
  检索请求带 `q=` 且列表收敛；清空后恢复；长题干行不折行且按钮同行。

## 风险与回滚

- 风险：`sm:flex-nowrap` 在极窄的 sm 视口可能让元数据列挤在一起 —— 由固定宽度 + 题干可收缩兜住，
  并在 Playwright 里断言行宽不超过视口。
- 回滚：两处改动彼此独立，均可单独回退。
