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
首次部署会创建管理员 `shuati.admin@gmail.com`：初始口令**由脚本随机生成并在部署结束时打印**
（也可用 `SHUATI_BOOTSTRAP_ADMIN_PASSWORD` 指定）。仓库里不再保留任何默认口令。
支付侧车首次先用占位密钥并保持 `SHUATI_PAYMENTS_ENABLED=false`，真实 Waffo 密钥写入
`/etc/shuati/payments.env` 后再启用。

域名审核或证书签发期间，如果部分手机运营商无法直连公网 IP，可临时开启
Cloudflare Quick Tunnel：

```bash
curl -fsSL https://raw.githubusercontent.com/seaunit/shuati/main/deploy/scripts/start-quick-tunnel.sh | sudo bash
```

脚本会输出一个 `https://*.trycloudflare.com` 地址，适合手机临时验收；正式域名生效后
应停用该隧道。

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

**支付侧车** `/etc/shuati/payments.env`：这一份**由脚本自动生成**，不需要手填。
商户 ID、店铺 ID、商品映射（3 个点数包）都是公开标识，已内置在
`provision-ubuntu.sh` 里；`INTERNAL_SECRET` 会自动生成并同步写入后端
`SHUATI_PAYMENTS_SECRET`，两边永远一致。

只有**私钥**需要在部署时交互输入（输入不回显，**直接回车 = 保留现有私钥或为空**）：

```
[配置] Waffo Pancake 私钥（RSA，用于请求签名）
[配置] 当前未设置
[配置] 打开私钥文件，把里面的内容整段复制粘贴进来即可
[配置] （PEM 原文，或文件里那一整串 base64 都行，两种都会自动识别）
[配置] 输入不会回显；直接回车 = 保留现有私钥或为空
[配置] 60 秒内没有输入将按回车处理：
```

**两处交互输入都有 60 秒等待上限**：超时按「直接回车」处理，也就是保留现有值或留空，
部署会继续往下走，不会一直卡在提示上。想调整等待时间（比如私钥要从别处复制、想给久一点），
部署时带上环境变量即可：

```bash
sudo SHUATI_PROMPT_TIMEOUT=180 bash deploy/scripts/provision-ubuntu.sh
```

超时后会打印一行 `[配置] 等待 60 秒无输入，按回车处理`，方便事后确认当时是保留还是新填。

拿到私钥时脚本会自动把 `SHUATI_PAYMENTS_ENABLED` 置为 `true`；跳过则保持 `false`，
并在部署结束时提示补配方法。也可以免交互传入：

```bash
# 方式一：直接给私钥文件内容（PEM 文本或一整串 base64 都行）
sudo WAFFO_PRIVATE_KEY_BASE64="$(cat private.pem)" bash deploy/scripts/provision-ubuntu.sh
# 方式二：给私钥文件路径，脚本自己读（服务器上不用长期留这个文件）
sudo WAFFO_PRIVATE_KEY_FILE=/root/waffo-private-key.pem bash deploy/scripts/provision-ubuntu.sh
```

生成后的 `/etc/shuati/payments.env` 长这样（私钥不落仓库）：

```bash
WAFFO_MERCHANT_ID=MER_7PGKvFIiNnnwwEO5RsJLTR
WAFFO_PRIVATE_KEY_BASE64=...
WAFFO_ENV=test
WAFFO_STORE_ID=STO_06yfSw5FByRGGzjlMVXC1n
WAFFO_PRODUCT_MAP=PACK:pack_9=PROD_...,PACK:pack_29=PROD_...,PACK:pack_99=PROD_...
PORT=8090
JAVA_BASE_URL=http://127.0.0.1:8080
INTERNAL_SECRET=与后端一致
WAFFO_DRY_RUN=false
```

重跑部署脚本时，未显式传入 `WAFFO_ENV` 会保留 `/etc/shuati/payments.env`
里已有的环境；只有首次部署才默认使用 `test`。已切生产后要临时切回测试，
可显式执行 `sudo WAFFO_ENV=test bash deploy/scripts/provision-ubuntu.sh`。

