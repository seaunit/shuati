# Quickstart: 验证答案语音朗读

## 1. Build

```bash
npm --prefix frontend run build
```

## 2. E2E

```bash
cd frontend
npx playwright test tests/answer-speech.spec.ts
```

Expected: 所有朗读交互测试通过。

当前测试结果：`12 passed`，覆盖 Markdown 清洗、重新生成解析、失败音色剔除、在线音色主动隐藏和可用音色自动降级。

刷题页相关既有回归：`12 passed`。

Capacitor 壳配置检查：`11/11` 通过。

## 3. Manual Flow

1. 登录测试环境并进入刷题页。
2. 提交一道题，等待参考答案或 AI 解析出现。
3. 点击“朗读”，确认句子高亮并播放。
4. 暂停、继续、调整语速、停止。
5. 开启“朗读代码”，确认代码段被纳入脚本。
6. 播放时切换题目，确认上一题朗读立即停止。
7. 在无中文语音设备或模拟环境确认显示明确提示。

## 4. Mobile

将同一前端构建放入 Capacitor 壳，真机验证：

- 首次点击后可播放。
- 锁屏、切后台或离开页面后播放停止。
- 返回页面可重新播放。
