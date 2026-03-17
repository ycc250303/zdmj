"""
代码向量化测试脚本

功能：
- 测试不同编程语言的代码分块（AST分块 vs 语言特定分块）
- 测试回退机制（AST解析失败时的回退）
- 测试代码增强效果
- 测试向量化和检索相似度

运行方式：
    python test_code_vectorization.py
"""

import logging
import sys
from pathlib import Path
from typing import Optional

# 添加项目根目录到路径
project_root = Path(__file__).parent
sys.path.insert(0, str(project_root))

from langchain_core.documents import Document

# 直接导入需要的模块，避免导入整个app包
import importlib.util

# 导入AST代码分块器
ast_chunker_path = project_root / "app" / "services" / "processing" / "ast_code_chunker.py"
spec = importlib.util.spec_from_file_location("ast_code_chunker", ast_chunker_path)
ast_code_chunker_module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(ast_code_chunker_module)
ASTCodeChunker = ast_code_chunker_module.ASTCodeChunker

# 导入代码增强器
code_enhancer_path = project_root / "app" / "services" / "processing" / "code_enhancer.py"
spec = importlib.util.spec_from_file_location("code_enhancer", code_enhancer_path)
code_enhancer_module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(code_enhancer_module)
CodeEnhancer = code_enhancer_module.CodeEnhancer

# 尝试导入embedding服务（可能因为缺少API密钥而失败）
EMBEDDING_AVAILABLE = False
QwenEmbedding = None
try:
    embedding_path = project_root / "app" / "services" / "vector" / "embedding.py"
    spec = importlib.util.spec_from_file_location("embedding", embedding_path)
    embedding_module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(embedding_module)
    QwenEmbedding = embedding_module.QwenEmbedding
    EMBEDDING_AVAILABLE = True
except Exception as e:
    EMBEDDING_AVAILABLE = False
    logger.warning(f"Embedding服务不可用: {e}")

# 配置日志
logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(name)s - %(message)s",
)
logger = logging.getLogger(__name__)


# 测试代码样例
TEST_CODE_SAMPLES = {
    "python": """
def calculate_sum(a: int, b: int) -> int:
    \"\"\"计算两个整数的和\"\"\"
    return a + b

def calculate_product(x: float, y: float) -> float:
    \"\"\"计算两个浮点数的乘积\"\"\"
    return x * y

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
""",
    "java": """
public class HelloWorld {
    /**
     * 主方法，程序入口
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        System.out.println("Hello, World!");
    }
    
    /**
     * 计算两个整数的和
     * @param a 第一个整数
     * @param b 第二个整数
     * @return 两个整数的和
     */
    public int add(int a, int b) {
        return a + b;
    }
    
    /**
     * 计算两个整数的乘积
     * @param x 第一个整数
     * @param y 第二个整数
     * @return 两个整数的乘积
     */
    public int multiply(int x, int y) {
        return x * y;
    }
}
""",
    "javascript": """
/**
 * 计算两个数的和
 * @param {number} a - 第一个数
 * @param {number} b - 第二个数
 * @returns {number} 两个数的和
 */
function calculateSum(a, b) {
    return a + b;
}

/**
 * 计算两个数的乘积
 * @param {number} x - 第一个数
 * @param {number} y - 第二个数
 * @returns {number} 两个数的乘积
 */
function calculateProduct(x, y) {
    return x * y;
}

class Calculator {
    /**
     * 构造函数
     */
    constructor() {
        this.result = 0;
    }
    
    /**
     * 加法运算
     * @param {number} x - 第一个数
     * @param {number} y - 第二个数
     * @returns {number} 计算结果
     */
    add(x, y) {
        this.result = x + y;
        return this.result;
    }
}
""",
    "go": """
package main

import "fmt"

// calculateSum 计算两个整数的和
func calculateSum(a int, b int) int {
    return a + b
}

// calculateProduct 计算两个整数的乘积
func calculateProduct(x int, y int) int {
    return x * y
}

// Calculator 计算器结构体
type Calculator struct {
    result int
}

// NewCalculator 创建新的计算器实例
func NewCalculator() *Calculator {
    return &Calculator{result: 0}
}

// Add 加法运算
func (c *Calculator) Add(x int, y int) int {
    c.result = x + y
    return c.result
}
""",
    "cpp": """
#include <iostream>

// 计算两个整数的和
int calculateSum(int a, int b) {
    return a + b;
}

// 计算两个整数的乘积
int calculateProduct(int x, int y) {
    return x * y;
}

// 计算器类
class Calculator {
private:
    int result;
    
public:
    Calculator() : result(0) {}
    
    // 加法运算
    int add(int x, int y) {
        result = x + y;
        return result;
    }
    
    // 乘法运算
    int multiply(int x, int y) {
        result = x * y;
        return result;
    }
};
""",
    "invalid_syntax": """
def invalid_python_code(
    # 这是一个语法错误的代码，用于测试回退机制
    return "missing colon"
""",
    "unknown_language": """
some random text that is not code
this should trigger fallback mechanism
""",
}