支付启用后可在服务器自检（真实创建一次测试收银台，不写库、不改商品）：

```bash
cd /opt/shuati/payments && sudo -u shuati npm run smoke
```

**注册邮箱验证码**：`provision-ubuntu.sh` 会在写入配置前交互提示输入 SMTP 密码
（输入不回显，**直接回车 = 保留现有密码或为空**，同样有 60 秒等待上限）。
想换密码时，重跑脚本时输入新密码即可覆盖。下面的变量会被脚本自动写入 `/etc/shuati/env`，
手工部署时按同样格式配置即可：

```bash
SHUATI_MAIL_ENABLED=true
SHUATI_MAIL_HOST=smtpdm.aliyun.com
SHUATI_MAIL_PORT=465
SHUATI_MAIL_USERNAME=no-reply@mail.seaunit.site
SHUATI_MAIL_PASSWORD=在DirectMail控制台设置的SMTP密码
SHUATI_MAIL_FROM=no-reply@mail.seaunit.site
SHUATI_MAIL_FROM_NAME=拾题
SHUATI_EMAIL_SECRET=独立随机值
SHUATI_MAIL_SSL=true
SHUATI_MAIL_CONNECT_TIMEOUT_MS=10000
SHUATI_MAIL_READ_TIMEOUT_MS=10000
SHUATI_MAIL_WRITE_TIMEOUT_MS=10000
SHUATI_MAIL_CODE_TTL_SECONDS=300
SHUATI_MAIL_COOLDOWN_SECONDS=60
SHUATI_MAIL_EMAIL_DAILY_LIMIT=10
SHUATI_MAIL_IP_HOURLY_LIMIT=20
SHUATI_MAIL_DEVICE_DAILY_LIMIT=30
SHUATI_MAIL_GLOBAL_DAILY_LIMIT=2000
```

DirectMail 发信域名必须完成 SPF、DKIM、DMARC 和 MX 验证。生产环境只把 SMTP 密码写入
`/etc/shuati/env`，不要提交到 Git。

密码为空时脚本会打印告警，此时注册与找回密码不可用。补填方式：

```bash
sudo sed -i "s|^SHUATI_MAIL_PASSWORD=.*|SHUATI_MAIL_PASSWORD='你的密码'|" /etc/shuati/env
sudo systemctl restart shuati
```

也可以不交互，直接把密码交给脚本：

```bash
sudo SHUATI_MAIL_PASSWORD='你的密码' bash deploy/scripts/provision-ubuntu.sh
```

找回密码复用同一 DirectMail 配置，不需要新增 SMTP 变量。首次部署会自动执行
Flyway `V10__session_version.sql`，为 `profiles` 增加 `session_version`。
密码重置成功后该版本会递增，使所有旧 JWT 立即失效。

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
口令由 `provision-ubuntu.sh` 随机生成并写在部署输出末尾（例如 `密码：xxxxxxxx`），请立即保存并登录后修改。

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

项目提供了幂等 HTTPS 脚本，会申请 `seaunit.site` 与 `www.seaunit.site` 的证书、配置
Nginx 跳转，并把后端 Cookie 切换为安全模式：

```bash
curl -fsSL https://raw.githubusercontent.com/seaunit/shuati/main/deploy/scripts/enable-https.sh | sudo bash
```

### 大陆网络：Cloudflare Named Tunnel

香港源站直连在大陆部分运营商网络会被阻断，`*.trycloudflare.com` 也可能被单独屏蔽。
正式使用建议把域名 NS 切到 Cloudflare，然后使用 Named Tunnel：

1. Cloudflare 添加站点 `seaunit.site`，把阿里云域名 NS 改成 Cloudflare 分配的两条 NS；
2. Zero Trust → Networks → Tunnels → Create tunnel，复制 Tunnel Token；
3. 在服务器执行：

```bash
curl -fsSL https://raw.githubusercontent.com/seaunit/shuati/main/deploy/scripts/install-cloudflare-tunnel.sh | sudo bash -s -- '<TUNNEL_TOKEN>'
```

