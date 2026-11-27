#!/usr/bin/env python3
"""从固定 Git 提交生成系统知识文档、内容摘要和推广清理报告。"""
from __future__ import annotations

import argparse
from collections import Counter
from dataclasses import dataclass
import fnmatch
import hashlib
import json
from pathlib import Path, PurePosixPath
import re
import shutil
import subprocess
from datetime import datetime, timezone
from tempfile import TemporaryDirectory
from uuid import uuid4

from markdown_it import MarkdownIt
import yaml


PARSER = MarkdownIt("commonmark").enable("table")
INCLUDE = re.compile(r"<!--\s*@include:\s*(.*?)\s*-->")
HANDBOOK_COMMENT = re.compile(
    r'^[ \t]*\* 微信搜 JavaGuide 回复"面试突击"即可免费领取个人原创的 Java 面试手册[^\n]*\n', re.M)


@dataclass(frozen=True)
class CleanedDocument:
    title: str
    text: str
    changes: dict[str, int]
    code_blocks: int


def digest(text: str) -> str:
    return hashlib.sha256(text.encode("utf-8")).hexdigest()


def git(repo: Path, *args: str) -> bytes:
    return subprocess.run(["git", "-C", str(repo), *args], check=True, stdout=subprocess.PIPE).stdout


def read_config(path: Path) -> dict:
    config = json.loads(path.read_text(encoding="utf-8"))
    if config["scope"] != "SYSTEM":
        raise ValueError("JavaGuide 文档范围必须为 SYSTEM")
    for key in ("include_directories", "include_files", "exclude_files"):
        for name in config[key]:
            relative_path(name)
    for alias, canonical in config["duplicate_files"].items():
        relative_path(alias)
        relative_path(canonical)
    return config


def relative_path(name: str) -> PurePosixPath:
    path = PurePosixPath(name)
    if not name or path.is_absolute() or ".." in path.parts or "\\" in name or path.as_posix() != name:
        raise ValueError(f"非法文档相对路径：{name}")
    return path


def source_documents(repo: Path, commit: str, config: dict) -> tuple[dict[str, str], list[dict]]:
    """直接读取 Git 对象，避免工作目录修改及大小写文件冲突影响结果。"""
    entries = {}
    for entry in git(repo, "ls-tree", "-rz", commit, "--", "docs").split(b"\0"):
        if entry:
            attributes, name = entry.decode("utf-8").split("\t", 1)
            mode, kind, identifier = attributes.split()
            entries[name.removeprefix("docs/")] = (mode, kind, identifier)
    for name in config["include_directories"]:
        if not any(p.startswith(name + "/") for p in entries):
            raise ValueError(f"选取目录不存在：{name}")
    for name in config["include_files"]:
        if name not in entries:
            raise ValueError(f"选取文件不存在：{name}")
    documents, excluded = {}, []
    for name, (mode, kind, identifier) in sorted(entries.items()):
        selected = name in config["include_files"] or any(
            name.startswith(directory + "/") for directory in config["include_directories"])
        if not selected or not name.endswith(".md"):
            continue
        reason = None
        if PurePosixPath(name).name == "README.md":
            reason = "目录导航"
        if name in config["exclude_files"]:
            reason = "资料介绍页或内部维护文件"
        if name in config["duplicate_files"]:
            canonical = config["duplicate_files"][name]
            if canonical not in entries or identifier != entries[canonical][2]:
                raise ValueError(f"重复文档内容已变化：{name} / {canonical}")
            reason = f"相同内容保留 {canonical}"
        if reason:
            excluded.append({"source_path": "docs/" + name, "reason": reason})
            continue
        relative_path(name)
        if kind != "blob" or mode not in ("100644", "100755"):
            raise ValueError(f"文档必须为普通文件：{name}")
        documents[name] = git(repo, "show", f"{commit}:docs/{name}").decode("utf-8")
    if not documents:
        raise ValueError("未选中任何知识文档")
    names = [name.casefold() for name in documents]
    if len(names) != len(set(names)):
        raise ValueError("输出文档存在大小写路径冲突")
    return documents, excluded


