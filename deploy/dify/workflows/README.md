# Dify 工作流定义

本目录保存拾题的 Dify 工作流 DSL。DSL 只包含提示词、输入输出结构和节点连接，不包含任何模型 Key、Service API Token 或用户数据。

## 工作流

| 文件 | 用途 | 输出 |
| --- | --- | --- |
| `shuati-judge.yml` | 简答题判分 | `result` JSON 字符串 |
| `shuati-explain.yml` | 选择题 / 简答题解析 | `explanation` Markdown |
| `shuati-ask.yml` | 围绕当前题目追问与知识扩展 | `answer` Markdown |
| `shuati-extract-text.yml` | 文本或笔记生成题目 | `result` JSON 字符串 |
| `shuati-extract-image.yml` | 截图生成题目 | `result` JSON 字符串 |

## 本地导入

1. 启动 Dify：`pwsh -File deploy/scripts/dify-local.ps1 start`
2. 登录 `http://localhost:8501`
3. 在工作室中依次导入五个 DSL。
4. 为每个工作流配置 Dify 模型供应商。
5. 在“访问 API”中创建独立 Service API Key。
6. 只把 API Key 写入本机 `.env.local`。

## 密钥边界

- 模型供应商 Key 只配置在 Dify 控制台。
- `DIFY_WORKFLOW_*_TOKEN` 只写本机环境变量。
- 不把 Dify 导出的 `include_secret=true` DSL 提交到仓库。
- 发布前重新导出无密钥 DSL，与仓库版本比对。
