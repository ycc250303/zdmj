"""
AST感知代码分块服务

功能：
- 使用 tree-sitter 进行 AST 解析，在语义边界（函数、类、方法）处分割代码
- 支持多种编程语言（Python, Java, JavaScript, TypeScript, Go, C++等）
- 提取代码元数据（函数名、类名、参数、返回值、行号等）
- 支持回退机制：如果 AST 解析失败，使用语言特定分割器
- 保留完整的语义单元，不会在函数中间截断
"""

from __future__ import annotations

import logging
import re
from importlib import import_module
from pathlib import Path
from typing import Optional, Sequence

from langchain_core.documents import Document
from langchain_text_splitters import RecursiveCharacterTextSplitter

logger = logging.getLogger(__name__)

EXT_TO_LANG = {
    '.py': 'python',
    '.java': 'java',
    '.js': 'javascript',
    '.jsx': 'javascript',
    '.ts': 'typescript',
    '.tsx': 'typescript',
    '.go': 'go',
    '.cpp': 'cpp',
    '.cc': 'cpp',
    '.cxx': 'cpp',
    '.c': 'c',
    '.h': 'c',
    '.hpp': 'cpp',
}

QUERY_PATTERNS = {
    'python': """
        (function_definition) @function
        (class_definition) @class
    """,
    'java': """
        (method_declaration) @method
        (class_declaration) @class
        (interface_declaration) @interface
    """,
    'javascript': """
        (function_declaration) @function
        (method_definition) @method
        (class_declaration) @class
        (arrow_function) @arrow_function
    """,
    'typescript': """
        (function_declaration) @function
        (method_definition) @method
        (class_declaration) @class
        (interface_declaration) @interface
        (arrow_function) @arrow_function
    """,
    'go': """
        (function_declaration) @function
        (method_declaration) @method
        (type_declaration) @type
    """,
    'cpp': """
        (function_definition) @function
        (class_specifier) @class
        (namespace_definition) @namespace
    """,
    'c': """
        (function_definition) @function
    """,
}

LANGUAGE_SPLITTER_MAP = {
    'python': 'python',
    'java': 'java',
    'javascript': 'js',
    'js': 'js',
    'typescript': 'js',
    'ts': 'js',
    'go': 'go',
    'cpp': 'cpp',
    'c': 'cpp',
}

LANGUAGE_GRAMMAR_IMPORTS = {
    "python": ("tree_sitter_python", ("python",)),
    "java": ("tree_sitter_java", ("java",)),
    "javascript": ("tree_sitter_javascript", ("javascript", "js")),
    "typescript": ("tree_sitter_typescript", ("typescript", "ts")),
    "go": ("tree_sitter_go", ("go",)),
    "cpp": ("tree_sitter_cpp", ("cpp", "c")),
}

METADATA_PATTERNS = {
    'python': (
        ("function_name", r'def\s+(\w+)'),
        ("class_name", r'class\s+(\w+)'),
    ),
    'java': (
        ("method_name", r'(\w+)\s*\([^)]*\)\s*\{'),
        ("class_name", r'class\s+(\w+)'),
    ),
    'javascript': (
        ("function_name", r'function\s+(\w+)'),
        ("method_name", r'(\w+)\s*\([^)]*\)\s*\{'),
        ("class_name", r'class\s+(\w+)'),
    ),
    'typescript': (
        ("function_name", r'function\s+(\w+)'),
        ("method_name", r'(\w+)\s*\([^)]*\)\s*\{'),
        ("class_name", r'class\s+(\w+)'),
    ),
    'go': (
        ("function_name", r'func\s+(\w+)'),
    ),
    'cpp': (
        ("function_name", r'(\w+)\s*\([^)]*\)\s*\{'),
        ("class_name", r'class\s+(\w+)'),
    ),
    'c': (
        ("function_name", r'(\w+)\s*\([^)]*\)\s*\{'),
    ),
}

# 尝试导入 tree-sitter 相关模块
try:
    from tree_sitter import Language, Parser, Node
    TREE_SITTER_AVAILABLE = True
except ImportError:
    TREE_SITTER_AVAILABLE = False
    logger.warning("tree-sitter 未安装，AST 解析功能将不可用，将使用回退方案")