def split_metadata(raw: str) -> tuple[dict, str]:
    raw = raw.replace("\r\n", "\n").replace("\r", "\n")
    match = re.match(r"\A---\n(.*?)\n---(?:\n|$)", raw, re.S)
    if not match:
        raise ValueError("源文档缺少 YAML 元数据")
    metadata = yaml.safe_load(match[1])
    if not isinstance(metadata, dict) or not isinstance(metadata.get("title"), str) or not metadata["title"].strip():
        raise ValueError("源文档缺少有效标题")
    if "\n" in metadata["title"]:
        raise ValueError("文档标题必须为单行")
    return metadata, raw[match.end():]


def remove_ranges(text: str, ranges: list[tuple[int, int]]) -> str:
    lines = text.splitlines(keepends=True)
    removed = {index for start, end in ranges for index in range(start, end)}
    return "".join(line for index, line in enumerate(lines) if index not in removed)


def applies(rule: dict, path: str) -> bool:
    return any(fnmatch.fnmatchcase(path, pattern) for pattern in rule["paths"])


def clean_body(path: str, text: str, config: dict) -> tuple[str, Counter]:
    """按原文位置处理章节与段落；代码块仅移除已确认的推广注释行。"""
    changes = Counter()
    for rule in config["section_rules"]:
        if not applies(rule, path):
            continue
        tokens = PARSER.parse(text)
        for index, token in enumerate(tokens):
            if token.type != "heading_open" or tokens[index + 1].content != rule["title"]:
                continue
            start, end = token.map[0], len(text.splitlines())
            for following in tokens[index + 1:]:
                if following.type == "heading_open" and int(following.tag[1:]) <= int(token.tag[1:]):
                    end = following.map[0]
                    break
            section = "".join(text.splitlines(keepends=True)[start:end]).strip("\n")
            if digest(section) != rule["content_hash"]:
                raise ValueError(f"待删除章节内容已变化，需要检查：{path} / {rule['title']}")
            text = remove_ranges(text, [(start, end)])
            changes[rule["id"]] += 1
            break

    ranges = []
    for token in PARSER.parse(text):
        if token.type == "html_block":
            for match in INCLUDE.finditer(token.content):
                snippet = match[1]
                if snippet not in config["drop_snippets"]:
                    raise ValueError(f"未知引用片段：{path} / {snippet}")
            if INCLUDE.search(token.content):
                # 同一 HTML 块中其余内容继续保留，按行处理引用指令。
                lines = text.splitlines(keepends=True)
                for line in range(*token.map):
                    if INCLUDE.fullmatch(lines[line].strip()):
                        ranges.append((line, line + 1))
                        changes["drop_promo_snippet"] += 1
        if token.type == "inline":
            for rule in config["block_rules"]:
                if applies(rule, path) and re.search(rule["pattern"], token.content):
                    ranges.append(tuple(token.map))
                    changes[rule["id"]] += 1
                    break
    text = remove_ranges(text, ranges)

    lines = text.splitlines(keepends=True)
    protected = set()
    for token in PARSER.parse(text):
        if token.type in ("fence", "code_block"):
            protected.update(range(*token.map))
    for index, line in enumerate(lines):
        if index in protected:
            cleaned, count = HANDBOOK_COMMENT.subn("", line)
            lines[index] = cleaned
            changes["drop_handbook_comment"] += count
            continue
        for rule in config["text_rules"]:
            if applies(rule, path):
                line, count = re.subn(rule["pattern"], rule["replacement"], line)
                changes[rule["id"]] += count
        lines[index] = line
    return "".join(lines).strip("\n") + "\n", +changes


