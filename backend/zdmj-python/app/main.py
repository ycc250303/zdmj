"""
FastAPI 应用入口
包含 /api 前缀路由和生命周期管理
"""
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from fastapi.exceptions import RequestValidationError
from starlette.exceptions import HTTPException as StarletteHTTPException
from contextlib import asynccontextmanager
from app.config import settings
from app.database import db
from app.api import health, knowledge
from app.exceptions import BusinessException, ValidationException, ServiceException
from app.middleware import exception_handler


@asynccontextmanager
async def lifespan(app: FastAPI):
    """应用生命周期管理"""
    # 启动时连接数据库
    try:
        await db.connect_postgres()
        await db.connect_redis()
    except Exception as e:
        print(f"数据库连接初始化失败: {e}")
    
    yield
    
    # 关闭时断开数据库连接
    await db.disconnect_postgres()
    await db.disconnect_redis()


# 创建 FastAPI 应用实例
app = FastAPI(
    title=settings.app_name,
    version=settings.app_version,
    debug=settings.debug,
    lifespan=lifespan
)

# 配置 CORS
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],  # 生产环境应配置具体域名
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# 注册全局异常处理器
app.add_exception_handler(RequestValidationError, exception_handler.validation_exception_handler)
app.add_exception_handler(StarletteHTTPException, exception_handler.http_exception_handler)
app.add_exception_handler(BusinessException, exception_handler.business_exception_handler)
app.add_exception_handler(ValidationException, exception_handler.validation_exception_handler_custom)
app.add_exception_handler(ServiceException, exception_handler.service_exception_handler)
app.add_exception_handler(Exception, exception_handler.general_exception_handler)

# 注册 API 路由（使用 /api 前缀）
app.include_router(health.router, prefix=settings.api_prefix, tags=["健康检查"])
app.include_router(knowledge.router, prefix=settings.api_prefix, tags=["知识库向量化"])


@app.get("/")
async def root():
    """根路径"""
    return {
        "message": "zdmj-python API",
        "version": settings.app_version,
        "docs": "/docs"
    }