4. 为 Tunnel 添加 Public Hostname：
   - `seaunit.site` → `https://127.0.0.1:443`
   - `www.seaunit.site` → `https://127.0.0.1:443`
   - 高级设置里开启 `No TLS Verify`
   - `HTTP Host Header` 填 `seaunit.site`

这样仍然使用现有香港服务器作为源站，只是公网入口改为 Cloudflare。

## 7. 支付回调

把 `https://你的域名/pay/webhooks/waffo` 填到 Waffo 后台的 Webhook（事件只勾 `order.completed`）。

Nginx 已配好 `/pay/webhooks/` → `127.0.0.1:8090` 的反代，且只暴露这一个路径（创建收银台的 `/checkout` 仅内网可达）。

## 8. 日常运维

### 安卓安装包发布（`/apk/`）

网页端下载页是 `/download`，安装包与版本清单放在 `/apk/`（Nginx 已把 `/apk/` 指向
`/opt/shuati/apk/`；两个路径必须分开，否则带斜杠访问会撞成目录）。

安装包**不进 Git 仓库**，走 GitHub Release 分发。部署脚本同步时按这个顺序来：

| 步骤 | 做什么 |
| --- | --- |
| 1 | 拉 `releases/latest/download/latest.json`（版本清单，下载页只读它）；拉不到就保留服务器上已有的包 |
| 2 | 按清单里的版本号，拉 `releases/download/android-v{版本}/shuati-android-{版本}.apk`——这个 URL 每个版本只出现一次，不会被 CDN 缓存串版本 |
| 3 | 用它覆盖稳定别名 `shuati-android.apk`（下载页与二维码指向它）；带版本号那份同时留档 |
| 4 | 用清单里的 SHA256 校验下载结果，不一致会明确告警（CDN 还在发上一版时就是这个现象） |

第 2 步失败（比如手动发的 Release 没带版本号产物）会回退去拉 `releases/latest/download/shuati-android.apk`。

出包与发布（推荐打 tag，交给 CI）：

```bash
# 推荐：打 tag，CI 出包并发 Release（版本号必须与 mobile/version.properties 一致）
git tag android-v1.0.0 && git push origin android-v1.0.0

# 或者本机出包后由脚本发 Release（需要 gh CLI 已登录，或 GITHUB_TOKEN）
cd mobile && npm run publish:apk
```

拉取失败（Release 还没发、GitHub 抖动）**不会中断部署**，服务器上已有的安装包会保留；
想跳过这一步用 `SHUATI_SKIP_APK=1`；换镜像源用 `SHUATI_APK_BASE_URL=<latest 基础地址>`，
换 Release 文件地址用 `SHUATI_APK_TAG_BASE_URL=<releases/download 基础地址>`。

新版 Release 发布后重新部署一次，`https://www.seaunit.site/download` 自动显示新版本号
（页面读的是 `latest.json`，不缓存），下载按钮与二维码都指向 `/apk/shuati-android.apk`。

**部署脚本会自检这一条**：它会用 `curl` 打本机 Nginx 的 `/apk/latest.json` 与
`/apk/shuati-android.apk`，分别要求 `application/json` 与
`application/vnd.android.package-archive`。对不上就说明 `/apk/` 又被 SPA 回退成首页了——
那会让用户下载到一个 2KB 的 HTML 改名成 `.apk`，安装时报"解析软件包时出现问题"。

### 下载速度（Cloudflare 边缘缓存）

下载页的按钮优先指向**带版本号**的文件（清单里的 `versionedUrl`）：文件名固定、内容不变，
响应头是 `public, max-age=31536000, immutable`，是最适合边缘缓存的东西。

但 **Cloudflare 默认只缓存常见静态后缀，`.apk` 不在其中**——线上实测过
`cf-cache-status: MISS`，也就是说每次下载都在跨境回源香港服务器（实测约 1.2MB/s，
4MB 的包要 3 秒多；手机上更慢）。

