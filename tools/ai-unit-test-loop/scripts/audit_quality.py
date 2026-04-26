#!/usr/bin/env python3
import argparse
import json
import os
import re
import sys
from glob import glob


ASSERT_PATTERN = re.compile(r"\bassert[A-Za-z]+\s*\(")
WEAK_ASSERT_PATTERN = re.compile(r"\b(assertTrue|assertNotNull|assertDoesNotThrow)\s*\(")
NEGATIVE_HINT_PATTERN = re.compile(r"assertThrows|异常|invalid|wrong|error|fail", re.IGNORECASE)


def parse_args():
    parser = argparse.ArgumentParser(description="Lightweight anti-cheat audit for unit tests")
    parser.add_argument("--test-root", required=True, help="src/test/java root")
    parser.add_argument("--risk-pass", type=int, default=80, help="minimum risk score to pass")
    parser.add_argument("--out", default="target/testloop/audit-report.json")
    return parser.parse_args()


def list_test_files(test_root):
    return glob(os.path.join(test_root, "**", "*Test.java"), recursive=True)


def main():
    args = parse_args()
    files = list_test_files(args.test_root)
    total_assertions = 0
    weak_assertions = 0
    total_test_methods = 0
    negative_test_methods = 0

    for path in files:
        with open(path, "r", encoding="utf-8") as f:
            content = f.read()
        assertions = ASSERT_PATTERN.findall(content)
        total_assertions += len(assertions)
        weak_assertions += len(WEAK_ASSERT_PATTERN.findall(content))
        total_test_methods += content.count("@Test")
        negative_test_methods += len(NEGATIVE_HINT_PATTERN.findall(content))

    weak_ratio = 0.0 if total_assertions == 0 else weak_assertions / total_assertions
    negative_ratio = 0.0 if total_test_methods == 0 else min(1.0, negative_test_methods / total_test_methods)
    happy_path_dominance = 1.0 - negative_ratio

    must_fix = []
    advice = []
    score = 100

    if weak_ratio > 0.20:
        must_fix.append("弱断言占比过高（>20%）")
        score -= 15
    if happy_path_dominance > 0.70:
        must_fix.append("happy-path 占比过高，异常/边界路径不足")
        score -= 15
    if total_test_methods == 0:
        must_fix.append("未检测到有效测试方法")
        score -= 30
    if total_assertions < total_test_methods:
        advice.append("建议每个测试至少有 1 个业务断言")
        score -= 5

    score = max(0, score)
    passed = score >= args.risk_pass and not must_fix

    result = {
        "riskScore": score,
        "pass": passed,
        "metrics": {
            "weakAssertionRatio": round(weak_ratio, 4),
            "happyPathDominanceRatio": round(happy_path_dominance, 4),
            "testMethodCount": total_test_methods,
            "assertionCount": total_assertions,
        },
        "mustFix": must_fix,
        "advice": advice,
    }

    os.makedirs(os.path.dirname(args.out), exist_ok=True)
    with open(args.out, "w", encoding="utf-8") as f:
        json.dump(result, f, ensure_ascii=False, indent=2)

    print(json.dumps(result, ensure_ascii=False, indent=2))
    return 0 if passed else 1


if __name__ == "__main__":
    sys.exit(main())
