"""
通用响应模型
与Java端的Result格式保持一致（使用msg字段而非message）
"""
from typing import Generic, TypeVar, Optional
from pydantic import BaseModel

T = TypeVar('T')


class ApiResponse(BaseModel, Generic[T]):
    """
    统一API响应格式
    与Java端的Result格式保持一致（code, msg, data）
    
    示例:
        # 成功响应
        ApiResponse(code=200, msg="成功", data={"key": "value"})
        
        # 失败响应
        ApiResponse(code=500, msg="操作失败", data=None)
    """
    code: int
    msg: str
    data: Optional[T] = None

    def __init__(self, **data):
        super().__init__(**data)

    @classmethod
    def success(cls, data: T = None, msg: str = "成功") -> "ApiResponse[T]":
        """
        创建成功响应
        
        Args:
            data: 响应数据
            msg: 响应消息，默认为"成功"
            
        Returns:
            ApiResponse对象
        """
        return cls(code=200, msg=msg, data=data)

    @classmethod
    def error(cls, code: int = 500, msg: str = "操作失败", data: T = None) -> "ApiResponse[T]":
        """
        创建错误响应
        
        Args:
            code: 错误码，默认500
            msg: 错误消息
            data: 可选的错误详情数据
            
        Returns:
            ApiResponse对象
        """
        return cls(code=code, msg=msg, data=data)
