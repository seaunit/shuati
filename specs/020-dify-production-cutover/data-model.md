# Data Model: Dify 生产切换

## AiCallLog

现有业务表，生产调用继续记录：

- `provider=DIFY`
- `workflow_key=judge|explain|extract-text|extract-image`
- `external_run_id`
- Token 用量、耗时、成功状态和脱敏错误

## WorkflowRuntime

运行时实体，不进入业务数据库：

- 工作流名称：`shuati-judge`、`shuati-explain`、`shuati-extract-text`、`shuati-extract-image`
- Dify App ID
- Service API Token
- 插件依赖：`langgenius/deepseek:0.0.26`

## ProductionEnvironment

密钥文件实体：

- 路径：`/etc/shuati/env`
- 权限：`600`
- 属主：`shuati:shuati`
- 关键字段：AI 引擎、Dify 地址、四个 Token、Dify 运行时密钥、模型 Key

## DeploymentEvidence

发布证据：

- systemd 服务健康与重启次数
- `/actuator/health`
- 四类 Dify 真实冒烟结果
- 公网页面状态码
- 端口监听与密钥扫描结果
