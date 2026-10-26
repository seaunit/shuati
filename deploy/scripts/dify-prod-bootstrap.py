#!/usr/bin/env python3
from __future__ import annotations

import argparse
import base64
import json
import os
import shlex
import subprocess
import sys
import time
import urllib.error
import urllib.request
import uuid
from http.cookiejar import CookieJar
from pathlib import Path


BASE_URL = os.environ.get("DIFY_CONSOLE_URL", "http://127.0.0.1:8501").rstrip("/")
CONSOLE_API = f"{BASE_URL}/console/api"
SERVICE_API = f"{BASE_URL}/v1"
ENV_FILE = Path(os.environ.get("SHUATI_ENV_FILE", "/etc/shuati/env"))
SCRIPT_DIR = Path(__file__).resolve().parent
WORKFLOW_DIR = Path(
    os.environ.get("DIFY_WORKFLOW_DIR", SCRIPT_DIR.parent / "dify" / "workflows")
)
PLUGIN_ID = (
    "langgenius/deepseek:0.0.26@"
    "bdf47cb41e9062bba00d26625d2a795c1874011d8ce64bff738c44b5a31787ea"
)
PROVIDER = "langgenius/deepseek/deepseek"
WORKFLOWS = {
    "shuati-judge": "DIFY_WORKFLOW_JUDGE_TOKEN",
    "shuati-explain": "DIFY_WORKFLOW_EXPLAIN_TOKEN",
    "shuati-extract-text": "DIFY_WORKFLOW_EXTRACT_TEXT_TOKEN",
    "shuati-extract-image": "DIFY_WORKFLOW_EXTRACT_IMAGE_TOKEN",
}
PNG_1PX = base64.b64decode(
    "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg=="
)


class ApiError(RuntimeError):
    def __init__(self, status: int, body: str, method: str = "", path: str = ""):
        prefix = f"{method.upper()} {path} " if method and path else ""
        super().__init__(f"{prefix}HTTP {status}: {body[:500]}")
        self.status = status
        self.body = body


def read_env(path: Path) -> dict[str, str]:
    values: dict[str, str] = {}
    if not path.exists():
        return values
    for raw in path.read_text(encoding="utf-8").splitlines():
        line = raw.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, raw_value = line.split("=", 1)
        try:
            parsed = shlex.split(raw_value, comments=False, posix=True)
            value = parsed[0] if parsed else ""
        except ValueError:
            value = raw_value
        values[key.strip()] = value
    return values


