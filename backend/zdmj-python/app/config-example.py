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
    
    # PostgreSQL 配置（示例值，实际请使用环境变量或 .env 配置）
    # 示例环境变量：POSTGRES_HOST / POSTGRES_PORT / POSTGRES_USER / POSTGRES_PASSWORD / POSTGRES_DB
    postgres_host: str = "your-postgres-host"
    postgres_port: int = 5432
    postgres_user: str = "your-postgres-user"
    postgres_password: str = "your-postgres-password"
    postgres_db: str = "your-postgres-db"
    
    # Redis 配置（示例值，实际请使用环境变量或 .env 配置）
    # 示例环境变量：REDIS_HOST / REDIS_PORT / REDIS_PASSWORD / REDIS_DB
    redis_host: str = "your-redis-host"
    redis_port: int = 6379
    redis_password: str = "your-redis-password"
    redis_db: int = 0
    
    # API 配置
    api_prefix: str = "/api"
    
    
    class Config:
        env_file = ".env"
        env_file_encoding = "utf-8"
        case_sensitive = False


# 全局配置实例
settings = Settings()
