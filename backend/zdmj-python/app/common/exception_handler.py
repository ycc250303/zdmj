"""
全局异常处理中间件
统一处理异常并返回标准格式响应
"""
import logging

from fastapi import Request, status
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse
from starlette.exceptions import HTTPException as StarletteHTTPException

from app.common.response import ApiResponse
from app.exceptions import BusinessException, ServiceException, ValidationException

logger = logging.getLogger(__name__)

# 与 Java ErrorCode 枚举对齐（仅本服务当前会用到的通用码）
VALIDATION_ERROR_CODE = 1001
ILLEGAL_ARGUMENT_CODE = 1013
SYSTEM_EXCEPTION_CODE = 1014


async def validation_exception_handler(request: Request, exc: RequestValidationError):
    """
    处理请求参数验证异常
    """
    errors = exc.errors()
    error_messages = [f"{error['loc']}: {error['msg']}" for error in errors]
    message = f"参数验证失败: {', '.join(error_messages)}"

    logger.warning(f"参数验证失败: {request.url.path} - {message}")

    response = ApiResponse.error(code=VALIDATION_ERROR_CODE, msg=message)
    return JSONResponse(
        status_code=status.HTTP_400_BAD_REQUEST,
        content=response.model_dump(),
    )


async def http_exception_handler(request: Request, exc: StarletteHTTPException):
    """
    处理HTTP异常
    """
    logger.warning(f"HTTP异常: {request.url.path} - {exc.status_code} - {exc.detail}")

    response = ApiResponse.error(
        code=exc.status_code,
        msg=str(exc.detail),
    )
    return JSONResponse(
        status_code=exc.status_code,
        content=response.model_dump(),
    )


async def business_exception_handler(request: Request, exc: BusinessException):
    """
    处理业务异常
    """
    logger.error(f"业务异常: {request.url.path} - {exc.code} - {exc.message}")

    response = ApiResponse.error(
        code=exc.code,
        msg=exc.message,
    )
    return JSONResponse(
        status_code=status.HTTP_200_OK,  # 业务异常通常返回200，通过code字段区分
        content=response.model_dump(),
    )


async def validation_exception_handler_custom(request: Request, exc: ValidationException):
    """
    处理自定义验证异常
    """
    logger.warning(f"参数验证异常: {request.url.path} - {exc.message}")

    response = ApiResponse.error(
        code=exc.code,
        msg=exc.message,
    )
    return JSONResponse(
        status_code=status.HTTP_400_BAD_REQUEST,
        content=response.model_dump(),
    )


async def value_error_handler(request: Request, exc: ValueError):
    """
    处理非法参数异常（对齐 Java IllegalArgumentException）。
    """
    logger.warning(f"非法参数: {request.url.path} - {exc}")
    response = ApiResponse.error(
        code=ILLEGAL_ARGUMENT_CODE,
        msg=f"非法参数: {exc}",
    )
    return JSONResponse(
        status_code=status.HTTP_400_BAD_REQUEST,
        content=response.model_dump(),
    )


async def service_exception_handler(request: Request, exc: ServiceException):
    """
    处理服务异常
    """
    logger.error(f"服务异常: {request.url.path} - {exc.code} - {exc.message}")

    response = ApiResponse.error(
        code=exc.code,
        msg=exc.message,
    )
    return JSONResponse(
        status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
        content=response.model_dump(),
    )


async def general_exception_handler(request: Request, exc: Exception):
    """
    处理所有其他未捕获的异常
    """
    logger.exception(f"未处理的异常: {request.url.path} - {type(exc).__name__} - {str(exc)}")

    response = ApiResponse.error(
        code=SYSTEM_EXCEPTION_CODE,
        msg="系统异常，请联系管理员",
    )
    return JSONResponse(
        status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
        content=response.model_dump(),
    )
