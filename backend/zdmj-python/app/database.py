"""
数据库连接模块
管理 PostgreSQL 和 Redis 连接
"""
import asyncpg
import redis.asyncio as aioredis
from typing import Optional
from app.config import settings


class Database:
    """数据库连接管理类"""
    
    def __init__(self):
        self.postgres_pool: Optional[asyncpg.Pool] = None
        self.redis_client: Optional[aioredis.Redis] = None
    
    async def connect_postgres(self):
        """创建 PostgreSQL 连接池"""
        try:
            self.postgres_pool = await asyncpg.create_pool(
                host=settings.postgres_host,
                port=settings.postgres_port,
                user=settings.postgres_user,
                password=settings.postgres_password,
                database=settings.postgres_db,
                min_size=2,
                max_size=10
            )
            print("PostgreSQL 连接池创建成功")
        except Exception as e:
            print(f"PostgreSQL 连接失败: {e}")
            raise
    
    async def disconnect_postgres(self):
        """关闭 PostgreSQL 连接池"""
        if self.postgres_pool:
            await self.postgres_pool.close()
            self.postgres_pool = None
            print("PostgreSQL 连接池已关闭")
    
    async def connect_redis(self):
        """创建 Redis 连接"""
        try:
            self.redis_client = aioredis.from_url(
                f"redis://:{settings.redis_password}@{settings.redis_host}:{settings.redis_port}/{settings.redis_db}",
                encoding="utf-8",
                decode_responses=True
            )
            # 测试连接
            await self.redis_client.ping()
            print("Redis 连接成功")
        except Exception as e:
            print(f"Redis 连接失败: {e}")
            raise
    
    async def disconnect_redis(self):
        """关闭 Redis 连接"""
        if self.redis_client:
            await self.redis_client.close()
            self.redis_client = None
            print("Redis 连接已关闭")
    
    async def check_postgres_health(self) -> bool:
        """检查 PostgreSQL 连接健康状态"""
        if not self.postgres_pool:
            return False
        try:
            async with self.postgres_pool.acquire() as conn:
                await conn.fetchval("SELECT 1")
            return True
        except Exception:
            return False
    
    async def check_redis_health(self) -> bool:
        """检查 Redis 连接健康状态"""
        if not self.redis_client:
            return False
        try:
            await self.redis_client.ping()
            return True
        except Exception:
            return False


# 全局数据库实例
db = Database()