到 CF 后台加一条规则就够（每个节点第一次下载后就不再回源）：

1. Caching → Cache Rules → Create rule
2. 名称：`缓存安卓包`
3. 匹配：`URI Path` starts with `/apk/`
4. Cache eligibility：**Eligible for cache**
5. Edge TTL：**Use cache-control header if present**

效果：带版本号的包按 immutable 缓存到边缘；`latest.json` 是 `no-store`，不受影响；
稳定别名是 `no-cache`，会回源确认（只有清单读不到时才会用到它）。

### Nginx 配置

每次部署都会**重建** `/etc/nginx/conf.d/shuati.conf`：有证书就用
`deploy/nginx/shuati-prod.conf`（域名与证书目录从线上那份配置里读出来），
没证书就用 `deploy/nginx/shuati.conf`；改完先 `nginx -t`，不过就自动回滚到备份
（`/etc/nginx/conf.d/shuati.conf.bak.<时间戳>`）。

两份模板都在 HTTPS/HTTP server 内包含
`/etc/nginx/snippets/seaunit-integrations/*.conf`。拾题只负责提供这个稳定 include，
识海等独立应用各自安装并维护自己的 location snippet，因此重新部署拾题不会再删除 `/kb`。
根路径 `/` 和 `/seaunit-portal/` 固定指向 `https://seaunit.site/` 双应用门户。

> 以前这里是"线上已有 `ssl_certificate` 就整份跳过"，怕覆盖 certbot 改过的证书行。
> 代价是之后所有 Nginx 改动都进不到线上——`/apk/` 规则就是这样漏掉的。
> **改 server 级规则时，两份模板都要改**（HTTP 与 HTTPS 各一份），部署自检会盯着。

```bash
sudo journalctl -u shuati -f            # 后端日志
sudo journalctl -u shuati-payments -f   # 支付侧车日志
sudo systemctl restart shuati           # 重启后端
sudo systemctl reload nginx
```

备份至少覆盖：MySQL `shuati` 库、`/etc/shuati/`（密钥）、`APP_AES_SECRET`（丢了就解不开已存的 AI Key）。

## 9. 搜索引擎收录（百度）

百度蜘蛛对 JS 渲染支持很弱，所以公开页面不走 SPA，直接由后端吐完整 HTML：

| 路径 | 谁渲染 | 说明 |
| --- | --- | --- |
| `/` | 后端 | 落地页：产品说明 + 公共题库列表 |
| `/bank/{id}` | 后端 | 公共题库页：单元列表 |
| `/bank/{id}/unit/{uid}` | 后端 | 单元页：本单元全部题目（题干、选项、答案、解析） |
| `/bank/{id}/question/{qid}` | 后端 | 单题详情页：标题就是题干，吃长尾搜索 |
| `/sitemap.xml` | 后端 | 首页 + 合规页 + 公共题库、单元与题目 |
| `/robots.txt` | 前端静态 | 屏蔽 `/api`、`/app`、`/login` 等 |
| `/app/**`、`/login` | 前端 SPA | 应用本体，不参与收录 |

只暴露 `owner_id` 为空的公共题库，用户自己建的题库不会出现在收录里。
每页都带 canonical、面包屑、上下页内链，以及 schema.org 结构化数据
（首页 WebSite/Organization、列表页 ItemList、单题 Question/Answer、全站 BreadcrumbList）。
按当前数据量，sitemap 约 480 条 URL（1 题库 + 158 单元 + 322 题）。

### 上线步骤

1. 部署（`provision-ubuntu.sh`）会把验证文件与 `robots.txt` 一起放到 `/opt/shuati/frontend/`。
2. Nginx 需要放开后端路由，重跑一次 Cloudflare 生产配置即可（会自动备份并在 `nginx -t` 失败时回滚）：

```bash
curl -fsSL https://raw.githubusercontent.com/seaunit/shuati/main/deploy/scripts/enable-cloudflare-proxy.sh | sudo bash
```

3. 自检：

