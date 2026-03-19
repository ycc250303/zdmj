"""
配置管理模块
使用 pydantic-settings 管理环境变量配置
"""
from pydantic_settings import BaseSettings
from typing import Optional


class Settings(BaseSettings):
    """应用配置"""
    
    # 应用配置
    app_name: str = "zdmj-python"
    app_version: str = "1.0.0"
    debug: bool = False
    
    # PostgreSQL 配置
    postgres_host: str = "111.229.81.45"
    postgres_port: int = 5432
    postgres_user: str = "zdmj"
    postgres_password: str = "zdmj"
    postgres_db: str = "zdmj"
    
    # Redis 配置
    redis_host: str = "111.229.81.45"
    redis_port: int = 6379
    redis_password: str = "zdmj"
    redis_db: int = 0
    
    # API 配置
    api_prefix: str = "/api"
    
    # COS 配置
    cos_secret_id: Optional[str] = "AKIDvz2Fgtra4Jav6322XxIblh5JI7nHWXJr"
    cos_secret_key: Optional[str] = "ndVMC1qW3OboskMsNiTDozCJE7tZ3ITR"
    cos_region: str = "ap-shanghai"
    cos_bucket: Optional[str] = "zdmj-1381832847"
    
    # 千问 Embedding 配置
    qwen_api_key: Optional[str] = "sk-d1cd60a55bd24eae942ba77605c9114c"
    qwen_embedding_model: str = "text-embedding-v4"
    qwen_embedding_dimension: int = 1024  # text-embedding-v4 的向量维度
    
    # GitHub 向量化配置
    github_max_files: int = 1000  # GitHub仓库最大处理文件数（优化后支持大仓库，稳定处理）
    github_max_file_size: int = 500 * 1024  # 单个文件最大大小（500KB）
    embedding_batch_size: int = 30  # 向量化批次大小（降低批次大小，提高稳定性）
    github_clone_timeout: int = 600  # GitHub仓库克隆超时时间（10分钟，支持大仓库）
    github_file_read_timeout: float = 5.0  # 单个文件读取超时时间（秒）
    github_progress_update_interval: int = 50  # 每处理多少个文件更新一次进度
    
    class Config:
        env_file = ".env"
        env_file_encoding = "utf-8"
        case_sensitive = False


# 全局配置实例
settings = Settings()
