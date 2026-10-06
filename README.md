# 拾题（Vue 3 + Spring Boot 3 + MySQL）

刷题与题库管理系统，前端 Vue 3 + Vite，后端 Spring Boot 3 + MySQL 8.4，
部署形态为可执行 jar + systemd + Nginx 反向代理（同域）。

## 目录

```
backend/   Spring Boot 后端（Java 17 + Maven + Flyway）
frontend/  Vue 3 前端（Vite + Pinia + Tailwind 4）
db/        Supabase → MySQL 迁移脚本与说明
deploy/    Nginx / systemd / 构建部署脚本
docs/      设计与实施计划
```

## 本地开发

前置：JDK 17、Maven、Node 20+、MySQL 8.4、Redis 6+。

```bash
# 1. 初始化数据库（开发库）
mysql -u root -p < db/mysql/00_create_database.sql
mysql -u root -p shuati < db/mysql/01_schema.sql
mysql -u root -p shuati < db/_supabase_raw/02_data.sql   # 迁移的真实数据
mysql -u root -p shuati < db/mysql/03_billing_seed.sql

# 2. 配置密钥（已被 gitignore）
cp deploy/env.example .env.local
# 至少填 APP_AES_SECRET、DEEPSEEK_API_KEY、SHUATI_JWT_SECRET

# 3. 启动后端
mvn -f backend/pom.xml spring-boot:run -Dspring-boot.run.profiles=local

# 4. 启动前端
npm --prefix frontend install
npm --prefix frontend run dev
```

访问 http://localhost:5173 ，开发服务器会把 `/api` 代理到 `127.0.0.1:8080`。

## 测试

```bash
mvn -f backend/pom.xml test        # 需要本机 MySQL 的 shuati_test 库
npm --prefix frontend run build    # 类型检查 + 生产构建
```

## 生产部署

```bash
# 服务器准备
sudo useradd -r -s /usr/sbin/nologin shuati
sudo mkdir -p /opt/shuati /etc/shuati
sudo cp deploy/env.example /etc/shuati/env   # 填入生产密钥

# 构建并上传
bash deploy/scripts/deploy.sh user@server

# 服务器上启用
sudo cp deploy/systemd/shuati.service /etc/systemd/system/
sudo cp deploy/nginx/shuati.conf /etc/nginx/conf.d/
sudo systemctl daemon-reload
sudo systemctl enable --now shuati
sudo nginx -t && sudo systemctl reload nginx
```

首次启动会用 `SHUATI_BOOTSTRAP_ADMIN_EMAIL/PASSWORD` 创建管理员；
账号已存在时只提升角色、不覆盖密码。

## 环境变量

| 变量 | 说明 |
| --- | --- |
| `SHUATI_DB_URL` / `SHUATI_DB_USER` / `SHUATI_DB_PASSWORD` | 生产 MySQL 连接 |
| `SHUATI_JWT_SECRET` | JWT 签名密钥，至少 32 字节 |
| `APP_AES_SECRET` | AI Key 的 AES-256-GCM 密钥（Base64，32 字节），与旧系统一致 |
| `DEEPSEEK_API_KEY` / `DEEPSEEK_BASE_URL` / `DEEPSEEK_MODEL` | AI 接入兜底配置 |
| `SHUATI_BOOTSTRAP_ADMIN_EMAIL` / `SHUATI_BOOTSTRAP_ADMIN_PASSWORD` | 首次启动引导管理员 |
| `SHUATI_REDIS_HOST` / `SHUATI_REDIS_PORT` / `SHUATI_REDIS_PASSWORD` / `SHUATI_REDIS_DB` | Redis 连接（验证码存储与限流） |
| `SHUATI_CAPTCHA_SECRET` | 验证码加盐哈希密钥 |

## 关键实现说明

- 认证：Spring Security 6 + JWT（httpOnly Cookie）+ CSRF 双重提交，旧 `$2a$10$` bcrypt 哈希可直接登录。
- 点数：`MONTHLY` 优先、`BONUS` 兜底，扣费用 `SELECT ... FOR UPDATE` 保证原子；AI 失败自动退点。
- 导入：`@Async` 线程池 + `import_task` 轮询，文档 5000 字分块、10 万字上限，失败切片按比例退点。
- 验证码：服务端生成、Redis 存储（`captcha:{ticket}`，TTL 自动过期）、一次性校验、错误统一提示、IP/设备限流；默认加盐哈希存储，答案永不返回前端。详见 `docs/验证码安全说明.md`。
- 数据差异：MySQL 无法实现 PostgreSQL 的部分唯一索引，公共题库名唯一与默认题库唯一由服务层保证。
