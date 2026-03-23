"""
GitHub 内容获取服务

功能：
- 仅支持 **公有 GitHub 仓库** 的文件访问
- 支持提取文本文件和代码文件（如 .py, .java, .js, .ts, .md 等）
- 在本文件中提供 main 函数用于本地简单测试，URL 直接写在 main 函数中
"""

from __future__ import annotations

import logging
import os
import re
import shutil
import tempfile
from dataclasses import dataclass
from pathlib import Path
from typing import Optional, Sequence

from langchain_core.documents import Document
import requests

logger = logging.getLogger(__name__)


# 典型文件 URL 示例：
# https://github.com/owner/repo/blob/branch/path/to/file.py
GITHUB_FILE_URL_PATTERN = re.compile(
    r"^https?://github\.com/(?P<owner>[^/]+)/(?P<repo>[^/]+)/blob/(?P<branch>[^/]+)/(?P<path>.+)$"
)

GITHUB_REPO_URL_PATTERN = re.compile(
    r"^https?://github\.com/(?P<owner>[^/]+)/(?P<repo>[^/]+?)(?:\.git)?/?$"
)

@dataclass
class GitHubFileInfo:
    """GitHub 文件信息."""

    owner: str
    repo: str
    branch: str
    path: str


class GitHubFetcher:
    """
    GitHub 公有仓库内容获取服务

    当前实现：
    - 仅支持「文件 URL」：https://github.com/owner/repo/blob/branch/path/to/file.ext
    - 通过将 blob 地址转换为 raw.githubusercontent.com 地址拉取文件内容
    - 支持所有文本类文件（包括代码文件），统一以 UTF-8 为主进行解码
    """

    def __init__(self, timeout: float = 10.0) -> None:
        """
        :param timeout: HTTP 请求超时时间（秒）
        """
        self._timeout = timeout

    def _parse_file_url(self, url: str) -> GitHubFileInfo:
        """
        解析 GitHub 文件 URL，提取 owner / repo / branch / path.

        支持的 URL 形式示例：
        - https://github.com/psf/requests/blob/main/requests/models.py
        """
        match = GITHUB_FILE_URL_PATTERN.match(url)
        if not match:
            raise ValueError(
                f"不支持的 GitHub 文件 URL 格式，仅支持形如 "
                f"'https://github.com/owner/repo/blob/branch/path/to/file' 的地址: {url}"
            )

        return GitHubFileInfo(
            owner=match.group("owner"),
            repo=match.group("repo"),
            branch=match.group("branch"),
            path=match.group("path"),
        )

    def _build_raw_url(self, info: GitHubFileInfo) -> str:
        """
        将 GitHub 文件信息转换为 raw.githubusercontent.com 直连地址.

        例如：
        owner=psf, repo=requests, branch=main, path=README.md
        -> https://raw.githubusercontent.com/psf/requests/main/README.md
        """
        return f"https://raw.githubusercontent.com/{info.owner}/{info.repo}/{info.branch}/{info.path}"

    @staticmethod
    def _parse_repo_root(repo_url: str) -> tuple[str, str]:
        """
        从仓库根路径 URL 中解析 owner/repo.

        支持形式：
        - https://github.com/owner/repo
        - https://github.com/owner/repo/
        - https://github.com/owner/repo.git
        """
        match = GITHUB_REPO_URL_PATTERN.match(repo_url)
        if not match:
            raise ValueError(
                "不支持的 GitHub 仓库 URL 格式，仅支持形如 "
                "'https://github.com/owner/repo' 的地址: "
                f"{repo_url}"
            )
        return match.group("owner"), match.group("repo")

    @staticmethod
    def _normalize_extensions(
        exts: Optional[Sequence[str]],
    ) -> Optional[set[str]]:
        if exts is None:
            return None
        return {("." + e.lstrip(".")).lower() for e in exts if e}

    @staticmethod
    def _clone_repository(repo_url: str) -> str:
        """
        使用 git clone --depth=1 将 GitHub 仓库克隆到临时目录，返回本地路径。

        注意：只拉取最新一次提交，避免传输过大历史。
        优化：添加超时控制和进度日志。
        """
        owner, repo = GitHubFetcher._parse_repo_root(repo_url)
        tmp_dir = tempfile.mkdtemp(prefix="github_repo_")

        logger.info(
            "开始克隆 GitHub 仓库: owner=%s, repo=%s, target_dir=%s",
            owner,
            repo,
            tmp_dir,
        )

        try:
            # 从配置获取超时时间
            try:
                from app.config import settings
                timeout = settings.github_clone_timeout
            except Exception:
                timeout = 300  # 默认5分钟
            
            # 使用 subprocess 执行 git clone，可以更好地控制超时
            import subprocess
            
            clone_url = f"https://github.com/{owner}/{repo}.git"
            cmd = ["git", "clone", "--depth", "1", clone_url, tmp_dir]
            
            # 执行 git clone，设置超时
            process = subprocess.Popen(
                cmd,
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
                cwd=None
            )
            
            try:
                stdout, stderr = process.communicate(timeout=timeout)
                if process.returncode != 0:
                    error_msg = stderr.decode('utf-8', errors='ignore') if stderr else "未知错误"
                    raise RuntimeError(f"Git clone 失败: {error_msg}")
            except subprocess.TimeoutExpired:
                process.kill()
                process.wait()
                raise RuntimeError(f"Git clone 超时（{timeout}秒）")
                
        except subprocess.TimeoutExpired:
            logger.exception("克隆 GitHub 仓库超时")
            shutil.rmtree(tmp_dir, ignore_errors=True)
            raise RuntimeError(f"克隆 GitHub 仓库超时（{timeout}秒）")
        except Exception as exc:
            logger.exception("克隆 GitHub 仓库失败: %s", exc)
            shutil.rmtree(tmp_dir, ignore_errors=True)
            raise RuntimeError(f"克隆 GitHub 仓库失败: {exc}") from exc

        logger.info("克隆 GitHub 仓库完成: %s", tmp_dir)
        return tmp_dir

    def fetch_file_bytes(self, url: str) -> bytes:
        """
        根据 GitHub 文件 URL 获取原始字节内容.

        :param url: GitHub 文件 URL（包含 /blob/）
        :return: 文件二进制内容
        """
        info = self._parse_file_url(url)
        raw_url = self._build_raw_url(info)

        logger.info(
            "开始从 GitHub 拉取文件: owner=%s, repo=%s, branch=%s, path=%s",
            info.owner,
            info.repo,
            info.branch,
            info.path,
        )

        try:
            resp = requests.get(raw_url, timeout=self._timeout)
        except Exception as exc:  # 网络错误
            logger.exception("请求 GitHub 失败: %s", exc)
            raise RuntimeError(f"请求 GitHub 失败: {exc}") from exc

        if resp.status_code != 200:
            raise RuntimeError(
                f"GitHub 返回非 200 状态码: {resp.status_code}, url={raw_url}"
            )

        logger.info("GitHub 文件获取完成，大小：%d 字节", len(resp.content))
        return resp.content

    def fetch_file_text(self, url: str, encoding: Optional[str] = None) -> str:
        """
        获取 GitHub 文本/代码文件内容（以字符串形式返回）.

        :param url: GitHub 文件 URL（包含 /blob/）
        :param encoding: 可选指定编码；默认按以下顺序尝试：
                         1) 指定 encoding
                         2) UTF-8
                         3) 使用 requests 自动推断的 apparent_encoding
        :return: 文件文本内容
        """
        info = self._parse_file_url(url)
        raw_url = self._build_raw_url(info)

        logger.info(
            "开始从 GitHub 拉取文本/代码文件: owner=%s, repo=%s, branch=%s, path=%s",
            info.owner,
            info.repo,
            info.branch,
            info.path,
        )

        try:
            resp = requests.get(raw_url, timeout=self._timeout)
        except Exception as exc:
            logger.exception("请求 GitHub 失败: %s", exc)
            raise RuntimeError(f"请求 GitHub 失败: {exc}") from exc

        if resp.status_code != 200:
            raise RuntimeError(
                f"GitHub 返回非 200 状态码: {resp.status_code}, url={raw_url}"
            )

        # 优先使用显式指定的 encoding
        text: Optional[str] = None
        if encoding:
            try:
                text = resp.content.decode(encoding, errors="replace")
            except LookupError:
                logger.warning("未知编码 %s，回退为 UTF-8 尝试解码", encoding)

        if text is None:
            # 尝试 UTF-8
            try:
                text = resp.content.decode("utf-8")
            except UnicodeDecodeError:
                # 使用 requests 推断的编码
                apparent = resp.apparent_encoding or "utf-8"
                logger.info("UTF-8 解码失败，改用推断编码: %s", apparent)
                text = resp.content.decode(apparent, errors="replace")

        logger.info("GitHub 文本/代码文件获取完成，长度：%d 字符", len(text))
        return text

    def fetch_repository_documents(
        self,
        repo_url: str,
        *,
        file_extensions: Optional[Sequence[str]] = None,
        max_files: Optional[int] = None,
    ) -> list[Document]:
        """
        基于 GitHub 仓库根路径提取代码/文本文件，并组装成 LangChain 的 Document 列表。

        向量化流程中可以直接对返回的 Document 做分块和 embedding。
        """
        # 默认的代码 / 文本文件后缀
        default_exts = {
            ".py",
            ".java",
            ".js",
            ".ts",
            ".tsx",
            ".jsx",
            ".go",
            ".rs",
            ".cs",
            ".php",
            ".rb",
            ".kt",
            ".swift",
            ".c",
            ".h",
            ".cpp",
            ".hpp",
            ".md",
            ".txt",
            ".json",
            ".yml",
            ".yaml",
            ".toml",
        }
        exts = (
            self._normalize_extensions(file_extensions)
            if file_extensions is not None
            else default_exts
        )

        repo_dir = self._clone_repository(repo_url)
        docs: list[Document] = []

        try:
            root_path = Path(repo_dir)
            logger.info(
                "开始遍历仓库文件: root=%s, exts=%s, max_files=%s",
                root_path,
                sorted(exts) if exts is not None else None,
                max_files,
            )

            count = 0
            skipped_large = 0
            skipped_errors = 0
            
            # 从配置中获取最大文件大小限制（如果可用），否则使用默认值
            try:
                from app.config import settings
                max_file_size = settings.github_max_file_size
            except Exception:
                max_file_size = 500 * 1024  # 默认500KB
            
            # 优化：先收集所有符合条件的文件路径，然后批量处理
            # 这样可以更好地控制进度和避免过早中断
            file_paths = []
            for path in root_path.rglob("*"):
                if not path.is_file():
                    continue

                suffix = path.suffix.lower()
                if exts is not None and suffix not in exts:
                    continue

                # 检查文件大小，跳过过大的文件
                try:
                    file_size = path.stat().st_size
                    if file_size > max_file_size:
                        skipped_large += 1
                        if skipped_large <= 10:  # 只记录前10个
                            logger.debug(
                                "跳过过大文件: path=%s, size=%d bytes (限制=%d bytes)",
                                path,
                                file_size,
                                max_file_size,
                            )
                        continue
                except Exception as exc:
                    skipped_errors += 1
                    logger.warning(
                        "获取文件大小失败，已跳过: path=%s, error=%s", path, exc
                    )
                    continue

                file_paths.append((path, file_size))
                
                if max_files is not None and len(file_paths) >= max_files:
                    logger.info(
                        "已达到最大文件数限制: max_files=%d, 已收集=%d",
                        max_files,
                        len(file_paths),
                    )
                    break
            
            print(f"   📋 文件收集完成: 总数={len(file_paths)}, 跳过过大文件={skipped_large}, 跳过错误={skipped_errors}")
            
            logger.info(
                "文件收集完成: 总数=%d, 跳过过大文件=%d, 跳过错误=%d",
                len(file_paths),
                skipped_large,
                skipped_errors,
            )
            
            # 批量处理文件，每处理一定数量记录一次进度
            # 从配置获取进度更新间隔
            try:
                from app.config import settings
                progress_interval = settings.github_progress_update_interval
            except Exception:
                progress_interval = max(1, len(file_paths) // 20)  # 默认每5%记录一次
            
            for idx, (path, file_size) in enumerate(file_paths, 1):
                try:
                    rel_path = path.relative_to(root_path)
                except ValueError:
                    rel_path = Path(os.path.basename(path))

                try:
                    # 直接读取文件（文件大小已经检查过，不会太大，读取通常很快）
                    # 对于大文件，使用 errors='replace' 避免编码错误导致整个任务失败
                    try:
                        content = path.read_text(encoding="utf-8")
                    except UnicodeDecodeError:
                        # UTF-8解码失败，尝试使用 errors='replace' 替换无效字符
                        content = path.read_text(encoding="utf-8", errors="replace")
                        logger.debug(
                            "文件包含非UTF-8字符，已替换: path=%s", path
                        )
                        
                except Exception as exc:
                    skipped_errors += 1
                    logger.warning(
                        "读取文件失败，已跳过: path=%s, error=%s", path, exc
                    )
                    continue

                docs.append(
                    Document(
                        page_content=content,
                        metadata={
                            "path": str(rel_path),
                            "repo_url": repo_url,
                            "file_size": file_size,
                        },
                    )
                )

                count += 1
                
                # 定期记录进度（控制台输出）
                if idx % progress_interval == 0 or idx == len(file_paths):
                    progress_pct = (idx / len(file_paths)) * 100
                    print(f"   📁 文件处理进度: {idx}/{len(file_paths)} ({progress_pct:.1f}%)", end="\r")
                    logger.info(
                        "文件处理进度: %d/%d (%.1f%%)",
                        idx,
                        len(file_paths),
                        progress_pct,
                    )

            logger.info(
                "仓库 Document 组装完成，共生成文档数：%d", len(docs)
            )
            return docs
        finally:
            shutil.rmtree(repo_dir, ignore_errors=True)

    def fetch_repository_text(
        self,
        repo_url: str,
        *,
        file_extensions: Optional[Sequence[str]] = None,
        max_files: Optional[int] = None,
    ) -> str:
        """
        兼容旧用法：在 Document 基础上拼接成一个大文本。
        """
        docs = self.fetch_repository_documents(
            repo_url,
            file_extensions=file_extensions,
            max_files=max_files,
        )

        parts: list[str] = []
        for doc in docs:
            file_path = doc.metadata.get("path") or "unknown"
            parts.append(f"# File: {file_path}\n{doc.page_content}")

        combined = "\n\n".join(parts)
        logger.info(
            "仓库文本拼接完成，文档数：%d，合并文本长度：%d 字符",
            len(docs),
            len(combined),
        )
        return combined


def main() -> None:
    """
    简单测试入口：

    运行方式（示例）：
        python -m app.services.fetcher.github_fetcher

    测试逻辑：
    - 在代码中维护一组公开的 GitHub 文件 URL（包括文本文件与代码文件）
    - 依次拉取并打印前若干字符的内容预览
    """
    logging.basicConfig(
        level=logging.INFO,
        format="%(asctime)s [%(levelname)s] %(name)s - %(message)s",
    )

    fetcher = GitHubFetcher(timeout=15.0)
    preview_len = 500

    # 1. 文件 URL 测试：直接拉取单个文件内容
    file_test_urls = [
        # 文本文件（Markdown）
        "https://github.com/ycc250303/langchain-study/blob/main/README.md",
        # Python 代码文件
        "https://github.com/ycc250303/langchain-study/blob/main/heima/RAG-project/rag.py",
    ]

    for idx, url in enumerate(file_test_urls, start=1):
        print(f"\n==== [文件模式] 开始解析第 {idx} 个 GitHub URL ====")
        print(f"URL: {url}\n")
        try:
            text = fetcher.fetch_file_text(url)
        except Exception as exc:  # 在测试入口中捕获所有异常并打印
            logger.exception("拉取 GitHub 文件失败: %s", exc)
            print(f"拉取失败: {exc}")
            continue

        preview = text[:preview_len]
        print("==== 内容预览 ====")
        print(preview)
        if len(text) > preview_len:
            print(f"\n...（总长度 {len(text)} 字符，仅展示前 {preview_len} 字符）")

    # 2. 仓库根路径测试：Clone 整个仓库并按扩展名提取内容
    repo_test_urls = [
        "https://github.com/ycc250303/SmartHire",
    ]

    for idx, url in enumerate(repo_test_urls, start=1):
        print(f"\n==== [仓库模式] 开始解析第 {idx} 个 GitHub 仓库 URL ====")
        print(f"Repo URL: {url}\n")
        try:
            # 为了避免测试时过慢，这里限制最多处理的文件数
            text = fetcher.fetch_repository_text(
                url,
                max_files=500,
            )
        except Exception as exc:
            logger.exception("拉取 GitHub 仓库失败: %s", exc)
            print(f"拉取仓库失败: {exc}")
            continue

        preview = text[:preview_len]
        print("==== 仓库内容预览 ====")
        print(preview)
        if len(text) > preview_len:
            print(f"\n...（总长度 {len(text)} 字符，仅展示前 {preview_len} 字符）")


if __name__ == "__main__":
    main()
