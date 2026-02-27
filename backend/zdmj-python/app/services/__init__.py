"""
服务模块
包含各种业务服务实现

已按功能分类到不同子目录：
- fetcher/: 内容获取服务（GitHub, COS）
- vector/: 向量相关服务（embedding, vector_store）
- processing/: 文档处理服务（chunking）

为了保持向后兼容，所有服务类都可以从 app.services 直接导入。
"""

# 向后兼容：从子模块导入所有服务类
from app.services.fetcher import GitHubFetcher, COSFetcher
from app.services.vector import QwenEmbedding, VectorStore, VectorTableType
from app.services.processing import DocumentChunker

__all__ = [
    # Fetcher services
    "GitHubFetcher",
    "COSFetcher",
    # Vector services
    "QwenEmbedding",
    "VectorStore",
    "VectorTableType",
    # Processing services
    "DocumentChunker",
]
