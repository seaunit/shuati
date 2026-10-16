# Feature Specification: 题库题目浏览（列表对齐 + 检索）

**Feature Branch**: `001-bank-question-browse`

**Created**: 2026-10-16

**Status**: Draft

**Input**: 用户要求 —— 「题目显示这块要固定对齐，内容超出部分用 ... 代替，不要把按钮之类的硬挤出来，另外增加题目检索功能」

## User Scenarios & Testing *(mandatory)*

### User Story 1 - 题干再长也不挤走操作按钮 (Priority: P1)

题库维护者在题库管理页浏览题目列表。题干很长时，当前实现会把「下架 / 删除」挤到第二行，
行的对齐被打乱，操作按钮位置随题干长度浮动，扫视和连点都很别扭。

**Why this priority**：这是维护题库时每一条都会看到的界面，直接影响录入与校对效率。

**Independent Test**：用一条 200 字题干渲染列表，断言行高不变、四个元数据列宽度一致、
操作按钮仍在同一行。

**Acceptance Scenarios**：

1. **Given** 题库里有一条超长题干的题目，**When** 打开题库管理页，**Then** 该行不折行，
   题干以省略号截断，编辑/上下架/删除按钮仍在同一行右侧。
2. **Given** 列表里有长题干和短题干两种题目，**When** 上下对比，**Then** 题型、难度、所属单元、
   状态四列的横向位置逐行对齐。
3. **Given** 题干被截断，**When** 鼠标悬停在题干上，**Then** 能看到完整题干的悬浮提示。

---

### User Story 2 - 按关键词检索题库内的题目 (Priority: P1)

维护者在几百道题的题库里找不到某道题，只能靠上下滚动「已下架/已上架」筛选，效率极低。

**Why this priority**：题库动辄上百题，没有检索就没有可维护性。

**Independent Test**：在检索框输入关键词，断言请求带上 `q=`，且列表只剩匹配题目。

**Acceptance Scenarios**：

1. **Given** 题库里存在题干包含「索引」的题目，**When** 在检索框输入「索引」，
   **Then** 列表只显示匹配的题目。
2. **Given** 已输入关键词，**When** 清空检索框，**Then** 列表恢复显示全部题目。
3. **Given** 已输入关键词，**When** 切换「全部 / 已上架 / 已下架」，**Then** 状态过滤与关键词同时生效。
4. **Given** 关键词无任何匹配，**When** 等待请求返回，**Then** 页面提示「没有符合条件的题目」。

---

### Edge Cases

- 关键词含 `%` 或 `_`：按普通字符匹配，不得变成 SQL 通配符（`%` 不能命中全部题目）。
- 关键词只有空格：等同于未检索。
- 题库为空：不显示检索框失效态，直接提示没有题目。
- 非维护者（他人题库 / 非管理员看公共题库）：接口仍返回 403/404，检索不放宽权限。

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: 题目行的题型、难度、所属单元、状态四列 MUST 固定宽度并可截断，逐行对齐；
  题干列 MUST 占据剩余空间。
- **FR-002**: 题干超出可用宽度 MUST 以省略号截断，MUST NOT 换行、MUST NOT 挤压其它列。
- **FR-003**: 桌面宽度下每行 MUST NOT 因题干过长折行。
- **FR-004**: 题库管理页 MUST 提供题干检索输入框。
- **FR-005**: 检索 MUST 由服务端执行（`GET /api/manage/banks/{id}/questions?q=`），
  MUST NOT 只过滤当前已加载的一页；权限与状态过滤保持不变（仅题库维护者可见）。
- **FR-006**: 空关键词 MUST 等价于不过滤；`%`、`_`、`\` MUST 转义为普通字符。
- **FR-007**: 无匹配结果时 MUST 给出明确空态文案。
- **FR-008**: 检索 MUST NOT 改变新增/编辑/上下架/删除题目的既有行为与响应。

### Key Entities

- **Question**：题目（题干、题型、难度、所属单元、上下架状态、答案、解析）。检索只读，不修改任何字段。
- **Unit**：单元，题目归属；检索结果仍按 `bank_id` 限定在题库内。

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 题干 200 字时，行高与短题干行一致，操作按钮仍在同一行（自动化断言 + 截图）。
- **SC-002**: 输入关键词后请求 URL 带 `q=`，列表只剩匹配项（Playwright 断言）。
- **SC-003**: `mvn test`（后端全量）与 `npx playwright test tests/mobile-responsive.spec.ts`、
  `npm run build` 全部通过。
- **SC-004**: 检索含 `%` 的关键词不会返回全部题目（后端 JUnit 断言）。

## Assumptions

- 检索范围为当前题库内题目的题干（不检索答案与解析），符合维护者「找那道题」的实际用法。
- 结果集沿用现有 `limit 500`，不做分页 —— 题库规模尚未到需要分页的量级。
- 沿用现有权限模型：能进题库管理页的账号才能检索（`requireManage`）。
