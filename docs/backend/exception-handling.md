# 异常处理

成功响应仍是 `Result`（`code=0`）。失败一律 RFC 9457 `application/problem+json`。

- **业务异常**（`BusinessException`）：顶层带 `code`（`ProblemDetail` properties，Jackson mixin 展开）
- **Spring MVC 异常**（404/405、JSON 不可读、上传超限等）：框架默认 Problem Details，**不**再翻译成业务码。前端登出认 HTTP 401 / `code=1002`，见 `client/src/service/request/api-error.ts`
- **Bean Validation**：拼接字段消息，码为 1001

不要启用 `spring.mvc.problemdetails.enabled`：已有 `GlobalExceptionHandler extends ResponseEntityExceptionHandler`，Boot 自动 Handler 因 `@ConditionalOnMissingBean` 不会生效。

## 分层

| 该抛什么 | 场景 |
| --- | --- |
| `new BusinessException(ErrorCode)` | 可预期业务/运维失败 |
| `new BusinessException(ErrorCode, detail)` | 同一错误码需补充说明 |
| `new BusinessException(ErrorCode, cause)` / 带 detail+cause | 需要保留根因日志时 |
| 编程错误 | **不要**包成业务码；落到 500 + `SYSTEM_EXCEPTION`（1008） |

禁止：整数码构造、裸 `String` 构造、用 `IllegalArgumentException` 表示用户入参错误。

## 两出口

```
Service / Aspect
  → throw BusinessException(ErrorCode)
  → GlobalExceptionHandler
  → ProblemDetailSupport.of → 带 code

Spring MVC
  → ResponseEntityExceptionHandler
  → createResponseEntity 只补 instance / Content-Type

Filter / Security（尚未进 MVC）
  → ProblemDetailHttpWriter.write(response, ErrorCode)
```

Writer 在 `response.isCommitted()` 时跳过。JWT Redis 故障写 `AUTH_STORE_UNAVAILABLE`（1012，503），见 [jwt-session.md](jwt-session.md)。限流抛 `RATE_LIMIT_EXCEEDED`（1011，429），见 [rate-limit.md](rate-limit.md)。

## 类

`ErrorCode`、`BusinessException`、`GlobalExceptionHandler`、`ProblemDetailSupport`、`ProblemDetailHttpWriter`。
