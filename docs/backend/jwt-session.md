# JWT 登录态（allowlist）

登录二次校验用 Redis 中的当前有效 token（`jwt:token:{userId}`），**不是**对话域 `conversationService`。岗位详情等业务缓存仍走 `RedisUtil` 软失败；二者语义不同，禁止混用。

## 职责划分

| 组件 | 用途 | Redis 故障 |
|------|------|------------|
| `JwtSessionStore` / `RedisJwtSessionStore` | 登录 allowlist | 上抛 |
| `RedisUtil` 缓存 API | 岗位详情等 | 吞异常，返回 null/false |
| `RedisUtil` Stream | 异步任务 | 上抛（本模块不拆类） |
| `RateLimitAspect` / 验证码 | 直连 `StringRedisTemplate` | 各自处理 |

登录与带 token 的请求**不得**调用 `RedisUtil.setString` / `getString`：缓存 API 把 miss 与故障都变成 `null`，且 `setString` 会给 TTL 加 0–5% 抖动，可能比 JWT 7 天更早过期。

## Key 与 TTL

- Key：`RedisConstants.JWT_TOKEN_KEY + userId` → `jwt:token:{userId}`
- TTL：`JWT_TOKEN_TTL`（7 天），与 `JwtUtil` 过期一致
- 写入：`SET` 覆盖（`replace`），实现单点登录/后登录踢前登录
- 删除：`revoke` 预留登出，当前登录流程不先 DEL 再 SET

## 请求路径

```
登录：UserServiceImpl → JwtSessionStore.replace → 成功才返回 token
请求：JwtAuthenticationFilter → JwtUtil 验签 → JwtSessionStore.find → 匹配才写 UserHolder
```

Filter 三态：

1. **已认证**：验签通过且 `find` 的 token 与请求一致 → 写 `UserHolder` + `SecurityContext`，继续 filter chain。
2. **未登录**：无 token / 验签失败 / miss / 不匹配 → 不写上下文，继续 chain；公开接口可匿名；受保护接口由 Security 入口写 **401**（`USER_NOT_LOGIN` 1002）。
3. **存储不可用**：`find` 抛 `DataAccessException` → 写出 **503** Problem Details（`AUTH_STORE_UNAVAILABLE` 1012）后 **return**，不再 `doFilter`。避免 Redis 宕机被当成未登录。

登录时 `replace` 失败同样映射为 1012（经 `BusinessException` + `GlobalExceptionHandler`），**不返回 token**。

`UserHolder` / `SecurityContext` 的清理在 `RequestContextCleanupFilter` 的 `finally`，不在 JWT Filter。见 [userholder-usage.md](userholder-usage.md)。

## 错误码与前端

| code | HTTP | 场景 | 前端 |
|------|------|------|------|
| 1002 | 401 | 未带有效会话 | `logoutCodes` 含 1002，且 HTTP 401 会清登录 |
| 1012 | 503 | Redis 登录态不可用 | **不会**踢登录（`VITE_SERVICE_LOGOUT_CODES` 不含 1012） |

Filter 与 Security 入口共用 `ProblemDetailHttpWriter`（尚未进入 MVC 时写 RFC 9457；响应已提交则 Writer 内跳过）。MVC 内异常见 [exception-handling.md](exception-handling.md)。

## 不采用

- 全局取消 `RedisUtil` 缓存吞异常（岗位缓存需要软失败）
- Redis 挂了只信 JWT 签名（allowlist 失效期间所有已签发 token 都会被当成有效）
- Spring Session（当前只要单用户当前 token，不必上 HTTP Session）
