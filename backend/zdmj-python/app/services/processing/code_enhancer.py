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
from typing import Optional

from langchain_core.documents import Document

logger = logging.getLogger(__name__)


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
            if language_lower == 'python':
                return self._extract_python_comment(code)
            elif language_lower == 'java':
                return self._extract_java_comment(code)
            elif language_lower in ('javascript', 'typescript', 'js', 'ts'):
                return self._extract_javascript_comment(code)
            elif language_lower == 'go':
                return self._extract_go_comment(code)
            elif language_lower in ('cpp', 'c'):
                return self._extract_cpp_comment(code)
            else:
                # 通用注释提取
                return self._extract_generic_comment(code)
        except Exception as e:
            logger.debug(f"提取注释时出错: {e}")
            return ""
    
    def _extract_python_comment(self, code: str) -> str:
        """提取 Python 代码的注释（docstring 和 # 注释）"""
        # 优先提取 docstring（三引号字符串）
        docstring_patterns = [
            r'"""(.*?)"""',  # 双引号 docstring
            r"'''(.*?)'''",  # 单引号 docstring
        ]
        
        for pattern in docstring_patterns:
            match = re.search(pattern, code, re.DOTALL)
            if match:
                docstring = match.group(1).strip()
                if docstring:
                    # 清理 docstring，只取第一行或前几行
                    lines = [line.strip() for line in docstring.split('\n') if line.strip()]
                    if lines:
                        return lines[0][:200]  # 限制长度
        
        # 如果没有 docstring，提取第一个 # 注释
        lines = code.split('\n')
        for line in lines[:5]:  # 只检查前5行
            stripped = line.strip()
            if stripped.startswith('#'):
                comment = stripped[1:].strip()
                if comment and not comment.startswith('!'):  # 排除 shebang
                    return comment[:200]
        
        return ""
    
    def _extract_java_comment(self, code: str) -> str:
        """提取 Java 代码的注释（Javadoc 和 // 注释）"""
        # 优先提取 Javadoc（/** ... */）
        javadoc_pattern = r'/\*\*(.*?)\*/'
        match = re.search(javadoc_pattern, code, re.DOTALL)
        if match:
            javadoc = match.group(1).strip()
            if javadoc:
                # 清理 Javadoc，移除 * 前缀
                lines = []
                for line in javadoc.split('\n'):
                    cleaned = re.sub(r'^\s*\*\s*', '', line).strip()
                    if cleaned:
                        lines.append(cleaned)
                if lines:
                    return lines[0][:200]
        
        # 如果没有 Javadoc，提取第一个 // 注释
        lines = code.split('\n')
        for line in lines[:5]:
            stripped = line.strip()
            if stripped.startswith('//'):
                comment = stripped[2:].strip()
                if comment:
                    return comment[:200]
        
        return ""
    
    def _extract_javascript_comment(self, code: str) -> str:
        """提取 JavaScript/TypeScript 代码的注释（JSDoc 和 // 注释）"""
        # 优先提取 JSDoc（/** ... */）
        jsdoc_pattern = r'/\*\*(.*?)\*/'
        match = re.search(jsdoc_pattern, code, re.DOTALL)
        if match:
            jsdoc = match.group(1).strip()
            if jsdoc:
                # 清理 JSDoc，移除 * 前缀
                lines = []
                for line in jsdoc.split('\n'):
                    cleaned = re.sub(r'^\s*\*\s*', '', line).strip()
                    if cleaned:
                        lines.append(cleaned)
                if lines:
                    return lines[0][:200]
        
        # 如果没有 JSDoc，提取第一个 // 注释
        lines = code.split('\n')
        for line in lines[:5]:
            stripped = line.strip()
            if stripped.startswith('//'):
                comment = stripped[2:].strip()
                if comment:
                    return comment[:200]
        
        return ""
    
    def _extract_go_comment(self, code: str) -> str:
        """提取 Go 代码的注释（// 注释）"""
        lines = code.split('\n')
        for line in lines[:5]:
            stripped = line.strip()
            if stripped.startswith('//'):
                comment = stripped[2:].strip()
                if comment:
                    return comment[:200]
        
        return ""
    
    def _extract_cpp_comment(self, code: str) -> str:
        """提取 C/C++ 代码的注释（/** ... */ 和 // 注释）"""
        # 优先提取多行注释（/** ... */ 或 /* ... */）
        multiline_pattern = r'/\*\*(.*?)\*/'
        match = re.search(multiline_pattern, code, re.DOTALL)
        if match:
            comment = match.group(1).strip()
            if comment:
                # 清理注释，移除 * 前缀
                lines = []
                for line in comment.split('\n'):
                    cleaned = re.sub(r'^\s*\*\s*', '', line).strip()
                    if cleaned:
                        lines.append(cleaned)
                if lines:
                    return lines[0][:200]
        
        # 如果没有多行注释，提取第一个 // 注释
        lines = code.split('\n')
        for line in lines[:5]:
            stripped = line.strip()
            if stripped.startswith('//'):
                comment = stripped[2:].strip()
                if comment:
                    return comment[:200]
        
        return ""
    
    def _extract_generic_comment(self, code: str) -> str:
        """通用注释提取（尝试提取常见的注释格式）"""
        # 尝试提取多行注释
        multiline_patterns = [
            r'/\*\*(.*?)\*/',  # /** ... */
            r'/\*(.*?)\*/',    # /* ... */
            r'"""(.*?)"""',    # """ ... """
            r"'''(.*?)'''",    # ''' ... '''
        ]
        
        for pattern in multiline_patterns:
            match = re.search(pattern, code, re.DOTALL)
            if match:
                comment = match.group(1).strip()
                if comment:
                    lines = [line.strip() for line in comment.split('\n') if line.strip()]
                    if lines:
                        return lines[0][:200]
        
        # 尝试提取单行注释
        lines = code.split('\n')
        for line in lines[:5]:
            stripped = line.strip()
            # 匹配常见的单行注释格式
            if stripped.startswith('//') or stripped.startswith('#'):
                comment = re.sub(r'^(//|#)\s*', '', stripped).strip()
                if comment:
                    return comment[:200]
        
        return ""


