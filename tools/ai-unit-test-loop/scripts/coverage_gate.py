#!/usr/bin/env python3
import argparse
import json
import os
import sys
import xml.etree.ElementTree as ET


def safe_ratio(covered: int, missed: int) -> float:
    total = covered + missed
    return 1.0 if total == 0 else covered / total


def parse_args():
    parser = argparse.ArgumentParser(description="JaCoCo coverage gate for module scope")
    parser.add_argument("--jacoco-xml", required=True, help="Path to jacoco.xml")
    parser.add_argument(
        "--class-prefix",
        default="com/zdmj/userAuthService/service/impl/,com/zdmj/userAuthService/util/",
        help="Comma-separated internal class prefixes",
    )
    parser.add_argument("--line-min", type=float, default=0.85)
    parser.add_argument("--branch-min", type=float, default=0.75)
    parser.add_argument("--condition-min", type=float, default=0.70)
    parser.add_argument("--out", default="target/testloop/coverage-gate-summary.json")
    return parser.parse_args()


def main():
    args = parse_args()
    prefixes = [prefix.strip() for prefix in args.class_prefix.split(",") if prefix.strip()]
    if not prefixes:
        prefixes = ["com/zdmj/userAuthService/service/impl/"]

    if not os.path.exists(args.jacoco_xml):
        print(f"[coverage_gate] jacoco xml not found: {args.jacoco_xml}", file=sys.stderr)
        return 2

    tree = ET.parse(args.jacoco_xml)
    root = tree.getroot()

    line_missed = line_covered = 0
    branch_missed = branch_covered = 0
    matched_classes = 0

    for pkg in root.findall("package"):
        pkg_name = pkg.attrib.get("name", "")
        for clazz in pkg.findall("class"):
            class_name = clazz.attrib.get("name", "")
            full_name = class_name if class_name.startswith(pkg_name) else f"{pkg_name}/{class_name.split('/')[-1]}"
            if not any(full_name.startswith(prefix) for prefix in prefixes):
                continue
            matched_classes += 1
            for counter in clazz.findall("counter"):
                counter_type = counter.attrib.get("type")
                missed = int(counter.attrib.get("missed", "0"))
                covered = int(counter.attrib.get("covered", "0"))
                if counter_type == "LINE":
                    line_missed += missed
                    line_covered += covered
                elif counter_type == "BRANCH":
                    branch_missed += missed
                    branch_covered += covered

    line_ratio = safe_ratio(line_covered, line_missed)
    branch_ratio = safe_ratio(branch_covered, branch_missed)
    condition_ratio = branch_ratio

    result = {
        "status": "pass",
        "matchedClasses": matched_classes,
        "classPrefixes": prefixes,
        "coverage": {
            "line": round(line_ratio, 4),
            "branch": round(branch_ratio, 4),
            "condition": round(condition_ratio, 4),
        },
        "thresholds": {
            "line": args.line_min,
            "branch": args.branch_min,
            "condition": args.condition_min,
        },
        "gaps": [],
    }

    if matched_classes == 0:
        result["status"] = "fail"
        result["gaps"].append({
            "metric": "scope",
            "actual": 0,
            "required": 1,
            "suggestion": "未匹配到目标类，请检查 class-prefix",
        })

    checks = [
        ("line", line_ratio, args.line_min),
        ("branch", branch_ratio, args.branch_min),
        ("condition", condition_ratio, args.condition_min),
    ]
    for metric, actual, required in checks:
        if actual < required:
            result["status"] = "fail"
            result["gaps"].append({
                "metric": metric,
                "actual": round(actual, 4),
                "required": required,
                "suggestion": f"{metric} 覆盖率不足，补充异常与边界路径测试",
            })

    os.makedirs(os.path.dirname(args.out), exist_ok=True)
    with open(args.out, "w", encoding="utf-8") as f:
        json.dump(result, f, ensure_ascii=False, indent=2)

    print(json.dumps(result, ensure_ascii=False, indent=2))
    return 0 if result["status"] == "pass" else 1


if __name__ == "__main__":
    sys.exit(main())
