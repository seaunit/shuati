# Quickstart: Dify 生产发布

## 1. Preflight

```bash
ssh root@8.210.73.129
free -m
docker --version
systemctl is-active shuati nginx mysql redis-server
```

Expected: 现有服务全部 active；Dify 部署前新增 2 GiB Swap。

## 2. Deploy Dify

```bash
/opt/shuati/deploy/scripts/dify-prod.sh install
/opt/shuati/deploy/scripts/dify-prod.sh status
```

Expected: Dify 控制台只在 `127.0.0.1:8501` 提供 HTTP 200。

## 3. Bootstrap Workflows

```bash
python3 /opt/shuati/deploy/scripts/dify-prod-bootstrap.py
```

Expected:

- 管理员初始化完成
- DeepSeek 插件安装完成
- 四个工作流导入并发布
- 四个 Service API Token 写入 `/etc/shuati/env`

## 4. Switch Backend

```bash
sudo sed -i 's/^SHUATI_AI_ENGINE=.*/SHUATI_AI_ENGINE=dify/' /etc/shuati/env
sudo systemctl restart shuati
curl -fsS http://127.0.0.1:8080/actuator/health
```

## 5. Production Verification

```bash
curl -fsS https://www.seaunit.site/
curl -fsS https://www.seaunit.site/login
curl -fsS https://www.seaunit.site/pricing
ss -ltnp | grep 8501
```

Expected: 公开页面 200，8501 只监听 `127.0.0.1`，四类 Dify 冒烟成功。

## Rollback

```bash
sudo sed -i 's/^SHUATI_AI_ENGINE=.*/SHUATI_AI_ENGINE=direct/' /etc/shuati/env
sudo systemctl restart shuati
curl -fsS http://127.0.0.1:8080/actuator/health
```

Expected: 正式后端恢复直连 AI，网站和支付保持在线。
