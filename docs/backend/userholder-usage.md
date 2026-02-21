# UserHolder 使用指南

## 概述

`UserHolder` 是基于 ThreadLocal 实现的用户信息持有者，用于：
1. **避免重复解析HTTP请求**：用户信息在过滤器层解析一次，存储到ThreadLocal中
2. **简化获取用户信息**：在Controller、Service层直接调用静态方法即可获取
3. **扩展性强**：后续可以轻松添加更多用户信息字段（如角色、权限等）

## 核心类说明

### 1. UserContext - 用户上下文

存储用户完整信息的实体类：

```java
public class UserContext {
    private Long userId;      // 用户ID
    private String username;  // 用户名
    private String email;     // 邮箱（可选）
    // 后续可以添加更多字段：角色、权限、部门等
}
```

### 2. UserHolder - 用户持有者

提供静态方法获取当前线程的用户信息：

```java
public class UserHolder {
    // 获取用户ID
    public static Long getUserId()
    
    // 获取用户名
    public static String getUsername()
    
    // 获取邮箱
    public static String getEmail()
    
    // 获取完整用户上下文
    public static UserContext get()
    
    // 检查是否已登录
    public static boolean isAuthenticated()
}
```

## 使用示例

### 在Controller中使用

```java
@RestController
@RequestMapping("/api/users")
public class UserController {

    @GetMapping("/profile")
    public Result<UserDTO> getCurrentUserProfile() {
        // 直接获取当前登录用户ID，无需注入HttpServletRequest
        Long userId = UserHolder.getUserId();
        String username = UserHolder.getUsername();
        
        log.info("当前用户: userId={}, username={}", userId, username);
        
        // 查询用户信息
        UserDTO userDTO = userService.getUserById(userId);
        return Result.success(userDTO);
    }
    
    @GetMapping("/me")
    public Result<UserContext> getCurrentUser() {
        // 获取完整的用户上下文
        UserContext userContext = UserHolder.get();
        return Result.success(userContext);
    }
}
```

### 在Service中使用

```java
@Service
public class OrderService {
    
    public OrderDTO createOrder(OrderCreateDTO createDTO) {
        // 在Service层直接获取当前用户ID，无需传递参数
        Long userId = UserHolder.getUserId();
        
        if (userId == null) {
            throw new BusinessException(401, "用户未登录");
        }
        
        // 创建订单时自动关联当前用户
        Order order = new Order();
        order.setUserId(userId);
        order.setCreateBy(userId);
        // ...
        
        return convertToDTO(order);
    }
}
```

### 在工具类中使用

```java
public class AuditUtil {
    
    public static void logOperation(String operation) {
        Long userId = UserHolder.getUserId();
        String username = UserHolder.getUsername();
        
        log.info("操作日志: userId={}, username={}, operation={}", 
                 userId, username, operation);
    }
}
```

## 工作原理

### 1. 过滤器层设置用户信息

在 `JwtAuthenticationFilter` 中：

```java
// 解析JWT Token获取用户信息
Long userId = JwtUtil.getUserIdFromToken(token);
String username = JwtUtil.getUsernameFromToken(token);

// 存储到ThreadLocal
UserContext userContext = UserContext.of(userId, username);
UserHolder.set(userContext);

// 请求结束后清除（在finally块中）
UserHolder.clear();
```

### 2. 业务层获取用户信息

在Controller、Service等任何地方：

```java
// 直接获取，无需传递HttpServletRequest
Long userId = UserHolder.getUserId();
```

## 性能优势

### 传统方式（每次解析HTTP请求）

```java
// Controller中需要注入HttpServletRequest
@GetMapping("/profile")
public Result<UserDTO> getProfile(HttpServletRequest request) {
    // 每次都要解析Token
    String token = request.getHeader("Authorization");
    Long userId = JwtUtil.getUserIdFromToken(token);
    // ...
}
```

**问题**：
- 每次请求都要解析Token
- 需要传递HttpServletRequest参数
- 代码冗余

### 使用UserHolder（解析一次）

```java
// 过滤器层解析一次
UserHolder.set(userContext);

// Controller中直接获取
@GetMapping("/profile")
public Result<UserDTO> getProfile() {
    Long userId = UserHolder.getUserId(); // 直接从ThreadLocal获取
    // ...
}
```

**优势**：
- Token只解析一次（在过滤器层）
- 无需传递参数
- 代码简洁
- 性能更好

## 扩展用户信息

### 添加更多字段到UserContext

```java
@Data
public class UserContext {
    private Long userId;
    private String username;
    private String email;
    
    // 新增字段
    private String role;           // 角色
    private List<String> permissions; // 权限列表
    private Long departmentId;    // 部门ID
    // ...
}
```

### 在过滤器中设置扩展信息

```java
// 从数据库查询完整用户信息
User user = userService.getUserById(userId);

// 创建包含扩展信息的上下文
UserContext userContext = UserContext.builder()
    .userId(user.getId())
    .username(user.getUsername())
    .email(user.getEmail())
    .role(user.getRole())
    .permissions(user.getPermissions())
    .build();

UserHolder.set(userContext);
```

### 在业务层使用扩展信息

```java
// 获取角色
String role = UserHolder.get().getRole();

// 检查权限
boolean hasPermission = UserHolder.get()
    .getPermissions()
    .contains("ORDER_CREATE");
```

## 注意事项

### 1. ThreadLocal清理

`JwtAuthenticationFilter` 已经在 `finally` 块中调用 `UserHolder.clear()`，确保请求结束后清理ThreadLocal，避免内存泄漏。

### 2. 异步场景

如果在异步线程中使用，需要手动传递用户信息：

```java
CompletableFuture.runAsync(() -> {
    // 异步线程中无法访问主线程的ThreadLocal
    // 需要先获取用户信息
    UserContext userContext = UserHolder.get();
    
    // 在异步线程中设置
    UserHolder.set(userContext);
    try {
        // 执行业务逻辑
    } finally {
        UserHolder.clear();
    }
});
```

### 3. 未登录情况

```java
// 检查是否已登录
if (!UserHolder.isAuthenticated()) {
    throw new BusinessException(401, "用户未登录");
}

// 或者检查用户ID是否为null
Long userId = UserHolder.getUserId();
if (userId == null) {
    throw new BusinessException(401, "用户未登录");
}
```

## 迁移指南

### 从SecurityUtil迁移到UserHolder

**旧代码**：
```java
Long userId = SecurityUtil.getCurrentUserId();
```

**新代码**：
```java
Long userId = UserHolder.getUserId();
```

`SecurityUtil` 已经更新为优先使用 `UserHolder`，旧代码仍然可以工作，但建议迁移到 `UserHolder`。

## 总结

- ✅ **性能优化**：避免重复解析HTTP请求
- ✅ **代码简化**：无需传递HttpServletRequest参数
- ✅ **易于扩展**：可以轻松添加更多用户信息字段
- ✅ **线程安全**：基于ThreadLocal，每个请求独立
- ✅ **自动清理**：过滤器自动清理，避免内存泄漏
