"""
健康检查接口
检查服务状态和数据库连接
"""
from fastapi import APIRouter
from app.database import db
from app.models.response import ApiResponse

router = APIRouter()


@router.get("/health", summary="健康检查", description="检查服务状态和数据库连接")
async def health_check() -> ApiResponse[dict]:
    """
    健康检查接口
    
    返回:
    - code: 状态码（200表示健康，503表示不健康）
    - message: 消息
    - data: 包含服务状态详情
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
    
    # 使用统一响应格式
    if overall_healthy:
        return ApiResponse.success(
            data=response_data,
            message="服务健康"
        )
    else:
        return ApiResponse.error(
            code=503,
            message="服务不健康",
            data=response_data
        )
