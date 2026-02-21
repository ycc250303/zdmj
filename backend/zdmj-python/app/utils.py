"""
工具函数模块
提供通用的工具函数
"""
from typing import Any, Dict
from app.models.response import ApiResponse


def create_success_response(data: Any = None, message: str = "成功") -> ApiResponse:
    """
    创建成功响应的便捷函数
    
    Args:
        data: 响应数据
        message: 响应消息
        
    Returns:
        ApiResponse对象
    """
    return ApiResponse.success(data=data, message=message)


def create_error_response(code: int = 500, message: str = "操作失败", data: Any = None) -> ApiResponse:
    """
    创建错误响应的便捷函数
    
    Args:
        code: 错误码
        message: 错误消息
        data: 可选的错误详情数据
        
    Returns:
        ApiResponse对象
    """
    return ApiResponse.error(code=code, message=message, data=data)