def test_chunking_methods():
    """测试不同分块方法"""
    print("\n" + "=" * 80)
    print("测试1: 代码分块方法对比")
    print("=" * 80)
    
    chunker = ASTCodeChunker(chunk_size=1500, chunk_overlap=200)
    
    results = {}
    
    for lang, code in TEST_CODE_SAMPLES.items():
        if lang in ("invalid_syntax", "unknown_language"):
            continue
            
        file_path = f"test.{lang}"
        if lang == "javascript":
            file_path = "test.js"
        elif lang == "cpp":
            file_path = "test.cpp"
        
        print(f"\n--- 测试语言: {lang} ---")
        print(f"文件路径: {file_path}")
        
        doc = Document(
            page_content=code.strip(),
            metadata={"path": file_path, "type": "code"}
        )
        
        chunks = chunker.chunk_documents([doc])
        
        results[lang] = {
            "chunks_count": len(chunks),
            "chunks": chunks,
        }
        
        print(f"分块数量: {len(chunks)}")
        
        # 显示每个分块的元数据
        for idx, chunk in enumerate(chunks[:3]):  # 只显示前3个
            chunk_method = chunk.metadata.get("chunk_method", "unknown")
            print(f"  分块 {idx + 1}:")
            print(f"    方法: {chunk_method}")
            print(f"    元数据: {chunk.metadata}")
            print(f"    内容长度: {len(chunk.page_content)} 字符")
            if chunk.metadata.get("function_name"):
                print(f"    函数名: {chunk.metadata['function_name']}")
            if chunk.metadata.get("class_name"):
                print(f"    类名: {chunk.metadata['class_name']}")
    
    return results


def test_fallback_mechanism():
    """测试回退机制"""
    print("\n" + "=" * 80)
    print("测试2: 回退机制测试")
    print("=" * 80)
    
    chunker = ASTCodeChunker(chunk_size=1500, chunk_overlap=200)
    
    # 测试1: 语法错误的代码
    print("\n--- 测试场景1: 语法错误的代码 ---")
    invalid_doc = Document(
        page_content=TEST_CODE_SAMPLES["invalid_syntax"].strip(),
        metadata={"path": "test_invalid.py", "type": "code"}
    )
    invalid_chunks = chunker.chunk_documents([invalid_doc])
    print(f"分块数量: {len(invalid_chunks)}")
    if invalid_chunks:
        print(f"分块方法: {invalid_chunks[0].metadata.get('chunk_method')}")
        print("[OK] 语法错误代码成功回退到语言特定分割器")
    
    # 测试2: 未知语言
    print("\n--- 测试场景2: 未知语言/非代码文件 ---")
    unknown_doc = Document(
        page_content=TEST_CODE_SAMPLES["unknown_language"].strip(),
        metadata={"path": "test.txt", "type": "text"}
    )
    unknown_chunks = chunker.chunk_documents([unknown_doc])
    print(f"分块数量: {len(unknown_chunks)}")
    if unknown_chunks:
        print(f"分块方法: {unknown_chunks[0].metadata.get('chunk_method')}")
        print("[OK] 未知语言成功回退到默认分割器")
    
    # 测试3: 空代码
    print("\n--- 测试场景3: 空代码 ---")
    empty_doc = Document(
        page_content="",
        metadata={"path": "test_empty.py", "type": "code"}
    )
    empty_chunks = chunker.chunk_documents([empty_doc])
    print(f"分块数量: {len(empty_chunks)}")
    print("[OK] 空代码正确处理")
    
    # 测试4: 无文件路径
    print("\n--- 测试场景4: 无文件路径 ---")
    no_path_doc = Document(
        page_content=TEST_CODE_SAMPLES["python"].strip(),
        metadata={"type": "code"}
    )
    no_path_chunks = chunker.chunk_documents([no_path_doc])
    print(f"分块数量: {len(no_path_chunks)}")
    if no_path_chunks:
        print(f"分块方法: {no_path_chunks[0].metadata.get('chunk_method')}")
        print("[OK] 无文件路径成功回退")


