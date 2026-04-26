#!/usr/bin/env python3
import argparse
import json
import os
import re
import sys
import xml.etree.ElementTree as ET
from glob import glob


def parse_args():
    parser = argparse.ArgumentParser(description="Path coverage gate from manifest + surefire reports")
    parser.add_argument("--manifest", required=True, help="Path manifest JSON file")
    parser.add_argument("--surefire-dir", required=True, help="Path to surefire reports directory")
    parser.add_argument("--out", default="target/testloop/path-gate-summary.json")
    return parser.parse_args()


def load_test_cases(surefire_dir):
    cases = []
    for xml_path in glob(os.path.join(surefire_dir, "TEST-*.xml")):
        try:
            root = ET.parse(xml_path).getroot()
        except ET.ParseError:
            continue
        for tc in root.findall(".//testcase"):
            classname = tc.attrib.get("classname", "")
            name = tc.attrib.get("name", "")
            full_name = f"{classname}#{name}"
            cases.append(full_name)
    return cases


def main():
    args = parse_args()
    if not os.path.exists(args.manifest):
        print(f"[path_gate] manifest not found: {args.manifest}", file=sys.stderr)
        return 2
    if not os.path.isdir(args.surefire_dir):
        print(f"[path_gate] surefire dir not found: {args.surefire_dir}", file=sys.stderr)
        return 2

    with open(args.manifest, "r", encoding="utf-8") as f:
        manifest = json.load(f)
    required_paths = manifest.get("requiredPaths", [])
    min_path_count = int(manifest.get("pathCountMin", len(required_paths)))

    test_cases = load_test_cases(args.surefire_dir)
    covered = []
    uncovered = []

    for path_def in required_paths:
        path_id = path_def.get("id")
        matchers = path_def.get("matchers", [])
        hit = False
        for matcher in matchers:
            pattern = re.compile(matcher)
            if any(pattern.search(tc) for tc in test_cases):
                hit = True
                break
        (covered if hit else uncovered).append(path_id)

    status = "pass"
    if len(covered) < min_path_count or uncovered:
        status = "fail"

    result = {
        "status": status,
        "pathCount": len(covered),
        "pathCountMin": min_path_count,
        "coveredPaths": covered,
        "uncoveredPaths": uncovered,
        "totalTestsDetected": len(test_cases),
    }

    os.makedirs(os.path.dirname(args.out), exist_ok=True)
    with open(args.out, "w", encoding="utf-8") as f:
        json.dump(result, f, ensure_ascii=False, indent=2)

    print(json.dumps(result, ensure_ascii=False, indent=2))
    return 0 if status == "pass" else 1


if __name__ == "__main__":
    sys.exit(main())