```bash
curl -s https://www.seaunit.site/robots.txt
curl -s https://www.seaunit.site/sitemap.xml | head -5
curl -s https://www.seaunit.site/baidu_verify_codeva-YKEobJ2RLw.html   # 输出站点校验码
curl -s https://www.seaunit.site/ | grep -o '<title>.*</title>'          # 首页要能直接看到正文
```

4. 在百度搜索资源平台「普通收录 → API 提交」拿到 token，写进 `/etc/shuati/env`：

```bash
sudo sh -c "echo \"SHUATI_BAIDU_PUSH_TOKEN='你的token'\" >> /etc/shuati/env"
```

5. 推送 URL（新增题库或单元后跑一次即可）：

```bash
cd /opt/shuati && sudo bash deploy/scripts/baidu-push.sh
```

也可以挂到 crontab 每周推一次：

```
0 6 * * 1 cd /opt/shuati && bash deploy/scripts/baidu-push.sh >>/var/log/shuati-baidu-push.log 2>&1
```

### 相关配置

| 变量 | 默认值 | 说明 |
| --- | --- | --- |
| `SHUATI_SITE_URL` | `https://www.seaunit.site` | canonical / sitemap 里的规范域名，要和搜索资源平台验证的站点一致 |
| `SHUATI_SITE_NAME` | `拾题` | 站点名 |
| `SHUATI_SITE_TAGLINE` | `在线刷题与 AI 解析` | 首页副标题 |
| `SHUATI_CONTACT_EMAIL` | `sealevel666@163.com` | 页脚客服邮箱 |
| `SHUATI_BAIDU_TONGJI_ID` | 已内置 | 百度统计站点 ID，留空则不注入统计脚本 |
| `SHUATI_BAIDU_PUSH_TOKEN` | 空 | 主动推送 token，只用于 `baidu-push.sh` |

站点验证文件（`baidu_verify_*.html`）必须长期保留在站点根目录，删掉验证会失效。

## 10. Google 收录（Search Console）

Google 不卡备案，反而是眼下最快能拿到收录数据的渠道。抓取端已经就绪：

- `robots.txt` 里**没有**屏蔽 Googlebot（屏蔽的是 `Google-Extended`、`Google-Agent`，
  那是给 AI 用的，不影响搜索）
- 公开页都是后端直出的完整 HTML，带 canonical、面包屑、JSON-LD
- `sitemap.xml` 带 `lastmod`，Google 会据此安排重抓

### 步骤

