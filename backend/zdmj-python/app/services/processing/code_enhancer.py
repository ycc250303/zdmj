"""
代码增强服务

功能：
- 为代码块添加结构化的自然语言描述
- 提取代码元数据（函数名、类名、参数、返回值、注释等）
- 生成增强后的代码块，提高与自然语言查询的匹配度
- 支持多种编程语言（Python, Java, JavaScript, TypeScript, Go, C++等）
"""

from __future__ import annotations

import logging
import re

from langchain_core.documents import Document

logger = logging.getLogger(__name__)

LANGUAGE_COMMENT_EXTRACTORS = {
    "python": "_extract_python_comment",
    "java": "_extract_java_comment",
    "javascript": "_extract_javascript_comment",
    "typescript": "_extract_javascript_comment",
    "js": "_extract_javascript_comment",
    "ts": "_extract_javascript_comment",
    "go": "_extract_go_comment",
    "cpp": "_extract_cpp_comment",
    "c": "_extract_cpp_comment",
}


class CodeEnhancer:
    """
    代码增强器：为代码块添加结构化描述
    
    从代码块的 metadata 和代码内容中提取关键信息，生成结构化的自然语言描述，
    并将其添加到代码块前面，以提高向量检索的准确性。
    """
    
    def __init__(self) -> None:
        """初始化代码增强器"""
        logger.debug("CodeEnhancer 初始化完成")
    
    def enhance_code_chunk(self, doc: Document) -> Document:
        """
        为代码块添加自然语言描述
        
        :param doc: 包含代码块的 Document
        :return: 增强后的 Document
        """
        if not doc or not doc.page_content:
            logger.warning("输入的 Document 为空或内容为空，返回原 Document")
            return doc
        
        code = doc.page_content
        metadata = doc.metadata.copy() if doc.metadata else {}
        language = metadata.get("language", "unknown")
        
        # 提取关键信息并生成描述
        description = self._generate_description(code, language, metadata)
        
        # 构建增强后的代码块
        enhanced_content = f"""[代码片段]
{description}

{code}"""
        
        # 在 metadata 中标记已增强
        metadata["enhanced"] = True
        
        return Document(
            page_content=enhanced_content,
            metadata=metadata
        )
    
    def enhance_code_chunks(
        self,
        documents: list[Document],
        *,
        skip_non_code: bool = True,
    ) -> list[Document]:
        """
        批量增强代码块
        
        :param documents: Document 列表
        :param skip_non_code: 是否跳过非代码文档，默认 True
        :return: 增强后的 Document 列表
        """
        if not documents:
            logger.warning("输入的 Document 列表为空，返回空列表")
            return []
        
        logger.info("开始代码增强处理，输入文档数：%d", len(documents))
        
        enhanced_docs: list[Document] = []
        
        for doc_idx, doc in enumerate(documents):
            try:
                metadata = doc.metadata or {}
                language = metadata.get("language")
                
                # 如果启用跳过非代码，且没有语言信息，则跳过
                if skip_non_code and not language:
                    logger.debug(
                        "文档 %d 没有语言信息，跳过增强: %s",
                        doc_idx,
                        metadata.get("file_path", "unknown")
                    )
                    enhanced_docs.append(doc)
                    continue
                
                # 增强代码块
                enhanced_doc = self.enhance_code_chunk(doc)
                enhanced_docs.append(enhanced_doc)
                
                logger.debug(
                    "文档 %d 增强完成: language=%s, file_path=%s",
                    doc_idx,
                    language,
                    metadata.get("file_path", "unknown")
                )
                
            except Exception as exc:
                logger.warning(
                    "文档 %d 增强失败，已跳过: %s",
                    doc_idx,
                    exc
                )
                # 增强失败时返回原文档
                enhanced_docs.append(doc)
                continue
        
        logger.info(
            "代码增强处理完成，输入文档数：%d，输出文档数：%d",
            len(documents),
            len(enhanced_docs)
        )
        
        return enhanced_docs
    
    def _generate_description(
        self,
        code: str,
        language: str,
        metadata: dict
    ) -> str:
        """
        生成代码块的描述
        
        :param code: 源代码字符串
        :param language: 编程语言
        :param metadata: 代码块的元数据
        :return: 结构化的描述字符串
        """
        parts: list[str] = []
        
        # 语言信息
        if language and language != "unknown":
            parts.append(f"[语言] {language}")
        
        # 文件路径信息
        file_path = metadata.get("file_path") or metadata.get("path")
        if file_path:
            parts.append(f"[文件] {file_path}")
        
        # 函数/方法信息
        function_name = metadata.get("function_name")
        method_name = metadata.get("method_name")
        if function_name:
            parts.append(f"[函数] {function_name}")
        elif method_name:
            parts.append(f"[方法] {method_name}")
        
        # 类信息
        class_name = metadata.get("class_name")
        if class_name:
            parts.append(f"[类] {class_name}")
        
        # 接口信息（Java, TypeScript）
        interface_name = metadata.get("interface_name")
        if interface_name:
            parts.append(f"[接口] {interface_name}")
        
        # 行号信息
        start_line = metadata.get("start_line")
        end_line = metadata.get("end_line")
        if start_line is not None and end_line is not None:
            if start_line == end_line:
                parts.append(f"[行号] {start_line}")
            else:
                parts.append(f"[行号] {start_line}-{end_line}")
        elif start_line is not None:
            parts.append(f"[行号] {start_line}")
        
        # 提取注释（docstring、单行注释等）
        comment = self._extract_comment(code, language)
        if comment:
            parts.append(f"[说明] {comment}")
        
        # 如果没有提取到任何信息，至少返回语言信息
        if not parts:
            parts.append(f"[代码片段]")
        
        return "\n".join(parts)
    
    def _extract_comment(self, code: str, language: str) -> str:
        """
        提取代码中的注释
        
        :param code: 源代码字符串
        :param language: 编程语言
        :return: 提取的注释文本（清理后的）
        """
        if not code or not language:
            return ""
        
        language_lower = language.lower()
        
        try:
            extractor_name = LANGUAGE_COMMENT_EXTRACTORS.get(language_lower)
            if extractor_name:
                extractor = getattr(self, extractor_name)
                return extractor(code)
            return self._extract_generic_comment(code)
        except Exception as e:
            logger.debug(f"提取注释时出错: {e}")
            return ""

    def _extract_multiline_comment(
        self,
        code: str,
        patterns: list[str],
        *,
        strip_star_prefix: bool = False,
    ) -> str:
        """按给定模式提取首个多行注释，并返回第一行文本"""
        for pattern in patterns:
            match = re.search(pattern, code, re.DOTALL)
            if not match:
                continue

            comment = match.group(1).strip()
            if not comment:
                continue

            lines: list[str] = []
            for line in comment.split('\n'):
                cleaned = line.strip()
                if strip_star_prefix:
                    cleaned = re.sub(r'^\s*\*\s*', '', cleaned).strip()
                if cleaned:
                    lines.append(cleaned)

            if lines:
                return lines[0][:200]

        return ""

    def _extract_singleline_comment(
        self,
        code: str,
        *,
        prefixes: tuple[str, ...],
        max_lines: int = 5,
        exclude_start: str | None = None,
    ) -> str:
        """提取前若干行中的首个单行注释"""
        for line in code.split('\n')[:max_lines]:
            stripped = line.strip()
            prefix = next((p for p in prefixes if stripped.startswith(p)), None)
            if not prefix:
                continue

            comment = stripped[len(prefix):].strip()
            if not comment:
                continue
            if exclude_start and comment.startswith(exclude_start):
                continue
            return comment[:200]

        return ""
    
    def _extract_python_comment(self, code: str) -> str:
        """提取 Python 代码的注释（docstring 和 # 注释）"""
        comment = self._extract_multiline_comment(
            code,
            [r'"""(.*?)"""', r"'''(.*?)'''"],
        )
        if comment:
            return comment
        return self._extract_singleline_comment(
            code,
            prefixes=('#',),
            exclude_start='!',
        )
    
    def _extract_java_comment(self, code: str) -> str:
        """提取 Java 代码的注释（Javadoc 和 // 注释）"""
        comment = self._extract_multiline_comment(
            code,
            [r'/\*\*(.*?)\*/'],
            strip_star_prefix=True,
        )
        if comment:
            return comment
        return self._extract_singleline_comment(code, prefixes=('//',))
    
    def _extract_javascript_comment(self, code: str) -> str:
        """提取 JavaScript/TypeScript 代码的注释（JSDoc 和 // 注释）"""
        comment = self._extract_multiline_comment(
            code,
            [r'/\*\*(.*?)\*/'],
            strip_star_prefix=True,
        )
        if comment:
            return comment
        return self._extract_singleline_comment(code, prefixes=('//',))
    
    def _extract_go_comment(self, code: str) -> str:
        """提取 Go 代码的注释（// 注释）"""
        return self._extract_singleline_comment(code, prefixes=('//',))
    
    def _extract_cpp_comment(self, code: str) -> str:
        """提取 C/C++ 代码的注释（/** ... */ 和 // 注释）"""
        comment = self._extract_multiline_comment(
            code,
            [r'/\*\*(.*?)\*/'],
            strip_star_prefix=True,
        )
        if comment:
            return comment
        return self._extract_singleline_comment(code, prefixes=('//',))
    
    def _extract_generic_comment(self, code: str) -> str:
        """通用注释提取（尝试提取常见的注释格式）"""
        comment = self._extract_multiline_comment(
            code,
            [
                r'/\*\*(.*?)\*/',
                r'/\*(.*?)\*/',
                r'"""(.*?)"""',
                r"'''(.*?)'''",
            ],
        )
        if comment:
            return comment
        return self._extract_singleline_comment(code, prefixes=('//', '#'))