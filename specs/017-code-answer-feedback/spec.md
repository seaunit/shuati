# Feature Specification: 代码题解析展示伪代码

**Feature Branch**: `017-code-answer-feedback`

**Created**: 2026-10-26

**Status**: Implemented

**Input**: 用户反馈算法实现题的判定结果没有清楚展示伪代码；实际参考答案中包含伪代码，但前端按普通文本显示，代码围栏被压成一行。

## User Scenarios & Testing

### User Story 1 - 算法题判定后能直接看到可读伪代码 (Priority: P1)

用户提交算法或编程类简答题后，参考答案和 AI 评语中的代码块应作为独立代码区域展示；即使答案错误，也应给出最小正确伪代码便于学习。

**Independent Test**: 返回含 Markdown 代码围栏的参考答案和评分反馈，页面应渲染两个可读代码块。

**Acceptance Scenarios**:

1. **Given** 参考答案包含三反引号伪代码，**When** 提交简答题，**Then** 页面显示独立代码块，不显示裸三反引号。
2. **Given** 题目要求写函数、SQL、算法或伪代码，**When** AI 判定完成，**Then** feedback 必须包含可读代码块或最小正确伪代码。
3. **Given** 考生答案完全错误，**When** 展示反馈，**Then** 不只是指出遗漏，还要给出正确思路和可执行伪代码框架。
4. **Given** 普通文字型简答题，**When** 判定完成，**Then** 不强制添加无关代码块。

### Edge Cases

- 参考答案没有代码围栏但有换行：按 Markdown 段落正常展示。
- 代码块语言标记为 `text`、`sql`、`java` 或未标记：都能渲染为代码区域。
- 历史评分记录不改数据库，前端更新后重新打开仍应按 Markdown 显示。

## Requirements

- **FR-001**: 参考答案 MUST 使用 Markdown 渲染，不能直接插值成普通文本。
- **FR-002**: 参考答案中的三反引号代码围栏 MUST 渲染成独立代码块。
- **FR-003**: 代码/算法类简答题的 AI feedback MUST 包含清晰的代码块或伪代码块。
- **FR-004**: 代码块应尽量包含输入、核心逻辑、返回或输出。
- **FR-005**: 非代码题 MUST NOT 被强制附加代码块。
- **FR-006**: 已有评分记录和参考答案数据 MUST NOT 需要迁移。

## Success Criteria

- **SC-001**: 用户截图中的 `function solve(m)` 参考答案能正常显示为多行伪代码。
- **SC-002**: Playwright 能检测到参考答案区域存在 `<pre>` 代码块。
- **SC-003**: 提示词测试确认算法题 feedback 要求代码块。
- **SC-004**: 后端全量测试与前端生产构建通过。

## Assumptions

- Markdown 代码围栏是 AI 输出和参考答案的正式代码格式。
- 代码块内容只用于学习提示，不执行用户或模型生成的代码。
