"""
健康检查接口
检查服务状态和数据库连接
"""
from fastapi import APIRouter, status
from fastapi.responses import JSONResponse
from app.database import db

router = APIRouter()


@router.get("/health", summary="健康检查", description="检查服务状态和数据库连接")
async def health_check():
    """
    健康检查接口
    
    返回:
    - status: 服务状态
    - postgres: PostgreSQL 连接状态
    - redis: Redis 连接状态
    """
    # 检查数据库连接状态
    postgres_healthy = await db.check_postgres_health()
    redis_healthy = await db.check_redis_health()
    
    # 判断整体健康状态
    overall_healthy = postgres_healthy and redis_healthy
    
    response_data = {
        "status": "healthy" if overall_healthy else "unhealthy",
        "postgres": {
            "status": "connected" if postgres_healthy else "disconnected"
        },
        "redis": {
            "status": "connected" if redis_healthy else "disconnected"
        }
    }
    
    # 如果服务不健康，返回 503 状态码
    status_code = status.HTTP_200_OK if overall_healthy else status.HTTP_503_SERVICE_UNAVAILABLE
    
    return JSONResponse(
        content=response_data,
        status_code=status_code
    )
