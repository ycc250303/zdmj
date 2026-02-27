"""
文档分块服务

功能：
- 使用 LangChain 的 RecursiveCharacterTextSplitter 对文档进行分块
- 支持文本、代码、Markdown 等多种文档类型
- 支持常用参数配置（chunk_size、chunk_overlap、separators 等）
- 保留原始 Document 的 metadata，并添加 chunk_index 信息
"""

from __future__ import annotations

import logging
from typing import Callable, Optional, Sequence

from langchain_core.documents import Document
from langchain_text_splitters import RecursiveCharacterTextSplitter

logger = logging.getLogger(__name__)


class DocumentChunker:
    """
    文档分块服务

    使用 RecursiveCharacterTextSplitter 对 Document 列表进行分块处理。
    支持自定义分块参数，并保留原始 metadata 信息。
    """

    def __init__(
        self,
        chunk_size: int = 1200,
        chunk_overlap: int = 300,
        separators: Optional[Sequence[str]] = None,
        length_function: Optional[Callable[[str], int]] = None,
        is_separator_regex: bool = False,
        keep_separator: bool = False,
        add_start_index: bool = False,
        strip_whitespace: bool = True,
    ) -> None:
        """
        初始化文档分块器

        :param chunk_size: 每个分块的最大字符数，默认 1200
        :param chunk_overlap: 相邻分块之间的重叠字符数，默认 300
        :param separators: 自定义分隔符列表，用于优先分割文本
                          若为 None，则使用 RecursiveCharacterTextSplitter 的默认分隔符
        :param length_function: 用于计算文本长度的函数，默认使用 len
        :param is_separator_regex: 分隔符是否为正则表达式，默认 False
        :param keep_separator: 是否在分块中保留分隔符，默认 False
        :param add_start_index: 是否在 metadata 中添加 start_index，默认 False
        :param strip_whitespace: 是否去除分块首尾空白，默认 True
        """
        self._splitter = RecursiveCharacterTextSplitter(
            chunk_size=chunk_size,
            chunk_overlap=chunk_overlap,
            separators=separators,
            length_function=len,
            is_separator_regex=is_separator_regex,
            keep_separator=keep_separator,
            add_start_index=add_start_index,
            strip_whitespace=strip_whitespace,
        )

        logger.info(
            "DocumentChunker 初始化完成: chunk_size=%d, chunk_overlap=%d",
            chunk_size,
            chunk_overlap,
        )

    def chunk_documents(
        self,
        documents: Sequence[Document],
        *,
        preserve_metadata: bool = True,
        add_chunk_index: bool = True,
    ) -> list[Document]:
        """
        对 Document 列表进行分块处理

        :param documents: 待分块的 Document 列表
        :param preserve_metadata: 是否保留原始 Document 的 metadata，默认 True
        :param add_chunk_index: 是否在 metadata 中添加 chunk_index（从 0 开始），默认 True
        :return: 分块后的 Document 列表
        """
        if not documents:
            logger.warning("输入的 Document 列表为空，返回空列表")
            return []

        logger.info("开始分块处理，输入文档数：%d", len(documents))

        all_chunks: list[Document] = []

        for doc_idx, doc in enumerate(documents):
            try:
                # 使用 splitter 对单个文档进行分块
                chunks = self._splitter.split_documents([doc])

                # 为每个分块添加额外的 metadata
                for chunk_idx, chunk in enumerate(chunks):
                    # 保留原始 metadata（如果启用）
                    if preserve_metadata:
                        new_metadata = chunk.metadata.copy()
                    else:
                        new_metadata = {}

                    # 添加 chunk_index（如果启用）
                    if add_chunk_index:
                        new_metadata["chunk_index"] = chunk_idx
                        new_metadata["total_chunks"] = len(chunks)
                        new_metadata["source_doc_index"] = doc_idx

                    all_chunks.append(
                        Document(
                            page_content=chunk.page_content,
                            metadata=new_metadata,
                        )
                    )

                logger.debug(
                    "文档 %d 分块完成，生成 %d 个分块", doc_idx, len(chunks)
                )
            except Exception as exc:
                logger.warning(
                    "文档 %d 分块失败，已跳过: %s", doc_idx, exc
                )
                continue

        logger.info(
            "分块处理完成，输入文档数：%d，输出分块数：%d",
            len(documents),
            len(all_chunks),
        )
        return all_chunks

    def chunk_text(
        self,
        text: str,
        *,
        metadata: Optional[dict] = None,
        add_chunk_index: bool = True,
    ) -> list[Document]:
        """
        对单个文本字符串进行分块处理

        :param text: 待分块的文本内容
        :param metadata: 可选的 metadata，会应用到所有分块
        :param add_chunk_index: 是否在 metadata 中添加 chunk_index，默认 True
        :return: 分块后的 Document 列表
        """
        if not text:
            logger.warning("输入的文本为空，返回空列表")
            return []

        logger.info("开始对文本进行分块，文本长度：%d 字符", len(text))

        # 将文本包装成 Document
        doc = Document(
            page_content=text,
            metadata=metadata or {},
        )

        # 使用 splitter 分块
        chunks = self._splitter.split_documents([doc])

        # 为每个分块添加 chunk_index（如果启用）
        if add_chunk_index:
            for chunk_idx, chunk in enumerate(chunks):
                chunk.metadata["chunk_index"] = chunk_idx
                chunk.metadata["total_chunks"] = len(chunks)

        logger.info("文本分块完成，生成 %d 个分块", len(chunks))
        return chunks


