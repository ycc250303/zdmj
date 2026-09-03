# 异常处理

成功响应仍是 `Result`（`code=0`）。失败一律 RFC 9457 `application/problem+json`，业务码写在顶层 `code`（`ProblemDetail` properties，Jackson mixin 展开）。前端见 `client/src/service/request/api-error.ts`。

不要启用 `spring.mvc.problemdetails.enabled`：已有 `GlobalExceptionHandler extends ResponseEntityExceptionHandler`，Boot 的自动 Handler 因 `@ConditionalOnMissingBean` 不会生效。

## 分层

| 该抛什么 | 场景 |
| --- | --- |
| `new BusinessException(ErrorCode)` | 可预期业务/运维失败（未登录、不存在、限流、Redis 登录态不可用） |
| `new BusinessException(ErrorCode, detail)` | 同一错误码需补充说明（权限、校验细节） |
| `new BusinessException(ErrorCode, cause)` / 带 detail+cause | 需要保留根因日志时 |
| 编程错误（NPE、非法状态、提示词缺失） | **不要**包成业务码；落到 500 + `SYSTEM_EXCEPTION`（1008） |

禁止：`new BusinessException(整数码, msg)`、裸 `String` 构造、用 `IllegalArgumentException` 表示用户入参错误、用 `*_FAILED` 掩盖实现缺陷（本轮未改目录，新增勿再扩）。

## 两出口

```
Service / Aspect / Controller
  → throw BusinessException(ErrorCode)
  → GlobalExceptionHandler
  → ProblemDetailSupport.of(ErrorCode, detail)
  → application/problem+json

Spring MVC（校验、404/405、请求体不可读、超限上传……）
  → ResponseEntityExceptionHandler
  → createResponseEntity 补 code / instance / Content-Type

Filter / Security（尚未进 MVC）
  → ProblemDetailHttpWriter.write(response, ErrorCode)
  → 同一套 ProblemDetailSupport
```

Writer 在 `response.isCommitted()` 时跳过；Security EntryPoint **不必**再判断。JWT Redis 故障写 `AUTH_STORE_UNAVAILABLE`（1012，503）后中断 chain，见 [jwt-session.md](jwt-session.md)。限流抛 `RATE_LIMIT_EXCEEDED`（1011，429），见 [rate-limit.md](rate-limit.md)。

## MVC 状态 → 业务码

已带 `code` 的 `ProblemDetail`（业务异常、校验拼接、`HttpMessageNotReadable`）不再改写。其余按 HTTP 状态：

- 400 → 1001；`HttpMessageNotReadableException` **按类型** → 1004（不再解析异常消息）
- 401 → 1002；403 → 1003；404 / 405 → 1006
- 413 → 1010；5xx → 1008

校验（`@Valid` / `ConstraintViolation` / `@ModelAttribute` 绑定）detail 拼接字段消息，码为 1001。

## 类

`com.zdmj.common.exception`：`ErrorCode`、`BusinessException`、`GlobalExceptionHandler`、`ProblemDetailSupport`、`ProblemDetailHttpWriter`。
