from __future__ import annotations

import importlib.util
import tempfile
import unittest
from pathlib import Path


SCRIPT = Path(__file__).with_name("dify-prod-bootstrap.py")
SPEC = importlib.util.spec_from_file_location("dify_prod_bootstrap", SCRIPT)
assert SPEC and SPEC.loader
bootstrap = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(bootstrap)


class RecordingDifyClient(bootstrap.DifyClient):
    def __init__(self, apps: list[dict]):
        super().__init__()
        self.apps = apps
        self.requests: list[tuple[str, str, dict | None]] = []

    def list_apps(self) -> list[dict]:
        return self.apps

    def request(self, method, path, body=None, **kwargs):
        self.requests.append((method, path, body))
        if path == "/apps/imports":
            return {"status": "completed", "app_id": "new-version-id"}
        raise AssertionError(f"unexpected request: {method} {path}")


class DifyBootstrapWorkflowSyncTest(unittest.TestCase):
    def test_legacy_same_name_app_does_not_prevent_new_workflow_import(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            workflow = Path(temp_dir) / "shuati-extract-text.yml"
            workflow.write_text(
                "app:\n"
                "  name: shuati-extract-text\n"
                "workflow:\n"
                "  graph: {}\n",
                encoding="utf-8",
            )
            client = RecordingDifyClient(
                [{"id": "legacy-id", "name": "shuati-extract-text"}]
            )

            app_id = client.import_or_reuse_app(workflow, "shuati-extract-text")

        self.assertEqual("new-version-id", app_id)
        self.assertEqual(1, len(client.requests))
        method, path, body = client.requests[0]
        self.assertEqual("POST", method)
        self.assertEqual("/apps/imports", path)
        self.assertIn("shuati-extract-text-", body["yaml_content"])
        self.assertNotIn(
            "name: shuati-extract-text\n",
            body["yaml_content"],
        )

    def test_provisioning_runs_dify_workflow_sync(self):
        provision = SCRIPT.with_name("provision-ubuntu.sh").read_text(encoding="utf-8")

        self.assertIn("dify-prod-bootstrap.py", provision)
        self.assertIn('systemctl is-active --quiet dify-api', provision)


if __name__ == "__main__":
    unittest.main()
