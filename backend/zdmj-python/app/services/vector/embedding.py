"""
千问 Embedding 向量化服务

功能：
- 使用阿里云千问 text-embedding-v4 模型生成文本向量
- 支持批量处理，提高效率
- 返回 1024 维向量，适配 pgvector 存储
- 支持重试机制和错误处理
"""

from __future__ import annotations

import logging
import time
from typing import Optional, Sequence

import dashscope  # type: ignore
from dashscope import TextEmbedding  # type: ignore

from app.config import settings

logger = logging.getLogger(__name__)


class QwenEmbedding:
    """
    千问 Embedding 向量化服务

    使用 text-embedding-v4 模型将文本转换为 1024 维向量。
    支持批量处理，自动处理 API 限流和重试。
    """

    def __init__(
        self,
        api_key: Optional[str] = None,
        model: str = "text-embedding-v4",
        batch_size: int = 50,
        max_retries: int = 3,
        retry_delay: float = 1.0,
    ) -> None:
        """
        初始化千问 Embedding 服务

        :param api_key: 千问 API 密钥，若为 None 则从配置读取
        :param model: 模型名称，默认 text-embedding-v4
        :param batch_size: 批量处理大小，默认 50（避免单次请求过大）
        :param max_retries: 最大重试次数，默认 3
        :param retry_delay: 重试延迟（秒），默认 1.0，会指数退避
        """
        # 优先使用传入的 api_key，其次从配置读取
        if api_key is None:
            api_key = getattr(settings, "qwen_api_key", None)

        if not api_key:
            raise RuntimeError(
                "千问 API 密钥缺失，请在 app.config.Settings 中添加 qwen_api_key，"
                "或在初始化 QwenEmbedding 时显式传入 api_key"
            )

        dashscope.api_key = api_key
        self._model = model
        self._batch_size = batch_size
        self._max_retries = max_retries
        self._retry_delay = retry_delay

        # 从配置读取向量维度（用于验证）
        self._expected_dimension = getattr(
            settings, "qwen_embedding_dimension", 1024
        )

        logger.info(
            "QwenEmbedding 初始化完成: model=%s, batch_size=%d, dimension=%d",
            self._model,
            self._batch_size,
            self._expected_dimension,
        )

    def _call_api_with_retry(
        self, texts: list[str], retry_count: int = 0
    ) -> list[list[float]]:
        """
        调用千问 API，带重试机制

        :param texts: 待向量化的文本列表
        :param retry_count: 当前重试次数
        :return: 向量列表，每个向量是 1024 维的浮点数列表
        """
        try:
            response = TextEmbedding.call(
                model=self._model,
                input=texts,
            )

            if response.status_code != 200:
                error_msg = f"千问 API 返回非 200 状态码: {response.status_code}"
                if response.message:
                    error_msg += f", message: {response.message}"
                raise RuntimeError(error_msg)

            # 提取向量
            embeddings: list[list[float]] = []
            if response.output and "embeddings" in response.output:
                for item in response.output["embeddings"]:
                    if "embedding" in item:
                        embedding = item["embedding"]
                        # 验证向量维度
                        if len(embedding) != self._expected_dimension:
                            logger.warning(
                                "向量维度不匹配: 期望 %d，实际 %d",
                                self._expected_dimension,
                                len(embedding),
                            )
                        embeddings.append(embedding)
                    else:
                        logger.warning("API 返回的 embedding 项缺少 embedding 字段")
                        embeddings.append([])

            if len(embeddings) != len(texts):
                raise RuntimeError(
                    f"向量数量不匹配: 输入文本数 {len(texts)}，返回向量数 {len(embeddings)}"
                )

            return embeddings

        except Exception as exc:
            # 重试逻辑
            if retry_count < self._max_retries:
                delay = self._retry_delay * (2 ** retry_count)  # 指数退避
                logger.warning(
                    "千问 API 调用失败，%f 秒后重试 (第 %d/%d 次): %s",
                    delay,
                    retry_count + 1,
                    self._max_retries,
                    exc,
                )
                time.sleep(delay)
                return self._call_api_with_retry(texts, retry_count + 1)
            else:
                logger.exception("千问 API 调用失败，已达最大重试次数: %s", exc)
                raise RuntimeError(f"千问 API 调用失败: {exc}") from exc

    def embed_documents(
        self, texts: Sequence[str]
    ) -> list[list[float]]:
        """
        批量对文档进行向量化

        :param texts: 待向量化的文本列表
        :return: 向量列表，每个向量是 1024 维的浮点数列表
        """
        if not texts:
            logger.warning("输入的文本列表为空，返回空列表")
            return []

        texts_list = list(texts)
        total = len(texts_list)
        logger.info("开始批量向量化，文本总数：%d，批量大小：%d", total, self._batch_size)

        all_embeddings: list[list[float]] = []

        # 分批处理
        for i in range(0, total, self._batch_size):
            batch = texts_list[i : i + self._batch_size]
            batch_num = i // self._batch_size + 1
            total_batches = (total + self._batch_size - 1) // self._batch_size

            logger.info(
                "处理第 %d/%d 批，文本数：%d", batch_num, total_batches, len(batch)
            )

            try:
                batch_embeddings = self._call_api_with_retry(batch)
                all_embeddings.extend(batch_embeddings)
                logger.debug(
                    "第 %d 批处理完成，生成 %d 个向量", batch_num, len(batch_embeddings)
                )
            except Exception as exc:
                logger.exception("第 %d 批处理失败: %s", batch_num, exc)
                # 可以选择继续处理下一批，或者直接抛出异常
                raise

        logger.info(
            "批量向量化完成，输入文本数：%d，输出向量数：%d",
            total,
            len(all_embeddings),
        )
        return all_embeddings

    def embed_query(self, text: str) -> list[float]:
        """
        对单个查询文本进行向量化（用于检索场景）

        :param text: 查询文本
        :return: 1024 维向量
        """
        if not text:
            raise ValueError("查询文本不能为空")

        logger.debug("开始对查询文本进行向量化，文本长度：%d 字符", len(text))

        embeddings = self._call_api_with_retry([text])
        if not embeddings:
            raise RuntimeError("向量化失败，未返回向量")

        result = embeddings[0]
        logger.debug("查询文本向量化完成，向量维度：%d", len(result))
        return result


