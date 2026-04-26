#!/usr/bin/env python3
import argparse
import json
import os
import re
import sys
import xml.etree.ElementTree as ET
from glob import glob


ENV_PATTERNS = [
    r"Failed to load ApplicationContext",
    r"Connection refused",
    r"Could not connect",
    r"NoSuchBeanDefinitionException",
    r"Port.*already in use",
]

TEST_PATTERNS = [
    r"Argument\(s\) are different",
    r"Wanted but not invoked",
    r"Unnecessary stubbings",
    r"AssertionFailedError",
    r"expected:.*but was:",
]

BUSINESS_PATTERNS = [
    r"BusinessException",
    r"ErrorCode",
    r"USER_",
    r"CAPTCHA_",
    r"PASSWORD_",
]


def parse_args():
    parser = argparse.ArgumentParser(description="Classify failed tests by failure type")
    parser.add_argument("--surefire-dir", required=True)
    parser.add_argument("--out", default="target/testloop/triage-report.json")
    return parser.parse_args()


def classify(text: str) -> str:
    for pattern in ENV_PATTERNS:
        if re.search(pattern, text, re.IGNORECASE):
            return "environment"
    for pattern in TEST_PATTERNS:
        if re.search(pattern, text, re.IGNORECASE):
            return "test_code"
    for pattern in BUSINESS_PATTERNS:
        if re.search(pattern, text, re.IGNORECASE):
            return "business_defect"
    return "test_code"


def build_actions(category: str):
    if category == "environment":
        return ["修复测试环境或依赖配置", "确保测试不依赖外网与真实服务"]
    if category == "business_defect":
        return ["确认规格与断言一致", "提交业务缺陷并附最小复现测试"]
    return ["修正 mock 与测试数据前置条件", "补充关键断言与交互验证"]


def main():
    args = parse_args()
    if not os.path.isdir(args.surefire_dir):
        print(f"[triage] surefire dir not found: {args.surefire_dir}", file=sys.stderr)
        return 2

    cases = []
    for xml_path in glob(os.path.join(args.surefire_dir, "TEST-*.xml")):
        try:
            root = ET.parse(xml_path).getroot()
        except ET.ParseError:
            continue
        for testcase in root.findall(".//testcase"):
            failure = testcase.find("failure")
            error = testcase.find("error")
            node = failure if failure is not None else error
            if node is None:
                continue
            text = ((node.attrib.get("message", "") or "") + "\n" + (node.text or "")).strip()
            category = classify(text)
            cases.append({
                "testCase": f"{testcase.attrib.get('classname', '')}#{testcase.attrib.get('name', '')}",
                "category": category,
                "evidence": [text[:400] if text else "无详细错误文本"],
                "fixActions": build_actions(category),
            })

    summary = {"environment": 0, "test_code": 0, "business_defect": 0}
    for case in cases:
        summary[case["category"]] += 1

    result = {"status": "triaged", "cases": cases, "summary": summary}
    os.makedirs(os.path.dirname(args.out), exist_ok=True)
    with open(args.out, "w", encoding="utf-8") as f:
        json.dump(result, f, ensure_ascii=False, indent=2)

    print(json.dumps(result, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    sys.exit(main())
