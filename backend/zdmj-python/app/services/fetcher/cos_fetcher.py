"""
COS 内容获取与 PDF 文本解析服务

功能：
- 根据腾讯云 COS 文件访问 URL 下载文件
- 使用 Apache Tika 解析 PDF 文件并提取文本内容
- 在本文件中提供 main 函数用于本地简单测试
"""

from __future__ import annotations

import logging
import re
from dataclasses import dataclass
from typing import Optional

from qcloud_cos import CosConfig, CosS3Client  # type: ignore
from tika import parser as tika_parser  # type: ignore

from app.config import settings

logger = logging.getLogger(__name__)


COS_URL_PATTERN = re.compile(
    r"^https?://(?P<bucket>[^.]+)\.(?P<service>cos|cos-internal)\.(?P<region>[^.]+)\.myqcloud\.com/(?P<key>.+)$"
)


@dataclass
class COSCredentials:
    """COS 鉴权信息."""

    secret_id: str
    secret_key: str


class COSFetcher:
    """
    腾讯云 COS 内容获取服务

    目前实现：
    - 仅支持 PDF 文件：下载后通过 Tika 提取文本
    """

    def __init__(
        self,
        credentials: Optional[COSCredentials] = None,
    ) -> None:
        # 优先使用传入的 credentials，其次尝试从全局配置读取
        if credentials is None:
            try:
                secret_id = getattr(settings, "cos_secret_id")
                secret_key = getattr(settings, "cos_secret_key")
            except AttributeError as exc:  # 配置中未定义 COS 密钥字段
                raise RuntimeError(
                    "COS 配置信息缺失，请在 app.config.Settings 中添加 cos_secret_id / cos_secret_key，"
                    "或在初始化 COSFetcher 时显式传入 COSCredentials"
                ) from exc

            if not secret_id or not secret_key:
                raise RuntimeError("COS 密钥为空，请正确配置 cos_secret_id / cos_secret_key")

            credentials = COSCredentials(secret_id=secret_id, secret_key=secret_key)

        self._credentials = credentials

    def _parse_cos_url(self, url: str) -> tuple[str, str, str]:
        """
        从 COS 访问 URL 中解析 bucket、region、key.

        典型 URL 示例：
        - https://example-bucket-123456.cos.ap-beijing.myqcloud.com/path/to/file.pdf
        """
        match = COS_URL_PATTERN.match(url)
        if not match:
            raise ValueError(f"不支持的 COS URL 格式: {url}")

        bucket = match.group("bucket")
        region = match.group("region")
        key = match.group("key")
        return bucket, region, key

    def _build_client(self, region: str) -> CosS3Client:
        """根据 region 构建 COS 客户端."""
        config = CosConfig(
            Region=region,
            SecretId=self._credentials.secret_id,
            SecretKey=self._credentials.secret_key,
            Token=None,
        )
        client = CosS3Client(config)
        return client

    def fetch_pdf_bytes(self, url: str) -> bytes:
        """
        根据 COS 访问 URL 下载 PDF 文件的字节内容.

        :param url: COS 文件访问 URL
        :return: PDF 二进制数据
        """
        bucket, region, key = self._parse_cos_url(url)
        client = self._build_client(region)

        logger.info("开始从 COS 下载文件: bucket=%s, region=%s, key=%s", bucket, region, key)

        response = client.get_object(Bucket=bucket, Key=key)
        body = response["Body"].get_raw_stream().read()

        logger.info("COS 文件下载完成，大小：%d 字节", len(body))
        return body

    def extract_text_from_pdf_bytes(self, pdf_bytes: bytes) -> str:
        """
        使用 Apache Tika 从 PDF 二进制数据中提取文本.

        :param pdf_bytes: PDF 文件二进制内容
        :return: 提取出的纯文本
        """
        # Tika 第一次调用会尝试启动 Java 进程，可能稍慢
        parsed = tika_parser.from_buffer(pdf_bytes)
        text = parsed.get("content") or ""
        # 做一个简单的清洗，去掉首尾空白
        cleaned = text.strip()
        logger.info("Tika 解析完成，文本长度：%d 字符", len(cleaned))
        return cleaned

    def fetch_pdf_text(self, url: str) -> str:
        """
        综合方法：通过 COS URL 下载 PDF 并返回解析后的文本.

        :param url: COS 文件访问 URL
        :return: 解析后的文本内容
        """
        pdf_bytes = self.fetch_pdf_bytes(url)
        return self.extract_text_from_pdf_bytes(pdf_bytes)


def main() -> None:
    """
    简单测试入口：

    运行方式（示例）：
        python -m app.services.fetcher.cos_fetcher

    在代码中维护一个 COS PDF URL 测试列表，依次解析并打印前若干字符的解析结果。
    """
    logging.basicConfig(
        level=logging.INFO,
        format="%(asctime)s [%(levelname)s] %(name)s - %(message)s",
    )

    # 将下面的示例 URL 替换为你自己的 COS PDF 访问地址
    test_urls = [
        "https://zdmj-1381832847.cos.ap-shanghai.myqcloud.com/resume-1/尹诚成-简历-1771922605040-f7c7182e8ded40f89a0a77031ef90d21.pdf"
    ]

    if not test_urls:
        print("当前测试 URL 列表为空，请在 content_fetcher.main 中填入实际 COS PDF URL 后再运行。")
        return

    fetcher = COSFetcher()
    preview_len = 500

    for idx, url in enumerate(test_urls, start=1):
        print(f"\n==== 开始解析第 {idx} 个 URL ====")
        print(f"URL: {url}\n")
        try:
            text = fetcher.fetch_pdf_text(url)
        except Exception as exc:  # 在测试入口中捕获所有异常并打印
            logger.exception("拉取/解析 COS PDF 失败: %s", exc)
            print(f"拉取/解析失败: {exc}")
            continue

        preview = text[:preview_len]
        print("==== 解析结果预览 ====")
        print(preview)
        if len(text) > preview_len:
            print(f"\n...（总长度 {len(text)} 字符，仅展示前 {preview_len} 字符）")


if __name__ == "__main__":
    main()
