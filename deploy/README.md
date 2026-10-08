# 服务器部署手册（jar + Nginx + systemd）

> 目标形态：**一台服务器**跑 Spring Boot jar + Node 支付侧车 + MySQL + Redis + Nginx。
> 当前服务器为 **Ubuntu 24.04 LTS**，按下面步骤直接执行，不需要更换系统。

## 本项目实际参数

| 项 | 值 |
| --- | --- |
| 域名 | `seaunit.site`（同时支持 `www.seaunit.site`） |
| 服务器 | 阿里云香港轻量 · 2 vCPU / 4 GiB / 50 GiB ESSD |
| 公网 IP | `8.210.73.129` |
| Nginx `server_name` | 已写好，无需再改 |

> 首次部署前，阿里云轻量控制台的「防火墙」必须放行 **22 / 80 / 443**。
> 这是平台侧安全组，脚本无法替代。

## 0. 机器规格

| 项 | 建议 |
| --- | --- |
| CPU / 内存 | **2 核 4G**（2G 也能跑，但需把 JVM 降到 `-Xmx512m`、MySQL buffer pool 降到 128M） |
| 磁盘 | 40G 起步，50–70G 更稳 |
| 带宽 | 20Mbps / 0.5TB 足够 |
| 地域 | 用户在内地 → 选**中国内地**（需 ICP 备案）；免备案可先用香港 |

## 1. 初始化服务器

Ubuntu 24.04 全新实例可以直接用一键脚本完成环境、建库、构建和启动：

```bash
curl -fsSL https://raw.githubusercontent.com/seaunit/shuati/main/deploy/scripts/provision-ubuntu.sh | sudo bash
```

脚本默认按 **HTTP + 公网 IP** 启动，因此会写入 `SHUATI_COOKIE_SECURE=false`。
域名和 HTTPS 配好后，再改为 `true`。生产数据库从空库开始，Flyway 首次启动自动建表。
首次部署会创建管理员 `shuati.admin@gmail.com`，初始密码 `Shuati@2026`。
支付侧车首次先用占位密钥并保持 `SHUATI_PAYMENTS_ENABLED=false`，真实 Waffo 密钥写入
`/etc/shuati/payments.env` 后再启用。

如果需要手工拆开每一步执行，再使用下面的初始化脚本：

```bash
sudo bash deploy/scripts/server-bootstrap.sh
```

会装好：JDK 17、Node 20、MySQL 8、Redis、Nginx，并创建运行用户 `shuati` 与目录 `/opt/shuati`、`/etc/shuati`。

## 2. 建库

```bash
sudo mysql
```

```sql
CREATE DATABASE shuati CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE USER 'shuati'@'127.0.0.1' IDENTIFIED BY '换成你的强密码';
GRANT ALL PRIVILEGES ON shuati.* TO 'shuati'@'127.0.0.1';
FLUSH PRIVILEGES;
EXIT;
```

导入结构（**生产库从空库开始，不要导入真实数据**）：

```bash
mysql -u shuati -p shuati < db/mysql/01_schema.sql
mysql -u shuati -p shuati < db/mysql/03_billing_seed.sql
```

> 表结构也可以交给 Flyway 自动建：只要库是空的，首次启动 jar 时会自动执行迁移。

## 3. 写配置

**后端** `/etc/shuati/env`（从 `deploy/env.example` 复制后改）：