def check_cleaned(path: str, before: str, after: str, config: dict) -> int:
    """核对代码、已知推广特征和正文有效性；错误立即终止整批输出。"""
    tokens_before, tokens_after = PARSER.parse(before), PARSER.parse(after)
    code_before = [(t.type, t.info, HANDBOOK_COMMENT.sub("", t.content))
                   for t in tokens_before if t.type in ("fence", "code_block")]
    code_after = [(t.type, t.info, t.content)
                  for t in tokens_after if t.type in ("fence", "code_block")]
    if code_before != code_after:
        raise ValueError(f"技术代码发生变化：{path}")
    for token in tokens_after:
        if token.type in ("inline", "html_block"):
            inspected = token.content + "\n" + "\n".join(
                str(value) for child in token.children or [] for value in child.attrs.values())
            for pattern in config["forbidden_patterns"]:
                if re.search(pattern, inspected, re.I):
                    raise ValueError(f"残留推广内容：{path} / {token.content[:220]}")
    if not any(t.type in ("inline", "fence", "code_block") and t.content.strip() for t in tokens_after):
        raise ValueError(f"清理后正文为空：{path}")
    return len(code_after)


def clean_document(path: str, raw: str, config: dict) -> CleanedDocument:
    metadata, body = split_metadata(raw)
    cleaned, changes = clean_body(path, body, config)
    code_blocks = check_cleaned(path, body, cleaned, config)
    repeated, _ = clean_body(path, cleaned, config)
    if repeated != cleaned:
        raise ValueError(f"重复清理结果不一致：{path}")
    title = metadata["title"].strip()
    return CleanedDocument(title, f"# {title}\n\n{cleaned}", dict(changes), code_blocks)


def build(repo: Path, output: Path, config_path: Path, ref: str = "HEAD") -> dict:
    repo, output = repo.resolve(), output.resolve()
    if output.exists():
        raise ValueError(f"输出目录已存在：{output}")
    if output.is_relative_to(repo):
        raise ValueError("清理结果必须保存到源仓库之外")
    config = read_config(config_path)
    commit = git(repo, "rev-parse", "--verify", ref + "^{commit}").decode().strip()
    sources, excluded = source_documents(repo, commit, config)
    cleaned = {name: clean_document(name, raw, config) for name, raw in sources.items()}
    source_url = config["repository"].removesuffix(".git")
    manifest, files, totals = [], [], Counter()
    for name, document in cleaned.items():
        manifest.append({
            "source_path": "docs/" + name, "raw_path": "raw/docs/" + name,
            "output_path": "docs/" + name,
            "source_url": f"{source_url}/blob/{commit}/docs/{name}",
            "scope": config["scope"], "title": document.title,
            "raw_hash": digest(sources[name]), "content_hash": digest(document.text),
        })
        totals.update(document.changes)
        files.append({"source_path": "docs/" + name, "applied_rules": document.changes})
    processing = {"source_commit": commit, "repository": source_url, "hash_algorithm": "SHA-256",
                  "rules_hash": digest(config_path.read_text(encoding="utf-8")),
                  "cleaner_hash": digest(Path(__file__).read_text(encoding="utf-8"))}
    report = {**processing, "document_count": len(cleaned), "excluded_count": len(excluded),
              "code_blocks": sum(document.code_blocks for document in cleaned.values()),
              "rules_applied": dict(totals), "validation": {
                  "known_promotion_matches": 0, "code_preserved": True, "idempotent": True},
              "files": files, "excluded": excluded}
    output.parent.mkdir(parents=True, exist_ok=True)
    staging = output.with_name("." + output.name + ".staging")
    staging.mkdir()
    for name, document in cleaned.items():
        for directory, content in (("raw/docs", sources[name]), ("docs", document.text)):
            target = staging / directory / name
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(content.encode("utf-8"))
    checksums = []
    for item in manifest:
        for path_key, hash_key in (("raw_path", "raw_hash"), ("output_path", "content_hash")):
            actual = hashlib.sha256((staging / item[path_key]).read_bytes()).hexdigest()
            if actual != item[hash_key]:
                raise ValueError(f"输出文件摘要不一致：{item[path_key]}")
            checksums.append(f"{actual}  {item[path_key]}\n")
    for name, value in (("manifest.json", {**processing, "documents": manifest}), ("report.json", report)):
        (staging / name).write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    (staging / "SHA256SUMS").write_text("".join(checksums), encoding="utf-8")
    (staging / "LICENSE.JavaGuide").write_bytes(git(repo, "show", commit + ":LICENSE"))
    staging.rename(output)
    return report