def write_env(path: Path, updates: dict[str, str]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    original = path.read_text(encoding="utf-8") if path.exists() else ""
    lines = original.splitlines()
    pending = dict(updates)
    result: list[str] = []
    for line in lines:
        if "=" in line and not line.lstrip().startswith("#"):
            key = line.split("=", 1)[0].strip()
            if key in pending:
                result.append(f"{key}={pending.pop(key)}")
                continue
        result.append(line)
    for key, value in pending.items():
        result.append(f"{key}={value}")
    backup = path.with_name(f"{path.name}.bak.{time.strftime('%Y%m%d-%H%M%S')}")
    if path.exists():
        backup.write_text(original, encoding="utf-8")
        os.chmod(backup, 0o600)
    path.write_text("\n".join(result).rstrip() + "\n", encoding="utf-8")
    os.chmod(path, 0o600)
    try:
        import shutil
        shutil.chown(path, user="shuati", group="shuati")
    except (LookupError, PermissionError):
        pass


class DifyClient:
    def __init__(self) -> None:
        self.jar = CookieJar()
        self.opener = urllib.request.build_opener(
            urllib.request.HTTPCookieProcessor(self.jar)
        )
        self.csrf = ""

    def cookie(self, name: str) -> str:
        for cookie in self.jar:
            if cookie.name == name:
                return cookie.value
        return ""

    def request(
        self,
        method: str,
        path: str,
        body: dict | None = None,
        raw_body: bytes | None = None,
        content_type: str = "application/json",
        timeout: int = 60,
    ) -> dict:
        headers = {"Accept": "application/json"}
        data = raw_body
        if body is not None:
            data = json.dumps(body).encode("utf-8")
        if data is not None:
            headers["Content-Type"] = content_type
        csrf = self.cookie("csrf_token") or self.csrf
        if csrf:
            headers["X-CSRF-Token"] = csrf
        request = urllib.request.Request(
            path if path.startswith("http") else CONSOLE_API + path,
            data=data,
            headers=headers,
            method=method.upper(),
        )
        try:
            with self.opener.open(request, timeout=timeout) as response:
                payload = response.read()
                if not payload:
                    return {}
                return json.loads(payload)
        except urllib.error.HTTPError as exc:
            body_text = exc.read().decode("utf-8", errors="replace")
            raise ApiError(exc.code, body_text, method, path) from exc

    def login(self, email: str, password: str) -> None:
        encoded = base64.b64encode(password.encode("utf-8")).decode("ascii")
        result = self.request(
            "POST",
            "/login",
            {"email": email, "password": encoded, "remember_me": True},
        )
        if result.get("result") != "success":
            raise RuntimeError("Dify 登录未返回 success")
        self.csrf = self.cookie("csrf_token")
        if not self.csrf:
            raise RuntimeError("Dify 登录后缺少 CSRF Cookie")

    def ensure_setup(self, values: dict[str, str]) -> None:
        status = self.request("GET", "/setup")
        if status.get("step") == "finished":
            return
        init_password = values.get("INIT_PASSWORD", "")
        admin_email = values.get("DIFY_ADMIN_EMAIL", "")
        admin_password = values.get("DIFY_ADMIN_PASSWORD", "")
        if not init_password or not admin_email or not admin_password:
            raise RuntimeError(
                "/etc/shuati/env 缺少 INIT_PASSWORD、DIFY_ADMIN_EMAIL 或 DIFY_ADMIN_PASSWORD"
            )
        self.request("POST", "/init", {"password": init_password})
        self.request(
            "POST",
            "/setup",
            {
                "email": admin_email,
                "name": "拾题运维",
                "password": admin_password,
                "language": "zh-Hans",
            },
        )
        print("[bootstrap] Dify 管理员已初始化")

    def list_apps(self) -> list[dict]:
        result = self.request("GET", "/apps?page=1&limit=100")
        return result.get("data", [])

    def install_plugin(self) -> None:
        try:
            self.request(
                "POST",
                "/workspaces/current/plugin/install/marketplace",
                {"plugin_unique_identifiers": [PLUGIN_ID]},
                timeout=180,
            )
        except ApiError as exc:
            if exc.status not in {400, 409}:
                raise
        deadline = time.time() + 240
        while time.time() < deadline:
            plugins = self.request(
                "GET", "/workspaces/current/plugin/list?page=1&page_size=100"
            )
            if "langgenius/deepseek" in json.dumps(plugins, ensure_ascii=False):
                print("[bootstrap] DeepSeek 插件已安装")
                return
            time.sleep(5)
        raise RuntimeError("DeepSeek 插件安装超时")

    def configure_provider(self, api_key: str) -> None:
        if not api_key:
            raise RuntimeError("/etc/shuati/env 缺少 DEEPSEEK_API_KEY")
        try:
            existing = self.request(
                "GET", f"/workspaces/current/model-providers/{PROVIDER}/credentials"
            )
        except ApiError as exc:
            if exc.status not in {400, 404}:
                raise
            existing = {}
        if existing.get("credentials"):
            print("[bootstrap] DeepSeek 模型凭证已存在")
            return
        self.request(
            "POST",
            f"/workspaces/current/model-providers/{PROVIDER}/credentials",
            {"credentials": {"api_key": api_key}, "name": "production"},
        )
        print("[bootstrap] DeepSeek 模型凭证已配置")

    def import_or_reuse_app(self, workflow_file: Path, app_name: str) -> str:
        apps = self.list_apps()
        for app in apps:
            if app.get("name") == app_name:
                return str(app["id"])
        result = self.request(
            "POST",
            "/apps/imports",
            {
                "mode": "yaml-content",
                "yaml_content": workflow_file.read_text(encoding="utf-8"),
            },
            timeout=120,
        )
        if result.get("status") == "pending":
            result = self.request(
                "POST", f"/apps/imports/{result['id']}/confirm", {}, timeout=120
            )
        if result.get("status") not in {"completed", "completed-with-warnings"}:
            raise RuntimeError(
                f"导入 {app_name} 失败：{result.get('error') or result.get('status')}"
            )
        app_id = result.get("app_id")
        if not app_id:
            raise RuntimeError(f"导入 {app_name} 后没有 app_id")
        return str(app_id)

    def publish(self, app_id: str, app_name: str) -> None:
        self.request(
            "POST",
            f"/apps/{app_id}/workflows/publish",
            {
                "marked_name": f"production-{time.strftime('%Y%m%d')}",
                "marked_comment": f"{app_name} 正式环境发布",
            },
        )

    def api_key(self, app_id: str) -> str:
        existing = self.request("GET", f"/apps/{app_id}/api-keys")
        items = existing.get("data", [])
        if items:
            return str(items[0]["token"])
        created = self.request("POST", f"/apps/{app_id}/api-keys", {})
        token = created.get("token", "")
        if not token:
            raise RuntimeError(f"应用 {app_id} 创建 API Token 失败")
        return str(token)


def service_request(
    token: str,
    path: str,
    body: dict | None = None,
    raw_body: bytes | None = None,
    content_type: str = "application/json",
    timeout: int = 240,
) -> dict:
    data = raw_body
    headers = {
        "Authorization": f"Bearer {token}",
        "Accept": "application/json",
    }
    if body is not None:
        data = json.dumps(body).encode("utf-8")
    if data is not None:
        headers["Content-Type"] = content_type
    request = urllib.request.Request(
        SERVICE_API + path, data=data, headers=headers, method="POST"
    )
    try:
        with urllib.request.urlopen(request, timeout=timeout) as response:
            return json.loads(response.read())
    except urllib.error.HTTPError as exc:
        body_text = exc.read().decode("utf-8", errors="replace")
        raise ApiError(exc.code, body_text) from exc


def upload_image(token: str) -> str:
    boundary = "----shuati" + uuid.uuid4().hex
    parts = [
        f"--{boundary}\r\n".encode(),
        b'Content-Disposition: form-data; name="user"\r\n\r\nshuati:production\r\n',
        f"--{boundary}\r\n".encode(),
        b'Content-Disposition: form-data; name="file"; filename="smoke.png"\r\n',
        b"Content-Type: image/png\r\n\r\n",
        PNG_1PX,
        b"\r\n",
        f"--{boundary}--\r\n".encode(),
    ]
    result = service_request(
        token,
        "/files/upload",
        raw_body=b"".join(parts),
        content_type=f"multipart/form-data; boundary={boundary}",
    )
    file_id = result.get("id")
    if not file_id:
        raise RuntimeError("Dify 图片上传未返回 id")
    return str(file_id)


def run_workflow(token: str, inputs: dict, files: list[dict] | None = None) -> str:
    body = {
        "inputs": inputs,
        "response_mode": "blocking",
        "user": "shuati:production-smoke",
    }
    if files:
        body["files"] = files
    result = service_request(token, "/workflows/run", body)
    data = result.get("data", {})
    if data.get("status") != "succeeded":
        raise RuntimeError(f"Dify 工作流状态异常：{data.get('status')}")
    return str(result.get("workflow_run_id") or data.get("id") or "")


def smoke(tokens: dict[str, str]) -> None:
    runs = []
    runs.append(
        run_workflow(
            tokens["DIFY_WORKFLOW_JUDGE_TOKEN"],
            {
                "questionContent": "Java 中接口的作用是什么？",
                "keyPoints": "抽象能力；多实现",
                "referenceAnswer": "接口定义抽象能力，允许不同类实现。",
                "userAnswer": "接口用于定义规范并提供多态能力。",
            },
        )
    )
    runs.append(
        run_workflow(
            tokens["DIFY_WORKFLOW_EXPLAIN_TOKEN"],
            {
                "questionContent": "Spring Boot 中 @Component 的作用是什么？",
                "options": '[{"key":"A","text":"声明 Bean"}]',
                "referenceAnswer": "A",
            },
        )
    )
    runs.append(
        run_workflow(
            tokens["DIFY_WORKFLOW_EXTRACT_TEXT_TOKEN"],
            {"sourceText": "Spring Bean 默认作用域是 singleton。"},
        )
    )
    image_id = upload_image(tokens["DIFY_WORKFLOW_EXTRACT_IMAGE_TOKEN"])
    runs.append(
        run_workflow(
            tokens["DIFY_WORKFLOW_EXTRACT_IMAGE_TOKEN"],
            {"instruction": "识别截图中的题目并输出题目 JSON"},
            [{"type": "image", "transfer_method": "local_file", "upload_file_id": image_id}],
        )
    )
    print("[bootstrap] 四类 Dify 工作流冒烟成功")
    print("[bootstrap] 运行 ID：" + ", ".join(runs))


def activate_backend() -> None:
    subprocess.run(["systemctl", "restart", "shuati"], check=True)
    deadline = time.time() + 90
    while time.time() < deadline:
        try:
            with urllib.request.urlopen(
                "http://127.0.0.1:8080/actuator/health", timeout=5
            ) as response:
                if json.loads(response.read()).get("status") == "UP":
                    print("[bootstrap] 正式后端已切换到 Dify 并恢复 UP")
                    return
        except Exception:
            time.sleep(2)
    raise RuntimeError("后端切换 Dify 后 90 秒内未恢复")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--activate",
        action="store_true",
        help="四类冒烟通过后切换 SHUATI_AI_ENGINE=dify 并重启后端",
    )
    args = parser.parse_args()

    values = read_env(ENV_FILE)
    client = DifyClient()
    print("[bootstrap] 检查 Dify 初始化状态", flush=True)
    client.ensure_setup(values)
    values = read_env(ENV_FILE)
    print("[bootstrap] 登录 Dify 管理控制台", flush=True)
    client.login(
        values["DIFY_ADMIN_EMAIL"],
        values["DIFY_ADMIN_PASSWORD"],
    )
    print("[bootstrap] 安装 DeepSeek 插件", flush=True)
    client.install_plugin()
    print("[bootstrap] 配置 DeepSeek 模型凭证", flush=True)
    client.configure_provider(values.get("DEEPSEEK_API_KEY", ""))

    tokens: dict[str, str] = {}
    for app_name, env_key in WORKFLOWS.items():
        print(f"[bootstrap] 处理工作流 {app_name}", flush=True)
        workflow_file = WORKFLOW_DIR / f"{app_name}.yml"
        if not workflow_file.exists():
            raise RuntimeError(f"缺少工作流 DSL：{workflow_file}")
        app_id = client.import_or_reuse_app(workflow_file, app_name)
        client.publish(app_id, app_name)
        tokens[env_key] = client.api_key(app_id)
        print(f"[bootstrap] {app_name} 已发布，app_id={app_id}")

    write_env(
        ENV_FILE,
        {
            **tokens,
            "DIFY_BASE_URL": "http://127.0.0.1:8501/v1",
            "DIFY_CONNECT_TIMEOUT": "10s",
            "DIFY_READ_TIMEOUT": "175s",
        },
    )
    smoke(tokens)
    if args.activate:
        write_env(ENV_FILE, {"SHUATI_AI_ENGINE": "dify"})
        activate_backend()
    else:
        print("[bootstrap] Token 已写入生产配置；使用 --activate 切换后端")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as exc:
        print(f"[bootstrap] 失败：{exc}", file=sys.stderr)
        raise SystemExit(1)