def main() -> None:
    """
    简单测试入口：

    运行方式（示例）：
        python -m app.services.processing.chunking

    测试逻辑：
    - 创建测试文档列表
    - 使用 DocumentChunker 进行分块
    - 打印分块结果预览
    """
    import logging

    logging.basicConfig(
        level=logging.INFO,
        format="%(asctime)s [%(levelname)s] %(name)s - %(message)s",
    )

    from langchain_core.documents import Document

    # 创建测试文档
    test_docs = [
        Document(
            page_content="""
这是一个测试文档，用于验证文档分块功能。

第一段内容：LangChain 是一个强大的 LLM 应用开发框架，提供了丰富的工具和组件。
它支持多种文档加载器、文本分割器、向量存储等核心功能。

第二段内容：RecursiveCharacterTextSplitter 是 LangChain 中常用的文本分割器。
它会按照指定的分隔符递归地分割文本，确保每个分块的大小在合理范围内。

第三段内容：分块时可以通过 chunk_size 和 chunk_overlap 参数控制分块的大小和重叠。
重叠部分有助于保持上下文的连贯性，提高向量检索的准确性。

第四段内容：在实际应用中，我们需要根据不同的文档类型选择合适的分块策略。
例如，代码文件可以按函数或类边界分割，Markdown 文件可以按标题层级分割。
            """.strip(),
            metadata={"source": "test_doc_1.txt", "type": "text"},
        ),
        Document(
            page_content="""
# Python 代码示例

def hello_world():
    \"\"\"打印 Hello World\"\"\"
    print("Hello, World!")

class Calculator:
    \"\"\"简单的计算器类\"\"\"
    
    def __init__(self):
        self.result = 0
    
    def add(self, x: int, y: int) -> int:
        \"\"\"加法运算\"\"\"
        self.result = x + y
        return self.result
    
    def multiply(self, x: int, y: int) -> int:
        \"\"\"乘法运算\"\"\"
        self.result = x * y
        return self.result

if __name__ == "__main__":
    calc = Calculator()
    print(calc.add(2, 3))
    print(calc.multiply(4, 5))
            """.strip(),
            metadata={"source": "test_code.py", "type": "code"},
        ),
    ]

    # 创建分块器（使用默认参数：chunk_size=1200, chunk_overlap=300）
    chunker = DocumentChunker(chunk_size=100, chunk_overlap=25)

    # 对文档进行分块
    chunks = chunker.chunk_documents(test_docs)

    # 打印结果
    print(f"\n==== 分块结果 ====")
    print(f"输入文档数：{len(test_docs)}")
    print(f"输出分块数：{len(chunks)}\n")

    for idx, chunk in enumerate(chunks[:5]):  # 只显示前 5 个分块
        print(f"--- 分块 {idx + 1} ---")
        print(f"Metadata: {chunk.metadata}")
        print(f"内容长度: {len(chunk.page_content)} 字符")
        print(f"内容预览: {chunk.page_content[:200]}")
        print()

    if len(chunks) > 5:
        print(f"...（共 {len(chunks)} 个分块，仅展示前 5 个）")


if __name__ == "__main__":
    main()
