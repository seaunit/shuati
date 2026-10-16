# Tasks: 未作答也能跳过

**Input**: [spec.md](./spec.md)、[plan.md](./plan.md)

- [x] T001 `PracticeView.vue`：未提交时也显示「下一题 / 完成练习」，与「提交答案」并列
  （未提交时它是次要按钮，提交后变主按钮，保持原来的视觉主次）
- [x] T002 确认 `next()` 不产生提交请求（跳过 = 0 分、不进错题本）；跳过的题保持未作答，可回来补答
- [x] T003 Playwright：跳过后到达下一题 + 无提交请求 + 光标写的是新当前题 + 390px 下无横向溢出
- [x] T004 证据：`npm run build` 通过；`npx playwright test` **43 条全绿**（新增 1 条跳过用例）