1. 打开 [Google Search Console](https://search.google.com/search-console) → 添加资源 →
   选「**网址前缀**」填 `https://www.seaunit.site`（不要用「网域」，那个只能用 DNS 验证）
2. 选一种验证方式：

   **方式一：HTML 标记（推荐，改环境变量即可）**

   ```bash
   sudo sh -c "echo \"SHUATI_GOOGLE_SITE_VERIFICATION='GSC给你的content值'\" >> /etc/shuati/env"
   sudo systemctl restart shuati
   curl -s https://www.seaunit.site/ | grep google-site-verification
   ```

   注意：首页 `/` 是后端直出的，所以 meta 由后端注入（`shuati.seo.google-site-verification`）。
   只把 meta 写进 SPA 外壳是验不过的，Google 抓的是 `/` 那份 HTML。

   **方式二：HTML 文件**

   把 GSC 下载的 `googleXXXX.html` 提交到 `frontend/public/`（这样每次部署都在），
   或临时放到服务器：

   ```bash
   sudo sh -c 'printf "google-site-verification: googleXXXX.html\n" > /opt/shuati/frontend/googleXXXX.html'
   curl -s https://www.seaunit.site/googleXXXX.html
   ```

   放好之后如果之前点过一次「验证」（Google 那时拿到的是 SPA 首页那份 200 响应），
   记得去 Cloudflare「缓存 → 清除缓存」按 URL 清一下这个验证文件，否则可能读到旧副本。
   仓库里的 `${APP_DIR}/deploy/nginx/shuati-prod.conf` 已经把「不存在的 .html 返回 404」
   写进配置，重装一次 nginx 配置后这类验证就不会再被 SPA 回退干扰。

   **方式三：DNS TXT（不改代码，覆盖面最大）**

   在 Cloudflare DNS 加一条 TXT：名称 `@`、内容 `google-site-verification=xxxx`，等几分钟生效。

3. 验证通过后：左侧「站点地图」→ 填 `sitemap.xml` → 提交
4. 「网址检查」里逐个提交重点页面并点「请求编入索引」：首页、`/bank/1`、
   `/bank/1/unit/27`、以及几道题详情页。这个有每日额度，别浪费在重复页面上
5. 之后盯三个地方：「索引 → 页面」（收录/未收录原因）、「增强功能 → 面包屑」、
   「体验 → 核心网页指标」

Google 收录一般几天到几周，比百度快得多。Bing 站长工具可以直接从 GSC 导入站点，不用重新验证。

### 访问统计（GA4）

自然搜索的落地页是后端直出的，所以 GA 也要装在这些页面上，否则 GA 里看不到搜索流量。
衡量 ID（`G-S2H8MP4FYT`）作为默认值内置在 `application.yml` 和 `frontend/index.html` 两处，
部署即生效。要换账号时用环境变量覆盖，改完重启后端即可，不用重新构建前端：

```bash
sudo sh -c "echo \"SHUATI_GA_MEASUREMENT_ID='G-XXXXXXXXXX'\" >> /etc/shuati/env"
sudo systemctl restart shuati
curl -s https://www.seaunit.site/bank/1 | grep -c googletagmanager   # 应该是 1
```

两处用的是同一个衡量 ID；SPA 外壳那份改完需要重新构建前端。

记得在「隐私政策」里保留第三方统计的披露（已有百度统计 + Google Analytics 的说明），
接新统计工具时同步补一句。

### 让 Cloudflare 缓存直出页面

实测（上海移动 → Cloudflare 伦敦节点，`curl -w`）：

```
边缘已缓存的静态文件：connect=285ms tls=573ms ttfb=846ms
直出页面（每次回源）：connect=289ms tls=591ms ttfb=1505ms   ← 回源那一段约 900ms
```

国内到 Cloudflare 边缘本身就有几百毫秒且节点可能调度到欧洲，所以**回源那一段能省掉就省掉**。
后端已经给直出页面返回 `Cache-Control: public, max-age=300, s-maxage=600,
stale-while-revalidate=86400`，但 Cloudflare 默认只按文件扩展名缓存，
`/bank/1` 这种没有扩展名的路径不会自动缓存，需要加一条 Cache Rule：

**Rules → Cache Rules → Create rule**

- 表达式（Edit expression）：
  `(http.host eq "www.seaunit.site" and starts_with(http.request.uri.path, "/bank/")) or http.request.uri.path eq "/"`
- Cache eligibility：**Eligible for cache**
- Edge TTL：**Respect origin**（跟着后端的 s-maxage 走）
- Browser TTL：Respect origin

加完验证（应该变成 `cf-cache-status: HIT`，TTFB 掉到 ~850ms）：

```bash
curl -s -I https://www.seaunit.site/bank/1 | grep -i cf-cache-status
```

想先确认后端自己快不快，在服务器上直接测源站（绕开 Cloudflare）：

```bash
curl -s -o /dev/null -w 'ttfb=%{time_starttransfer}s\n' http://127.0.0.1:8080/bank/1
```

这个值是几十毫秒就说明应用没问题，慢的那部分是跨境链路。想彻底解决只能上国内节点（需要备案）。

### 建议：把 `seaunit.site` 301 到 `www`

现在 `seaunit.site` 和 `www.seaunit.site` 返回同一份内容（`canonical` 都指向 www）。
`canonical` 已经能表达首选域，但 301 更干净。在 Cloudflare 里加一条 Redirect Rule 即可：

**Rules → Redirect Rules → Create rule**，条件 `Hostname equals seaunit.site`，
目标 `https://www.seaunit.site${uri.path}`，状态码 **301**。
