"""Deployment contract tests; Docker is mocked and functions run through interactive Zsh."""
import os
from pathlib import Path
import re
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]
SOURCE = (ROOT / "scripts/deploy.sh").read_text()
FUNCTIONS = [
    "log", "die", "require_variable", "start_infrastructure", "initialize_object_storage",
    "wait_for_object_storage_initialization", "ensure_object_storage_initialized",
    "ensure_infrastructure_running", "start_applications",
]

def definitions():
    # Keep the production script portable to its existing Bash deployment hosts.
    # Its indirect lookup has equivalent Zsh syntax; no Docker or build commands run here.
    parts = ["set -euo pipefail"]
    for name in ["INFRA_SERVICES", "INIT_SERVICES", "APP_SERVICES"]:
        parts.append(re.search(r"^" + name + r"=.*$", SOURCE, re.M).group())
    for name in FUNCTIONS:
        function = re.search(r"^" + name + r"\(\) \{\n.*?^\}", SOURCE, re.M | re.S).group()
        parts.append(function.replace("${!1:-}", "${(P)1:-}"))
    return "\n".join(parts)

MOCKS = """
DEPLOY_WAIT_TIMEOUT=2
compose() {
    printf '%s\\n' "compose $*" >> "$EVENTS"
    if [[ "$1" == ps ]]; then
        if [[ "$*" == *minio-init* && "$INIT_PRESENT" == false ]]; then return 0; fi
        printf '%s\\n' container-test
    fi
}
compose_apps() { printf '%s\\n' "apps $*" >> "$EVENTS"; }
docker() { printf '%s\\n' "$INIT_STATE"; }
wait_for_loki() { printf '%s\\n' loki-ready >> "$EVENTS"; }
validate_application_jars() { printf '%s\\n' jars-ready >> "$EVENTS"; }
sleep() { SECONDS=$((SECONDS + 2)); }
"""

class DeployTests(unittest.TestCase):
    def run_functions(self, action, state="exited 0", present="true", secrets=True):
        with tempfile.TemporaryDirectory() as directory:
            events = Path(directory) / "events"
            events.touch()
            env = dict(os.environ, EVENTS=str(events), INIT_STATE=state, INIT_PRESENT=present)
            env["MINIO_ROOT_PASSWORD"] = "test-root" if secrets else ""
            env["MINIO_SECRET_KEY"] = "test-app" if secrets else ""
            result = subprocess.run(["zsh", "-lic", definitions() + "\n" + MOCKS + "\n" + action],
                                    env=env, text=True, capture_output=True, timeout=10)
            return result, events.read_text().splitlines()

    def test_service_lists_cover_compose_without_oneshot_in_running_group(self):
        expected = set(re.findall(r"^  ([a-z][a-z-]+):$", (ROOT / "compose.yml").read_text().split("\nnetworks:")[0], re.M))
        actual = set()
        for name in ["INFRA_SERVICES", "INIT_SERVICES", "APP_SERVICES"]:
            actual.update(re.search(name + r"=\(([^)]+)\)", SOURCE).group(1).split())
        self.assertEqual(actual, expected)
        self.assertNotIn("minio-init", re.search(r"INFRA_SERVICES=\(([^)]+)\)", SOURCE).group(1).split())

    def test_infra_waits_and_initializes_before_apps(self):
        result, events = self.run_functions("start_infrastructure\nstart_applications")
        self.assertEqual(result.returncode, 0, result.stderr)
        up = next(e for e in events if e.startswith("compose up -d --wait "))
        self.assertIn(" minio ", up)
        self.assertIn(" tempo ", up)
        initializer = next(i for i, e in enumerate(events) if "--force-recreate minio-init" in e)
        application = next(i for i, e in enumerate(events) if e.startswith("apps up "))
        self.assertLess(initializer, application)
        self.assertIn("--no-deps", events[application])

    def test_init_failure_blocks_apps(self):
        result, events = self.run_functions("start_infrastructure\nstart_applications", state="restarting 1")
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("initialization failed", result.stderr)
        self.assertFalse(any(e.startswith("apps up ") for e in events))

    def test_init_timeout_is_bounded(self):
        result, _ = self.run_functions("initialize_object_storage", state="running 0")
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("did not finish", result.stderr)

    def test_apps_reject_missing_or_failed_initializer(self):
        for state, present in [("exited 0", "false"), ("exited 1", "true")]:
            result, events = self.run_functions("start_applications", state=state, present=present)
            self.assertNotEqual(result.returncode, 0)
            self.assertFalse(any(e.startswith("apps up ") for e in events))

    def test_missing_credentials_fail_before_starting_containers(self):
        result, events = self.run_functions("start_infrastructure", secrets=False)
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("MINIO_ROOT_PASSWORD", result.stderr)
        self.assertEqual(events, [])

    def test_bi_migration_is_registered(self):
        self.assertIn('"$ROOT_DIR/sql/migrations/20261102_bi_telemetry_archive.sql"', SOURCE)

if __name__ == "__main__":
    unittest.main()
