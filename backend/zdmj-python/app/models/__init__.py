"""
数据模型模块
包含请求、响应等通用模型
"""
from app.models.request import ApiRequest
from app.models.response import ApiResponse

__all__ = ["ApiRequest", "ApiResponse"]