def prune_versions(versions: Path, current: Path | None) -> None:
    """只清理资料工具管理的旧结果，当前成功目录始终保留。"""
    for path in versions.iterdir():
        if path.is_symlink() or not path.is_dir():
            raise ValueError(f"结果目录中存在非法文件：{path}")
        if path != current:
            shutil.rmtree(path)


def refresh(root: Path, config_path: Path) -> dict:
    """在外层任务锁内更新资料；原子切换成功目录后仅保留最新正文及两条成功摘要。"""
    root = root.resolve()
    versions = root / "versions"
    if versions.is_symlink():
        raise ValueError("versions 必须为资料目录内的真实目录")
    versions.mkdir(parents=True, exist_ok=True)
    pointer = root / "current"
    history, current = [], None
    if pointer.exists() or pointer.is_symlink():
        if not pointer.is_symlink():
            raise ValueError("current 必须为工具管理的成功目录链接")
        current = pointer.resolve(strict=True)
        if current.parent != versions or not current.is_dir():
            raise ValueError("current 必须指向 versions 内的直接子目录")
        history = json.loads((current / "success-history.json").read_text(encoding="utf-8"))
        if not isinstance(history, list) or not 1 <= len(history) <= 2:
            raise ValueError("成功记录必须包含最近一次或两次成功摘要")
    prune_versions(versions, current)
    for path in root.glob(".work-*"):
        if path.is_symlink() or not path.is_dir():
            raise ValueError(f"非法工作目录：{path}")
        shutil.rmtree(path)
    started_at = datetime.now(timezone.utc)
    run_id = started_at.strftime("%Y%m%dT%H%M%SZ-") + uuid4().hex[:12]
    print(f"status=started operationId={run_id} startedAt={started_at.isoformat()}", flush=True)
    config = read_config(config_path)
    source_url = config["repository"].removesuffix(".git") + ".git"
    with TemporaryDirectory(prefix=".work-", dir=root) as work:
        workspace = Path(work)
        repo, output = workspace / "repository.git", workspace / "result"
        subprocess.run(["timeout", "900", "git", "-c", "http.version=HTTP/1.1", "clone",
                        "--bare", "--depth=1", "--single-branch", "--branch", "main",
                        source_url, str(repo)], check=True)
        report = build(repo, output, config_path)
        subprocess.run(["sha256sum", "--check", "--strict", "--quiet", "SHA256SUMS"],
                       cwd=output, check=True)
        state = {"run_id": run_id, "started_at": started_at.isoformat(),
                 "completed_at": datetime.now(timezone.utc).isoformat(), "status": "success",
                 **{key: report[key] for key in ("source_commit", "rules_hash", "cleaner_hash",
                     "document_count", "excluded_count", "code_blocks", "rules_applied", "validation")}}
        (output / "success-history.json").write_text(
            json.dumps([*history[-1:], state], ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        target = versions / run_id
        output.rename(target)
        link = workspace / "current-link"
        link.symlink_to(Path("versions") / run_id, target_is_directory=True)
        link.replace(pointer)
        prune_versions(versions, target)
    print(json.dumps(state, ensure_ascii=False), flush=True)
    return state


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repo", type=Path)
    parser.add_argument("--output", type=Path)
    parser.add_argument("--refresh-root", type=Path, help="在任务锁内刷新服务器资料目录")
    parser.add_argument("--ref", default="HEAD")
    parser.add_argument("--config", type=Path, default=Path(__file__).with_name("javaguide_sources.json"))
    args = parser.parse_args()
    if args.refresh_root:
        if args.repo or args.output or args.ref != "HEAD":
            parser.error("--refresh-root 不与 --repo、--output 或 --ref 同时使用")
        refresh(args.refresh_root, args.config)
        return
    if not args.repo or not args.output:
        parser.error("导出时必须同时指定 --repo 和 --output")
    report = build(args.repo, args.output, args.config, args.ref)
    print(json.dumps({"source_commit": report["source_commit"], "documents": report["document_count"],
                      "code_blocks": report["code_blocks"], "output": str(args.output.resolve())}, ensure_ascii=False))


if __name__ == "__main__":
    main()
