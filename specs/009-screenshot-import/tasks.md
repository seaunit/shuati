# Tasks: 截图导入题库

## Phase 0 · 选型（阻塞）

- [ ] T000 确认识图通道：A 阿里云 OCR（推荐）/ B 视觉大模型 / C 前端 tesseract.js

## Phase 1 · 后端识图

- [ ] T001 新增 `ImageTextRecognizer` 接口 + 所选方案的实现；凭证从环境变量读，缺失时启动日志告警、接口返回 503
- [ ] T002 新增配置项 `shuati.ocr.provider/enabled/max-chars`，默认关闭，部署脚本按需写入 env
- [ ] T003 新增 `POST /api/import/screenshot`：接收 `{ imageBase64, fileName, bankName?, bankDescription?, targetBankId? }`，
      识别 → 写 `import_task(kind=screenshot)` → 返回 taskId；识别为空则 400 且不建任务
- [ ] T004 识别文本过长按 `max-chars` 截断，并在任务详情里标记「已截断」
- [ ] T005 单测：识别为空、超长截断、开关关闭、凭证缺失

## Phase 2 · 前端

- [ ] T006 导入页新增第 4 个来源「截图识别」，未启用时隐藏
- [ ] T007 支持 `paste` 事件读取剪贴板图片 + 文件选择，显示缩略图与「移除」
- [ ] T008 提交后复用现有任务轮询与预览勾选界面，文案改成「识别中… / AI 拆题中…」
- [ ] T009 Playwright：粘贴（用 ClipboardEvent 模拟）、未选图拦截、缩略图与提交

## Phase 3 · 计费与验收

- [ ] T010 计费沿用 EXTRACT 字数口径；识别失败不扣点（补集成用例）
- [ ] T011 验收：真实题目截图端到端跑通；无文字截图给中文错误；开关关闭时入口消失