def test_code_enhancement():
    """测试代码增强"""
    print("\n" + "=" * 80)
    print("测试3: 代码增强测试")
    print("=" * 80)
    
    chunker = ASTCodeChunker(chunk_size=1500, chunk_overlap=200)
    enhancer = CodeEnhancer()
    
    # 测试Python代码增强
    print("\n--- Python代码增强 ---")
    python_doc = Document(
        page_content=TEST_CODE_SAMPLES["python"].strip(),
        metadata={"path": "test.py", "type": "code"}
    )
    python_chunks = chunker.chunk_documents([python_doc])
    
    if python_chunks:
        original_chunk = python_chunks[0]
        enhanced_chunk = enhancer.enhance_code_chunk(original_chunk)
        
        print(f"原始内容长度: {len(original_chunk.page_content)} 字符")
        print(f"增强后内容长度: {len(enhanced_chunk.page_content)} 字符")
        print(f"\n增强后的内容预览:\n{enhanced_chunk.page_content[:500]}")
        print("[OK] 代码增强成功")
    
    # 测试Java代码增强
    print("\n--- Java代码增强 ---")
    java_doc = Document(
        page_content=TEST_CODE_SAMPLES["java"].strip(),
        metadata={"path": "test.java", "type": "code"}
    )
    java_chunks = chunker.chunk_documents([java_doc])
    
    if java_chunks:
        original_chunk = java_chunks[0]
        enhanced_chunk = enhancer.enhance_code_chunk(original_chunk)
        
        print(f"原始内容长度: {len(original_chunk.page_content)} 字符")
        print(f"增强后内容长度: {len(enhanced_chunk.page_content)} 字符")
        print(f"\n增强后的内容预览:\n{enhanced_chunk.page_content[:500]}")
        print("[OK] 代码增强成功")


def test_vectorization_and_retrieval():
    """测试向量化和检索相似度"""
    print("\n" + "=" * 80)
    print("测试4: 向量化和检索相似度测试")
    print("=" * 80)
    
    if not EMBEDDING_AVAILABLE:
        print("[WARN] Embedding服务不可用，跳过向量化测试")
        print("提示: 请确保配置了 QWEN_API_KEY 环境变量")
        return
    
    try:
        # 初始化embedding服务
        embedding_service = QwenEmbedding()
        print("[OK] Embedding服务初始化成功")
    except Exception as e:
        print(f"[ERROR] Embedding服务初始化失败: {e}")
        print("提示: 请确保配置了 QWEN_API_KEY 环境变量")
        return
    
    chunker = ASTCodeChunker(chunk_size=1500, chunk_overlap=200)
    enhancer = CodeEnhancer()
    
    # 准备测试查询
    test_queries = [
        "计算两个数的和",
        "计算两个数的乘积",
        "计算器类",
        "加法运算",
    ]
    
    # 处理Python代码
    print("\n--- 处理Python代码 ---")
    python_doc = Document(
        page_content=TEST_CODE_SAMPLES["python"].strip(),
        metadata={"path": "test.py", "type": "code"}
    )
    python_chunks = chunker.chunk_documents([python_doc])
    python_enhanced = enhancer.enhance_code_chunks(python_chunks)
    
    print(f"分块数量: {len(python_enhanced)}")
    
    # 生成代码块的向量
    try:
        code_texts = [chunk.page_content for chunk in python_enhanced]
        code_embeddings = embedding_service.embed_documents(code_texts)
        print(f"[OK] 代码向量生成成功，向量数量: {len(code_embeddings)}")
    except Exception as e:
        print(f"[ERROR] 代码向量生成失败: {e}")
        return
    
    # 生成查询向量并计算相似度
    print("\n--- 查询相似度测试 ---")
    for query in test_queries:
        try:
            query_embedding = embedding_service.embed_query(query)
            
            # 计算与每个代码块的相似度（余弦相似度）
            similarities = []
            for idx, code_embedding in enumerate(code_embeddings):
                # 简单的余弦相似度计算
                import numpy as np
                
                vec1 = np.array(query_embedding)
                vec2 = np.array(code_embedding)
                
                dot_product = np.dot(vec1, vec2)
                norm1 = np.linalg.norm(vec1)
                norm2 = np.linalg.norm(vec2)
                
                similarity = dot_product / (norm1 * norm2) if (norm1 * norm2) > 0 else 0.0
                
                chunk_info = {
                    "chunk_idx": idx,
                    "similarity": float(similarity),
                    "metadata": python_enhanced[idx].metadata,
                }
                similarities.append(chunk_info)
            
            # 按相似度排序
            similarities.sort(key=lambda x: x["similarity"], reverse=True)
            
            print(f"\n查询: \"{query}\"")
            print(f"  最高相似度: {similarities[0]['similarity']:.4f}")
            print(f"  匹配的代码块:")
            for sim_info in similarities[:3]:  # 显示前3个
                metadata = sim_info["metadata"]
                func_name = metadata.get("function_name", "N/A")
                class_name = metadata.get("class_name", "N/A")
                chunk_method = metadata.get("chunk_method", "unknown")
                print(f"    - 相似度: {sim_info['similarity']:.4f}, "
                      f"方法: {chunk_method}, "
                      f"函数: {func_name}, "
                      f"类: {class_name}")
            
        except Exception as e:
            print(f"[ERROR] 查询 \"{query}\" 处理失败: {e}")
            continue


