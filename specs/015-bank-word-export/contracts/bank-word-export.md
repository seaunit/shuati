# Contract: 题库导出 Word

## GET `/api/manage/banks/{id}/export.docx`

### Authorization

- 登录用户
- 私有题库：仅所有者
- 公共题库：仅管理员

### Success

- HTTP 200
- `Content-Type: application/vnd.openxmlformats-officedocument.wordprocessingml.document`
- `Content-Disposition: attachment`，文件名包含题库名并以 `.docx` 结尾
- Body 为标准 Office Open XML `.docx`

### Errors

- 401：未登录
- 403：无维护权限
- 404：题库不存在
- 429：导出过于频繁

### Side Effects

- 不修改数据库
- 不扣点数
- 不调用 AI
