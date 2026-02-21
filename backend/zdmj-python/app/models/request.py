"""
通用请求模型
所有请求DTO的基类
"""
from pydantic import BaseModel


class ApiRequest(BaseModel):
    """
    统一API请求基类
    所有请求DTO都应继承此类
    
    可以根据需要添加通用字段，例如：
    - request_id: 请求ID
    - user_id: 用户ID
    - timestamp: 时间戳
    """
    pass