def main() -> None:
    """
    简单测试入口：
    
    运行方式（示例）：
        python -m app.services.processing.code_enhancer
    
    测试逻辑：
    - 创建测试代码文档
    - 使用 CodeEnhancer 进行增强
    - 打印增强结果预览
    """
    import logging
    
    logging.basicConfig(
        level=logging.INFO,
        format="%(asctime)s [%(levelname)s] %(name)s - %(message)s",
    )
    
    from langchain_core.documents import Document
    
    # 创建测试代码文档
    test_docs = [
        Document(
            page_content="""def calculate_sum(a: int, b: int) -> int:
    \"\"\"计算两个整数的和\"\"\"
    return a + b""",
            metadata={
                "language": "python",
                "file_path": "calculator.py",
                "function_name": "calculate_sum",
                "start_line": 1,
                "end_line": 3,
            },
        ),
        Document(
            page_content="""class Calculator:
    \"\"\"简单的计算器类\"\"\"
    
    def __init__(self):
        self.result = 0
    
    def add(self, x: int, y: int) -> int:
        \"\"\"加法运算\"\"\"
        self.result = x + y
        return self.result""",
            metadata={
                "language": "python",
                "file_path": "calculator.py",
                "class_name": "Calculator",
                "start_line": 1,
                "end_line": 9,
            },
        ),
        Document(
            page_content="""public class HelloWorld {
    /**
     * 主方法，程序入口
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        System.out.println("Hello, World!");
    }
}""",
            metadata={
                "language": "java",
                "file_path": "HelloWorld.java",
                "class_name": "HelloWorld",
                "start_line": 1,
                "end_line": 8,
            },
        ),
    ]
    
    # 创建代码增强器
    enhancer = CodeEnhancer()
    
    # 对文档进行增强
    enhanced_docs = enhancer.enhance_code_chunks(test_docs)
    
    # 打印结果
    print(f"\n==== 代码增强结果 ====")
    print(f"输入文档数：{len(test_docs)}")
    print(f"输出文档数：{len(enhanced_docs)}\n")
    
    for idx, doc in enumerate(enhanced_docs):
        print(f"--- 文档 {idx + 1} ---")
        print(f"Metadata: {doc.metadata}")
        print(f"内容长度: {len(doc.page_content)} 字符")
        print(f"增强后的内容:\n{doc.page_content}")
        print()


if __name__ == "__main__":
    main()
