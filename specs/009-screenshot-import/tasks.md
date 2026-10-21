# Tasks: 截图导入题库

## Phase 0 · 选型（阻塞）

- [x] T000 确认识图通道：沿用现有单条 AI 配置（DeepSeek Flash 支持图片），不引入 OCR 服务

## Phase 1 · 后端识图

- [x] T001 `AiClient.chatWithImage()`：OpenAI 用 `image_url` data URL、Anthropic 用 base64 image block
- [x] T002 不新增 OCR 配置项（复用现有 AI 配置）
- [x] T003 `POST /api/import` 支持 `kind=screenshot`：data URL 校验（≤5MB）→ 写 `import_task(kind=screenshot)`
- [x] T004 截图任务一次出题；识别为空返回「截图里没识别到题目」，任务标记失败且全额退点
- [x] T005 单测：data URL 格式、5MB 上限、协议无关的 JSON 结构（`ImportScreenshotValidationTest`）

## Phase 2 · 前端

- [x] T006 导入页新增第 4 个来源「截图识别」
- [x] T007 支持 `paste` 事件读取剪贴板图片 + 文件选择（PNG/JPG/WebP），显示缩略图与「移除」
- [x] T008 提交后复用现有任务轮询与预览勾选界面
- [x] T009 Playwright：模拟粘贴、未选图拦截、缩略图与移除按钮（52 条用例全绿）

## Phase 3 · 计费与验收

- [x] T010 计费：按 1 万字等价扣 10 点，失败全额退回
- [ ] T011 验收：部署后用真实题目截图端到端跑通（需要线上模型实际接受图片输入）

## Phase 4 · 网页抓取合规（robots.txt）

- [x] T012 抓取前读取 `/robots.txt`（crawler-commons 解析），Disallow 返回 403 并提示改用「截图识别」；
      robots.txt 404 视为允许，4xx/5xx 或无法解析保守拒绝
- [x] T013 SSRF 防护：仅 http/https；禁止本机 / 内网 / 链路本地 / 云元数据地址；
      重定向逐跳复检；抓取 UA 标注 `ShuatiBot/1.0`
- [x] T014 网页端提示：URL 输入框下方说明「只抓 robots.txt 允许的页面，不允许请用截图识别」
- [x] T015 单测：全站禁止 / 按路径禁止 / 自定义 UA 覆盖、私网地址、非 http 与 localhost 拒绝