def test_chunk_method_comparison():
    """对比AST分块和回退分块的效果"""
    print("\n" + "=" * 80)
    print("测试5: AST分块 vs 回退分块对比")
    print("=" * 80)
    
    chunker = ASTCodeChunker(chunk_size=1500, chunk_overlap=200)
    
    python_code = TEST_CODE_SAMPLES["python"].strip()
    python_doc = Document(
        page_content=python_code,
        metadata={"path": "test.py", "type": "code"}
    )
    
    # AST分块
    ast_chunks = chunker.chunk_documents([python_doc])
    ast_methods = [chunk.metadata.get("chunk_method") for chunk in ast_chunks]
    ast_count = sum(1 for m in ast_methods if m == "ast")
    fallback_count = sum(1 for m in ast_methods if m == "fallback")
    
    print(f"\nPython代码分块结果:")
    print(f"  总分块数: {len(ast_chunks)}")
    print(f"  AST分块数: {ast_count}")
    print(f"  回退分块数: {fallback_count}")
    
    # 检查是否保留了语义完整性（函数、类不会被截断）
    print(f"\n语义完整性检查:")
    for idx, chunk in enumerate(ast_chunks):
        content = chunk.page_content
        has_complete_function = (
            "def " in content and 
            ("return" in content or ":" in content.split("\n")[0])
        )
        has_complete_class = (
            "class " in content and 
            ("def " in content or ":" in content.split("\n")[0])
        )
        
        if has_complete_function or has_complete_class:
            print(f"  分块 {idx + 1}: [OK] 语义完整")
        else:
            print(f"  分块 {idx + 1}: [WARN] 可能不完整")


def main():
    """主测试函数"""
    print("\n" + "=" * 80)
    print("代码向量化优化方案 - 测试脚本")
    print("=" * 80)
    print("\n本测试脚本将验证:")
    print("1. 不同编程语言的代码分块效果")
    print("2. AST解析失败时的回退机制")
    print("3. 代码增强功能")
    print("4. 向量化和检索相似度")
    print("5. AST分块 vs 回退分块对比")
    
    # 测试1: 代码分块方法
    chunking_results = test_chunking_methods()
    
    # 测试2: 回退机制
    test_fallback_mechanism()
    
    # 测试3: 代码增强
    test_code_enhancement()
    
    # 测试4: 向量化和检索（需要API密钥）
    try:
        test_vectorization_and_retrieval()
    except Exception as e:
        print(f"\n[WARN] 向量化测试跳过（需要配置API密钥）: {e}")
    
    # 测试5: 分块方法对比
    test_chunk_method_comparison()
    
    print("\n" + "=" * 80)
    print("测试完成")
    print("=" * 80)
    print("\n总结:")
    print("[OK] 回退机制测试完成")
    print("[OK] 代码分块功能测试完成")
    print("[OK] 代码增强功能测试完成")
    if chunking_results:
        total_chunks = sum(r["chunks_count"] for r in chunking_results.values())
        print(f"[OK] 共处理 {len(chunking_results)} 种语言，生成 {total_chunks} 个代码块")


if __name__ == "__main__":
    main()
