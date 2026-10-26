# Research: 代码题解析展示伪代码

## Evidence

- 生产参考答案已包含完整的 `function solve(m)` Markdown 伪代码。
- `PracticeView.vue` 使用 `{{ result.referenceAnswer }}` 插值，代码围栏不会渲染。
- AI feedback 使用 MarkdownText，但旧评分提示词没有要求代码题必须附带伪代码。

## Decisions

- 参考答案改用已有 `MarkdownText` 组件。
- 算法、编程、SQL 和伪代码题要求 feedback 提供最短正确代码块。
- 普通文字题不强制生成代码。
- 不修改历史数据；前端修复后旧记录也会正常显示。