def main() -> None:
    """
    简单测试入口：

    运行方式（示例）：
        python -m app.services.vector.embedding

    测试逻辑：
    - 创建测试文本列表
    - 使用 QwenEmbedding 进行向量化
    - 打印向量维度等信息
    """
    import logging

    logging.basicConfig(
        level=logging.INFO,
        format="%(asctime)s [%(levelname)s] %(name)s - %(message)s",
    )

    # 测试文本
    test_texts = [
        "LangChain 是一个强大的 LLM 应用开发框架",
        "RecursiveCharacterTextSplitter 是常用的文本分割器",
        "千问 text-embedding-v4 是阿里云提供的向量化模型",
        "pgvector 是 PostgreSQL 的向量扩展，支持高效的向量检索",
        "RAG（检索增强生成）是一种结合检索和生成的 AI 应用模式",
    ]

    try:
        # 创建 embedding 服务
        embedding_service = QwenEmbedding(batch_size=3)  # 测试用小批量

        # 批量向量化
        print("\n==== 开始批量向量化测试 ====")
        embeddings = embedding_service.embed_documents(test_texts)

        # 打印结果
        print(f"\n==== 向量化结果 ====")
        print(f"输入文本数：{len(test_texts)}")
        print(f"输出向量数：{len(embeddings)}")
        print(f"向量维度：{len(embeddings[0]) if embeddings else 0}")

        # 打印前 3 个向量的前 10 维作为预览
        for idx, (text, vec) in enumerate(zip(test_texts[:3], embeddings[:3])):
            print(f"\n--- 文本 {idx + 1} ---")
            print(f"文本: {text[:50]}...")
            print(f"向量前 10 维: {vec[:10]}")

        # 测试单个查询向量化
        print("\n==== 开始查询向量化测试 ====")
        query_text = "什么是向量检索？"
        query_vec = embedding_service.embed_query(query_text)
        print(f"查询文本: {query_text}")
        print(f"向量维度: {len(query_vec)}")
        print(f"向量前 10 维: {query_vec[:10]}")

    except Exception as exc:
        logger.exception("测试失败: %s", exc)
        print(f"\n测试失败: {exc}")
        print("请确保已正确配置 qwen_api_key")


if __name__ == "__main__":
    main()
