"""
自定义异常类
用于统一异常处理
"""


class BusinessException(Exception):
    """
    业务异常
    用于处理业务逻辑错误
    """
    def __init__(self, code: int = 500, message: str = "业务处理失败"):
        self.code = code
        self.message = message
        super().__init__(self.message)


class ValidationException(Exception):
    """
    参数验证异常
    用于处理请求参数验证错误
    """
    def __init__(self, message: str = "参数验证失败"):
        self.code = 400
        self.message = message
        super().__init__(self.message)


class ServiceException(Exception):
    """
    服务异常
    用于处理服务内部错误
    """
    def __init__(self, code: int = 500, message: str = "服务内部错误"):
        self.code = code
        self.message = message
        super().__init__(self.message)
