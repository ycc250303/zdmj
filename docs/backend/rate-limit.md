# 限流组件设计

## 概述

方法级分布式限流：在 Controller 方法上标注 `@RateLimit`，由 AOP 切面在请求进入业务逻辑前执行 Redis Lua 原子计数；超限抛出 `BusinessException(RATE_LIMIT_EXCEEDED)`，由全局异常处理器返回 HTTP 429。

**技术选型**

| 项 | 选择 |
|----|------|
| 触发方式 | Spring AOP `@Around` |
| 存储 | Redis（`StringRedisTemplate`） |
| 算法 | 固定窗口 + `INCR` + `PEXPIRE` |
| 原子性 | Lua 脚本 `lua/rate_limit.lua` |
| 异常 | `ErrorCode.RATE_LIMIT_EXCEEDED`（1011，HTTP 429） |

**依赖**：`spring-boot-starter-aop`、`spring-boot-starter-data-redis`。

---

## 模块结构

```
com.zdmj.common.annotation.RateLimit     # 声明式注解（可重复）
com.zdmj.common.aspect.RateLimitAspect   # AOP 切面 + Key 生成
resources/lua/rate_limit.lua             # Redis 计数脚本
```

切面在 `@PostConstruct` 等价逻辑中通过静态块加载 Lua（`DefaultRedisScript`），与 `VerificationCodeServiceImpl` 的脚本加载方式一致。

---

## 执行流程

```
HTTP 请求
  → RateLimitAspect.around()
  → 读取方法上全部 @RateLimit 规则
  → 逐条：生成 Redis Key → execute(lua) → 1 放行 / 0 拒绝
  → 任一规则失败 → throw BusinessException(1011)
  → 全部通过 → joinPoint.proceed()
```

同一方法多个 `@RateLimit 为 **与（AND）** 关系：每条规则独立计数，**全部通过**才进入方法体。

---

## 注解参数

| 参数 | 类型 | 默认 | 说明 |
|------|------|------|------|
| `dimension` | `Dimension` | `GLOBAL` | 限流维度，见下表 |
| `count` | `double` | （必填） | 窗口内最大请求数 |
| `interval` | `long` | `1` | 窗口长度数值 |
| `timeUnit` | `TimeUnit` | `SECONDS` | 窗口时间单位 |

窗口毫秒数 = `timeUnit.toMillis(interval)`。  
示例：`count=10, interval=1, timeUnit=MINUTES` → 每分钟最多 10 次。

### 限流维度

| 维度 | 计数范围 | Key 后缀依据 |
|------|---------|-------------|
| `GLOBAL` | 全站该接口共享 | 无后缀 |
| `IP` | 每个客户端 IP | `X-Forwarded-For` → `X-Real-IP` → `RemoteAddr` |
| `USER` | 每个登录用户 | `UserHolder.getUserId()`；未登录为 `anonymous` |

**注意**

- 限流标在 **Controller 方法**上，不标 Service。
- 登录、发验证码等公开接口勿单独使用 `USER` 维度（未登录用户会落入同一 `anonymous` 桶）。
- `USER` 维度依赖 `JwtAuthenticationFilter` 已写入 `UserHolder`；切面执行时点在 Filter 之后，时序正确。

---

## Redis Key 设计

格式（实现在 `RateLimitAspect.rateLimitKey`）：

```
ratelimit:{ClassName:methodName}:<dimension>[:<suffix>]
```

示例：

```
ratelimit:{UserController:login}:global
ratelimit:{UserController:login}:ip:192.168.1.1
ratelimit:{UserController:updateCurrentUser}:user:42
```

`{ClassName:methodName}` 为 Redis Cluster **hash tag**，便于同方法相关 key 路由到同一 slot。Key 前缀与 builder 内聚在切面内，不放入 `RedisConstants`。

---

## Lua 脚本

路径：`backend/zdmj/src/main/resources/lua/rate_limit.lua`

| 入参 | 含义 |
|------|------|
| `KEYS[1]` | 限流 key |
| `ARGV[1]` | 窗口大小（毫秒） |
| `ARGV[2]` | 窗口内上限 |

逻辑：`INCR` → 首次请求设 `PEXPIRE` → `count <= limit` 返回 `1`（放行），否则 `0`（拒绝）。

**算法特点**：实现简单、性能好；窗口边界可能存在短时突发（固定窗口固有问题）。当前未实现滑动窗口或令牌桶等待。

---

## 超限响应

由 `GlobalExceptionHandler` 统一处理，无需额外配置：

```json
{
  "title": "Too Many Requests",
  "status": 429,
  "detail": "请求过于频繁，请稍后再试",
  "code": 1011
}
```

Content-Type：`application/problem+json`（RFC 9457）。

---

## 使用示例

```java
import com.zdmj.common.annotation.RateLimit;
import java.util.concurrent.TimeUnit;

// 单规则：全局限流，每秒 10 次
@RateLimit(count = 10)
@GetMapping("/example")
public Result<?> example() { ... }

// 多规则：GLOBAL + IP 同时生效
@PostMapping("/login")
@RateLimit(dimension = RateLimit.Dimension.GLOBAL, count = 100, interval = 1, timeUnit = TimeUnit.MINUTES)
@RateLimit(dimension = RateLimit.Dimension.IP, count = 10, interval = 1, timeUnit = TimeUnit.MINUTES)
public Result<?> login(...) { ... }

// 已登录接口：按用户限流
@PutMapping("/me")
@RateLimit(dimension = RateLimit.Dimension.USER, count = 20, interval = 1, timeUnit = TimeUnit.MINUTES)
public Result<?> updateMe(...) { ... }
```

### 当前已接入（UserController）

| 接口 | 规则 |
|------|------|
| `POST /users/login` | GLOBAL 100/min + IP 10/min |
| `POST /users/verification-codes` | GLOBAL 200/min + IP 5/min |
| `PUT /users/me` | USER 20/min |
| `GET /users/validation/username` | GLOBAL 10/min + IP 5/min |

---

## 测试

单元测试：`RateLimitAspectTest`（Mock `StringRedisTemplate.execute`）

```bash
cd backend/zdmj
mvn test -Dtest=RateLimitAspectTest
```

本地联调（示例，`validation/username` IP 限制 5 次/分钟）：

```bash
for i in 1 2 3 4 5 6; do
  curl -s -w "\nHTTP:%{http_code}\n" \
    "http://localhost:8080/api/zdmj/users/validation/username?username=t$i"
done
```

第 6 次应返回 HTTP 429。

---

## 已知限制与扩展方向

| 项 | 现状 |
|----|------|
| 降级 / fallback | 无；超限仅抛业务异常 |
| 阻塞等待 | 无；不支持「排队等令牌」 |
| 配置外置 | 阈值写死在注解，未接 `application.yml` |
| 指标 | 未接入 Micrometer 限流计数 |
| 算法 | 固定窗口；可按需换滑动窗口 Lua |

扩展时优先保持：**注解声明 → 切面编排 → Lua 原子计数** 三层边界不变。
