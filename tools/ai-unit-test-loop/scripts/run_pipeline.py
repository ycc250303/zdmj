#!/usr/bin/env python3
import argparse
import json
import os
import subprocess
import sys
from datetime import datetime


def run_cmd(cmd, cwd):
    print(f"\n[run] {' '.join(cmd)} (cwd={cwd})")
    proc = subprocess.run(cmd, cwd=cwd, text=True)
    return proc.returncode


def read_json_if_exists(path):
    if not os.path.exists(path):
        return None
    with open(path, "r", encoding="utf-8") as f:
        return json.load(f)


def main():
    parser = argparse.ArgumentParser(description="One-command pipeline runner for AI test loop")
    parser.add_argument("--config", default="tools/ai-unit-test-loop/orchestrator.config.json")
    parser.add_argument("--skip-maven", action="store_true", help="Skip mvn clean test jacoco:report")
    args = parser.parse_args()

    repo_root = os.getcwd()
    config_path = os.path.join(repo_root, args.config)
    if not os.path.exists(config_path):
        print(f"[error] config not found: {config_path}", file=sys.stderr)
        return 2

    with open(config_path, "r", encoding="utf-8") as f:
        cfg = json.load(f)

    backend_dir = os.path.join(repo_root, cfg["backend_dir"])
    tools_dir = os.path.join(repo_root, "tools", "ai-unit-test-loop")

    class_prefix = cfg["class_prefix"]
    line_min = str(cfg["line_min"])
    branch_min = str(cfg["branch_min"])
    condition_min = str(cfg["condition_min"])
    risk_pass = str(cfg["risk_pass"])
    test_root = cfg["test_root"]
    path_manifest = os.path.join(repo_root, cfg["path_manifest"])

    if not os.path.isdir(backend_dir):
        print(f"[error] backend_dir invalid: {backend_dir}", file=sys.stderr)
        return 2
    if not os.path.exists(path_manifest):
        print(f"[error] path_manifest invalid: {path_manifest}", file=sys.stderr)
        return 2

    if not args.skip_maven:
        rc = run_cmd(["mvn", "-B", "-ntp", "clean", "test", "jacoco:report"], backend_dir)
        if rc != 0:
            print("[error] maven test failed")
            # 仍继续执行 triage，帮助定位原因
    else:
        rc = 0

    coverage_cmd = [
        "python3",
        os.path.join(tools_dir, "scripts", "coverage_gate.py"),
        "--jacoco-xml", "target/site/jacoco/jacoco.xml",
        "--class-prefix", class_prefix,
        "--line-min", line_min,
        "--branch-min", branch_min,
        "--condition-min", condition_min,
    ]
    path_cmd = [
        "python3",
        os.path.join(tools_dir, "scripts", "path_gate.py"),
        "--manifest", path_manifest,
        "--surefire-dir", "target/surefire-reports",
    ]
    audit_cmd = [
        "python3",
        os.path.join(tools_dir, "scripts", "audit_quality.py"),
        "--test-root", test_root,
        "--risk-pass", risk_pass,
    ]
    triage_cmd = [
        "python3",
        os.path.join(tools_dir, "scripts", "triage_failures.py"),
        "--surefire-dir", "target/surefire-reports",
    ]

    coverage_rc = run_cmd(coverage_cmd, backend_dir)
    path_rc = run_cmd(path_cmd, backend_dir)
    audit_rc = run_cmd(audit_cmd, backend_dir)
    triage_rc = run_cmd(triage_cmd, backend_dir)

    coverage_summary = read_json_if_exists(os.path.join(backend_dir, "target/testloop/coverage-gate-summary.json"))
    path_summary = read_json_if_exists(os.path.join(backend_dir, "target/testloop/path-gate-summary.json"))
    audit_summary = read_json_if_exists(os.path.join(backend_dir, "target/testloop/audit-report.json"))
    triage_summary = read_json_if_exists(os.path.join(backend_dir, "target/testloop/triage-report.json"))

    ok = all(code == 0 for code in [rc, coverage_rc, path_rc, audit_rc, triage_rc])
    final_summary = {
        "timestamp": datetime.now().isoformat(),
        "config": args.config,
        "status": "pass" if ok else "fail",
        "steps": {
            "maven": rc == 0,
            "coverage_gate": coverage_rc == 0,
            "path_gate": path_rc == 0,
            "audit": audit_rc == 0,
            "triage": triage_rc == 0,
        },
        "coverage": coverage_summary,
        "path": path_summary,
        "audit": audit_summary,
        "triage": triage_summary,
    }

    output_dir = os.path.join(tools_dir, "outputs")
    os.makedirs(output_dir, exist_ok=True)
    latest_path = os.path.join(output_dir, "latest-summary.json")
    with open(latest_path, "w", encoding="utf-8") as f:
        json.dump(final_summary, f, ensure_ascii=False, indent=2)

    print(f"\n[summary] {latest_path}")
    print(json.dumps(final_summary["steps"], ensure_ascii=False, indent=2))
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