```bash
SPRING_PROFILES_ACTIVE=prod
SHUATI_DB_URL=jdbc:mysql://127.0.0.1:3306/shuati?useUnicode=true&characterEncoding=utf8&connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true&allowPublicKeyRetrieval=true&useSSL=false
SHUATI_DB_USER=shuati
SHUATI_DB_PASSWORD=你的数据库密码
SHUATI_JWT_SECRET=至少32字节的随机串
APP_AES_SECRET=与旧系统一致的Base64密钥
DEEPSEEK_API_KEY=
DEEPSEEK_BASE_URL=https://api.deepseek.com
DEEPSEEK_MODEL=deepseek-flash
SHUATI_BOOTSTRAP_ADMIN_EMAIL=你的邮箱
SHUATI_BOOTSTRAP_ADMIN_PASSWORD=初始管理员密码
SHUATI_REDIS_HOST=127.0.0.1
SHUATI_REDIS_PORT=6379
SHUATI_CAPTCHA_SECRET=随机串
SHUATI_PAYMENTS_ENABLED=true
SHUATI_PAYMENTS_URL=http://127.0.0.1:8090
SHUATI_PAYMENTS_SECRET=与侧车 INTERNAL_SECRET 相同
SHUATI_PAYMENTS_CURRENCY=CNY
# 纯 IP + HTTP 的临时 demo 才设 false；有 HTTPS 一定要 true
SHUATI_COOKIE_SECURE=true
```

**支付侧车** `/etc/shuati/payments.env`：

```bash
WAFFO_MERCHANT_ID=MER_...
WAFFO_PRIVATE_KEY_BASE64=...
WAFFO_ENV=test
WAFFO_STORE_ID=STO_...
WAFFO_PRODUCT_MAP=...
PORT=8090
JAVA_BASE_URL=http://127.0.0.1:8080
INTERNAL_SECRET=与后端一致
WAFFO_DRY_RUN=false
```

两个文件都要限制权限：`sudo chmod 600 /etc/shuati/env /etc/shuati/payments.env && sudo chown shuati:shuati /etc/shuati/*`

## 4. 构建并上传

在**本机**执行（脚本会打包并 scp 上传，再重启服务）：

```bash
bash deploy/scripts/deploy.sh user@服务器IP
```

脚本做的事：前端 `npm run build` → 后端 `mvn package` → 上传 jar/dist/payments → 远端 `npm ci` → 重启 `shuati` 与 `shuati-payments` → reload Nginx。

## 5. 启用 systemd 与 Nginx（首次部署时手动做一次）

```bash
sudo cp deploy/systemd/shuati.service /etc/systemd/system/
sudo cp deploy/systemd/shuati-payments.service /etc/systemd/system/
sudo cp deploy/nginx/shuati.conf /etc/nginx/conf.d/
sudo systemctl daemon-reload
sudo systemctl enable --now shuati shuati-payments
sudo nginx -t && sudo systemctl reload nginx
```

验证：

```bash
curl http://127.0.0.1:8080/actuator/health   # {"status":"UP"}
curl http://127.0.0.1:8090/health            # {"ok":true,...}
sudo systemctl status shuati --no-pager
```

首次启动会用 `SHUATI_BOOTSTRAP_ADMIN_EMAIL/PASSWORD` 创建管理员（账号已存在则只提权、不改密码）。

## 6. 域名与 HTTPS

**纯 IP 的临时 demo**：把 `SHUATI_COOKIE_SECURE=false`，直接用 `http://IP` 访问即可（登录能正常，只是没有 TLS）。

**正式使用**：必须有域名 + HTTPS，否则：

- `Secure` Cookie 不会下发，登录会一直失败
- Waffo 支付回调要求 https
- 内地服务器不提备案，80/443 会被拦

```bash
sudo apt-get install -y certbot python3-certbot-nginx
sudo certbot --nginx -d 你的域名
```

证书自动续期已由 certbot 的 systemd timer 处理。启用后把 `SHUATI_COOKIE_SECURE` 设回 `true`。

## 7. 支付回调

把 `https://你的域名/pay/webhooks/waffo` 填到 Waffo 后台的 Webhook（事件只勾 `order.completed`）。

Nginx 已配好 `/pay/webhooks/` → `127.0.0.1:8090` 的反代，且只暴露这一个路径（创建收银台的 `/checkout` 仅内网可达）。

## 8. 日常运维

```bash
sudo journalctl -u shuati -f            # 后端日志
sudo journalctl -u shuati-payments -f   # 支付侧车日志
sudo systemctl restart shuati           # 重启后端
sudo systemctl reload nginx
```

备份至少覆盖：MySQL `shuati` 库、`/etc/shuati/`（密钥）、`APP_AES_SECRET`（丢了就解不开已存的 AI Key）。
