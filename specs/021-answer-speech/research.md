# Research: 答案语音朗读

## Decision: 使用设备原生 SpeechSynthesis

**Rationale**: 零服务成本、无需密钥、满足 Web 与 Capacitor 共用；所有文本只留在用户设备。

**Alternatives considered**:

- 后端接入云端 TTS：音色统一，但增加费用、延迟、缓存和隐私边界。
- 原生 Android/iOS TTS 插件：可控性更高，但会引入平台分支，违背一套前端多端打包原则。

## Decision: 单句串行播放

**Rationale**: 可准确高亮当前句，便于暂停、切题停止，并避免浏览器对超长 utterance 支持不一致。

**Alternatives considered**:

- 整篇一次性播放：实现简单，但无法可靠高亮、暂停和定位。

## Decision: Markdown 转换为听读脚本

**Rationale**: 移除标题符号、链接目标、列表标记等视觉字符，代码块默认只播报提示。

**Alternatives considered**:

- 直接朗读 Markdown：会把 `##`、反引号、链接 URL 读出来，体验差。

## Decision: 偏好只保存在 sessionStorage

**Rationale**: 当前需求不需要账号级同步，会话级保存足够，也不引入后端契约变更。

**Alternatives considered**:

- localStorage：跨会话保留但共享设备上可能影响其他用户。
- 用户配置表：成本高，超出本期范围。