class ASTCodeChunker:
    """
    基于 AST 的代码分块器
    
    使用 tree-sitter 解析代码 AST，按函数、类、方法等语义单元分块。
    如果 AST 解析失败，会回退到语言特定的分割器。
    """
    
    def __init__(
        self,
        chunk_size: int = 1500,
        chunk_overlap: int = 200,
    ) -> None:
        """
        初始化 AST 代码分块器
        
        :param chunk_size: 每个分块的最大字符数（用于回退方案），默认 1500
        :param chunk_overlap: 相邻分块之间的重叠字符数（用于回退方案），默认 200
        """
        self.chunk_size = chunk_size
        self.chunk_overlap = chunk_overlap
        
        # 初始化 tree-sitter parser
        self.parser: Optional[Parser] = None
        self.language_grammars: dict[str, Language] = {}
        
        # 回退分割器（当 AST 解析失败时使用）
        self.fallback_splitter = RecursiveCharacterTextSplitter(
            chunk_size=chunk_size,
            chunk_overlap=chunk_overlap
        )
        
        # 加载语言语法
        if TREE_SITTER_AVAILABLE:
            self._load_grammars()
        else:
            logger.warning("tree-sitter 不可用，将仅使用回退方案")
    
    def _load_grammars(self) -> None:
        """加载各语言的 tree-sitter 语法"""
        if not TREE_SITTER_AVAILABLE:
            return
        
        try:
            for display_name, (module_name, aliases) in LANGUAGE_GRAMMAR_IMPORTS.items():
                self._register_language_grammar(display_name, module_name, aliases)
            
            if self.language_grammars:
                self.parser = Parser()
                logger.info(
                    "tree-sitter 语法加载完成，支持的语言: %s",
                    list(self.language_grammars.keys())
                )
            else:
                logger.warning("未加载任何 tree-sitter 语法包，将使用回退方案")
                
        except Exception as e:
            logger.warning(f"加载 tree-sitter 语法时出错: {e}，将使用回退方案")

    def _register_language_grammar(
        self,
        display_name: str,
        module_name: str,
        aliases: tuple[str, ...],
    ) -> None:
        """导入并注册单个语言 grammar（含别名）"""
        try:
            grammar_module = import_module(module_name)
            grammar = grammar_module.language()
            for alias in aliases:
                self.language_grammars[alias] = grammar
        except ImportError:
            logger.debug("%s 未安装", module_name.replace("_", "-"))
        except Exception as exc:
            logger.debug("加载 %s 语法失败: %s", display_name, exc)
    
    def _detect_language(self, file_path: str) -> Optional[str]:
        """
        根据文件路径检测编程语言
        
        :param file_path: 文件路径
        :return: 语言名称（如 'python', 'java'），如果无法检测则返回 None
        """
        if not file_path:
            return None
        
        # 获取文件扩展名
        path = Path(file_path)
        ext = path.suffix.lower()
        
        return EXT_TO_LANG.get(ext)
    
    def _get_query_pattern(self, language: str) -> str:
        """
        根据语言返回 tree-sitter 查询模式
        
        :param language: 编程语言名称
        :return: tree-sitter 查询模式字符串
        """
        return QUERY_PATTERNS.get(language.lower(), "")
    
    def _extract_metadata(
        self,
        node: Node,
        code: str,
        language: str,
        file_path: str
    ) -> dict:
        """
        从 AST 节点提取元数据
        
        :param node: tree-sitter AST 节点
        :param code: 源代码字符串
        :param language: 编程语言
        :param file_path: 文件路径
        :return: 元数据字典
        """
        metadata = {
            "language": language,
            "file_path": file_path,
            "start_line": node.start_point[0] + 1,  # tree-sitter 行号从 0 开始
            "end_line": node.end_point[0] + 1,
            "chunk_method": "ast"
        }
        
        # 提取节点对应的代码文本
        node_text = code[node.start_byte:node.end_byte]
        
        for field, pattern in METADATA_PATTERNS.get(language, ()):
            match = re.search(pattern, node_text)
            if match:
                metadata[field] = match.group(1)
        
        return metadata

    def _add_chunk_index_metadata(self, chunks: list[Document]) -> None:
        """统一添加 chunk_index / total_chunks 元数据"""
        total_chunks = len(chunks)
        for chunk_idx, chunk in enumerate(chunks):
            chunk.metadata["chunk_index"] = chunk_idx
            chunk.metadata["total_chunks"] = total_chunks

    def _merge_with_original_metadata(
        self,
        original: dict,
        chunk_metadata: dict,
    ) -> dict:
        """合并 metadata，保留 chunk 侧字段优先级"""
        base = original.copy()
        base.pop("path", None)
        base.pop("file_path", None)
        return {**base, **chunk_metadata}
    
    def chunk_code(
        self,
        code: str,
        language: Optional[str] = None,
        file_path: str = "",
        *,
        preserve_metadata: bool = True,
        add_chunk_index: bool = True,
    ) -> list[Document]:
        """
        使用 AST 解析代码并分块
        
        :param code: 源代码字符串
        :param language: 编程语言（如 'python', 'java'），如果为 None 则从 file_path 推断
        :param file_path: 文件路径（用于推断语言和 metadata）
        :param preserve_metadata: 是否保留原始 metadata（当前未使用，为兼容性保留）
        :param add_chunk_index: 是否在 metadata 中添加 chunk_index，默认 True
        :return: Document 列表，每个 Document 包含一个代码块
        """
        if not code:
            logger.warning("输入的代码为空，返回空列表")
            return []
        
        # 检测语言
        if language is None:
            language = self._detect_language(file_path)
        
        if language is None:
            logger.warning(
                "无法检测编程语言，file_path=%s，使用回退方案",
                file_path
            )
            return self._chunk_with_language_splitter(code, language, file_path, add_chunk_index)
        
        # 检查是否有对应的语法
        grammar = self.language_grammars.get(language.lower())
        
        if not grammar or not self.parser:
            logger.debug(
                "语言 %s 的 tree-sitter 语法不可用，使用回退方案",
                language
            )
            return self._chunk_with_language_splitter(code, language, file_path, add_chunk_index)
        
        try:
            # 设置语言并解析 AST
            self.parser.set_language(grammar)
            tree = self.parser.parse(bytes(code, 'utf8'))
            
            # 获取查询模式
            query_pattern = self._get_query_pattern(language)
            if not query_pattern:
                logger.warning(
                    "语言 %s 没有定义查询模式，使用回退方案",
                    language
                )
                return self._chunk_with_language_splitter(code, language, file_path, add_chunk_index)
            
            # 执行查询
            query = grammar.query(query_pattern)
            captures = query.captures(tree.root_node)
            
            chunks: list[Document] = []
            
            for node, _ in captures:
                # 提取代码块
                chunk_code = code[node.start_byte:node.end_byte]
                
                # 跳过空代码块
                if not chunk_code.strip():
                    continue
                
                # 提取元数据
                metadata = self._extract_metadata(node, code, language, file_path)
                
                chunks.append(Document(
                    page_content=chunk_code,
                    metadata=metadata
                ))
            
            # 如果没有匹配到任何节点，将整个文件作为一个块
            if not chunks:
                logger.debug(
                    "AST 解析未匹配到任何语义单元，将整个文件作为一个块: %s",
                    file_path
                )
                chunks.append(Document(
                    page_content=code,
                    metadata={
                        "language": language,
                        "file_path": file_path,
                        "chunk_method": "ast_fallback"
                    }
                ))
            
            # 添加 chunk_index
            if add_chunk_index:
                self._add_chunk_index_metadata(chunks)
            
            logger.debug(
                "AST 代码分块完成: language=%s, file_path=%s, chunks=%d",
                language,
                file_path,
                len(chunks)
            )
            
            return chunks
            
        except Exception as e:
            logger.warning(
                "AST 解析失败，使用回退方案: language=%s, file_path=%s, error=%s",
                language,
                file_path,
                e
            )
            return self._chunk_with_language_splitter(code, language, file_path, add_chunk_index)
    
    def _chunk_with_language_splitter(
        self,
        code: str,
        language: Optional[str],
        file_path: str,
        add_chunk_index: bool = True
    ) -> list[Document]:
        """
        回退方案：使用语言特定的分割器
        
        :param code: 源代码字符串
        :param language: 编程语言
        :param file_path: 文件路径
        :param add_chunk_index: 是否添加 chunk_index
        :return: Document 列表
        """
        lc_lang = LANGUAGE_SPLITTER_MAP.get(language.lower() if language else '')
        
        if lc_lang:
            try:
                splitter = RecursiveCharacterTextSplitter.from_language(
                    lc_lang,
                    chunk_size=self.chunk_size,
                    chunk_overlap=self.chunk_overlap
                )
            except Exception as e:
                logger.warning(
                    "创建语言特定分割器失败，使用默认分割器: language=%s, error=%s",
                    language,
                    e
                )
                splitter = self.fallback_splitter
        else:
            splitter = self.fallback_splitter
        
        texts = splitter.split_text(code)
        chunks = [
            Document(
                page_content=text,
                metadata={
                    "language": language or "unknown",
                    "file_path": file_path,
                    "chunk_method": "fallback"
                }
            )
            for text in texts
        ]
        
        # 添加 chunk_index
        if add_chunk_index:
            self._add_chunk_index_metadata(chunks)
        
        logger.debug(
            "回退方案分块完成: language=%s, file_path=%s, chunks=%d",
            language,
            file_path,
            len(chunks)
        )
        
        return chunks
    
    def chunk_documents(
        self,
        documents: Sequence[Document],
        *,
        preserve_metadata: bool = True,
        add_chunk_index: bool = True,
    ) -> list[Document]:
        """
        对 Document 列表进行 AST 代码分块处理
        
        对于代码文件，使用 AST 分块；对于非代码文件，使用回退方案。
        
        :param documents: 待分块的 Document 列表
        :param preserve_metadata: 是否保留原始 Document 的 metadata，默认 True
        :param add_chunk_index: 是否在 metadata 中添加 chunk_index，默认 True
        :return: 分块后的 Document 列表
        """
        if not documents:
            logger.warning("输入的 Document 列表为空，返回空列表")
            return []
        
        logger.info("开始 AST 代码分块处理，输入文档数：%d", len(documents))
        
        all_chunks: list[Document] = []
        
        for doc_idx, doc in enumerate(documents):
            try:
                code = doc.page_content
                file_path = doc.metadata.get("path", doc.metadata.get("file_path", ""))
                
                # 检测是否为代码文件
                language = self._detect_language(file_path)
                
                if language:
                    # 代码文件：使用 AST 分块
                    chunks = self.chunk_code(
                        code=code,
                        language=language,
                        file_path=file_path,
                        preserve_metadata=preserve_metadata,
                        add_chunk_index=add_chunk_index,
                    )
                    
                    # 合并原始 metadata（如果启用）
                    if preserve_metadata:
                        for chunk in chunks:
                            # 合并原始 metadata，但代码分块器生成的 metadata 优先级更高
                            chunk.metadata = self._merge_with_original_metadata(
                                doc.metadata,
                                chunk.metadata,
                            )
                else:
                    # 非代码文件：使用回退方案
                    logger.debug(
                        "文档 %d 不是代码文件，使用回退方案: %s",
                        doc_idx,
                        file_path
                    )
                    chunks = self._chunk_with_language_splitter(
                        code=code,
                        language=None,
                        file_path=file_path,
                        add_chunk_index=add_chunk_index
                    )
                    
                    # 合并原始 metadata
                    if preserve_metadata:
                        for chunk in chunks:
                            chunk.metadata = self._merge_with_original_metadata(
                                doc.metadata,
                                chunk.metadata,
                            )
                
                all_chunks.extend(chunks)
                
                logger.debug(
                    "文档 %d 分块完成，生成 %d 个分块",
                    doc_idx,
                    len(chunks)
                )
                
            except Exception as exc:
                logger.warning(
                    "文档 %d 分块失败，已跳过: %s",
                    doc_idx,
                    exc
                )
                continue
        
        logger.info(
            "AST 代码分块处理完成，输入文档数：%d，输出分块数：%d",
            len(documents),
            len(all_chunks),
        )
        
        return all_chunks

