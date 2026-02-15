# 单体服务开发最佳实践指南

## 概述

单体服务（Monolith）架构简单、开发效率高、部署方便，适合大多数中小型项目。本文档总结单体服务开发的核心原则和最佳实践。

## 核心原则

### 1. 清晰的代码组织结构

#### ✅ 推荐的项目结构

```
backend/zdmj/src/main/java/com/zdmj/
├── common/                      # 公共模块
│   ├── context/                # 上下文（UserHolder等）
│   ├── util/                   # 工具类
│   ├── exception/              # 异常处理
│   └── config/                 # 公共配置
├── userauthservice/            # 用户认证服务模块
│   ├── controller/             # 控制器
│   ├── service/                # 服务层
│   │   ├── UserService.java   # 接口
│   │   └── impl/               # 实现
│   ├── mapper/                 # 数据访问层
│   ├── entity/                 # 实体类
│   └── dto/                    # 数据传输对象
├── orderservice/               # 订单服务模块（示例）
│   ├── controller/
│   ├── service/
│   └── entity/
└── config/                     # 配置类
    ├── SecurityConfig.java
    └── JwtAuthenticationFilter.java
```

**原则**：
- 按业务模块组织代码，而不是按技术层次
- 每个模块包含完整的MVC结构
- 公共代码放在 `common` 模块

### 2. 分层架构（清晰的责任划分）

#### 标准三层架构

```
Controller层（表现层）
    ↓
Service层（业务逻辑层）
    ↓
Mapper层（数据访问层）
    ↓
Database（数据库）
```

#### ✅ 各层职责

**Controller层**：
- 接收HTTP请求
- 参数校验
- 调用Service层
- 返回响应

```java
@RestController
@RequestMapping("/api/users")
public class UserController {
    
    @Autowired
    private UserService userService;
    
    @PostMapping("/register")
    public Result<UserDTO> register(@Valid @RequestBody UserRegisterDTO dto) {
        // 只负责接收请求和返回响应，业务逻辑在Service层
        UserDTO userDTO = userService.register(dto);
        return Result.success("注册成功", userDTO);
    }
}
```

**Service层**：
- 业务逻辑处理
- 事务管理
- 调用Mapper层
- 业务规则校验

```java
@Service
public class UserServiceImpl implements UserService {
    
    @Autowired
    private UserMapper userMapper;
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserDTO register(UserRegisterDTO dto) {
        // 业务逻辑：检查用户名、验证码、创建用户等
        if (existsByUsername(dto.getUsername())) {
            throw new BusinessException(400, "用户名已存在");
        }
        // ...
        userMapper.insert(user);
        return convertToDTO(user);
    }
}
```

**Mapper层**：
- 数据库操作
- SQL映射
- 数据转换

```java
@Mapper
public interface UserMapper {
    int insert(User user);
    User selectById(Long id);
}
```

### 3. 统一异常处理

#### ✅ 全局异常处理器

```java
@RestControllerAdvice
public class GlobalExceptionHandler {
    
    @ExceptionHandler(BusinessException.class)
    public Result<?> handleBusinessException(BusinessException e) {
        return Result.error(e.getCode(), e.getMessage());
    }
    
    @ExceptionHandler(Exception.class)
    public Result<?> handleException(Exception e) {
        log.error("系统异常", e);
        return Result.error(500, "系统内部错误");
    }
}
```

**好处**：
- 统一错误响应格式
- 避免Controller中重复try-catch
- 便于错误日志收集

### 4. 统一响应格式

#### ✅ 已实现：Result类

```java
public class Result<T> {
    private Integer code;  // 状态码
    private String msg;    // 消息
    private T data;        // 数据
}
```

**所有接口统一返回Result**：
- 成功：`Result.success(data)`
- 失败：`Result.error("错误信息")`

### 5. 参数校验

#### ✅ 使用Bean Validation

```java
@Data
public class UserRegisterDTO {
    @NotBlank(message = "用户名不能为空")
    @Size(min = 3, max = 50, message = "用户名长度必须在3-50个字符之间")
    private String username;
    
    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    private String email;
}
```

**Controller中使用@Valid**：
```java
@PostMapping("/register")
public Result<UserDTO> register(@Valid @RequestBody UserRegisterDTO dto) {
    // 参数校验失败会自动返回400错误
}
```

### 6. 事务管理

#### ✅ 使用@Transactional

```java
@Service
public class OrderServiceImpl {
    
    @Transactional(rollbackFor = Exception.class)
    public void createOrder(OrderCreateDTO dto) {
        // 多个数据库操作，要么全部成功，要么全部回滚
        orderMapper.insert(order);
        inventoryMapper.updateStock(productId, quantity);
        paymentMapper.insertPayment(payment);
    }
}
```

**注意事项**：
- 只在Service层使用事务
- 使用 `rollbackFor = Exception.class` 确保所有异常都回滚
- 避免在Controller层使用事务

### 7. 日志规范

#### ✅ 结构化日志

```java
@Slf4j
@Service
public class UserServiceImpl {
    
    public UserDTO register(UserRegisterDTO dto) {
        log.info("用户注册开始: username={}, email={}, traceId={}", 
                 dto.getUsername(), dto.getEmail(), TraceUtil.getTraceId());
        
        try {
            // 业务逻辑
            log.info("用户注册成功: userId={}, username={}", user.getId(), user.getUsername());
        } catch (Exception e) {
            log.error("用户注册失败: username={}, error={}", dto.getUsername(), e.getMessage(), e);
            throw e;
        }
    }
}
```

**日志级别**：
- `DEBUG`：调试信息，开发时使用
- `INFO`：关键业务流程记录
- `WARN`：警告信息，不影响功能
- `ERROR`：错误信息，需要关注

### 8. 代码复用和DRY原则

#### ✅ 提取公共方法

```java
// ❌ 错误：重复代码
public void createOrder1() {
    Order order = new Order();
    order.setCreateAt(LocalDateTime.now());
    order.setUpdateAt(LocalDateTime.now());
    // ...
}

public void createOrder2() {
    Order order = new Order();
    order.setCreateAt(LocalDateTime.now());
    order.setUpdateAt(LocalDateTime.now());
    // ...
}

// ✅ 正确：提取公共方法
private void setCreateTime(BaseEntity entity) {
    LocalDateTime now = LocalDateTime.now();
    entity.setCreateAt(now);
    entity.setUpdateAt(now);
}
```

### 9. 数据库设计原则

#### ✅ 命名规范

```sql
-- 表名：小写+下划线，复数形式
CREATE TABLE users (...);
CREATE TABLE orders (...);

-- 字段名：小写+下划线
CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(50),
    create_at TIMESTAMPTZ,
    update_at TIMESTAMPTZ
);
```

#### ✅ 索引优化

```sql
-- 为常用查询字段添加索引
CREATE INDEX idx_users_username ON users(username);
CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_orders_user_id ON orders(user_id);
```

#### ✅ 软删除（可选）

```sql
-- 添加删除标记字段
ALTER TABLE users ADD COLUMN deleted BOOLEAN DEFAULT FALSE;
CREATE INDEX idx_users_deleted ON users(deleted);

-- 查询时过滤已删除数据
SELECT * FROM users WHERE deleted = FALSE;
```

### 10. 安全性原则

#### ✅ 密码加密

```java
// 使用BCrypt加密密码
String hashedPassword = PasswordUtil.encode(rawPassword);

// 验证密码
boolean matches = PasswordUtil.matches(rawPassword, hashedPassword);
```

#### ✅ SQL注入防护

```java
// ✅ 正确：使用参数化查询（MyBatis自动处理）
@Select("SELECT * FROM users WHERE username = #{username}")
User selectByUsername(@Param("username") String username);

// ❌ 错误：字符串拼接（容易SQL注入）
String sql = "SELECT * FROM users WHERE username = '" + username + "'";
```

#### ✅ XSS防护

```java
// 前端转义HTML特殊字符
// 后端验证和清理用户输入
@NotBlank
@Size(max = 100)
private String username; // 限制长度，防止恶意输入
```

### 11. 性能优化

#### ✅ 数据库查询优化

```java
// ❌ 错误：N+1查询问题
List<Order> orders = orderMapper.selectAll();
for (Order order : orders) {
    User user = userMapper.selectById(order.getUserId()); // 循环查询
}

// ✅ 正确：批量查询或JOIN
List<Long> userIds = orders.stream().map(Order::getUserId).collect(Collectors.toList());
List<User> users = userMapper.selectByIds(userIds); // 批量查询
```

#### ✅ 缓存使用

```java
@Service
public class UserServiceImpl {
    
    @Cacheable(value = "users", key = "#id")
    public UserDTO getUserById(Long id) {
        return userMapper.selectById(id);
    }
    
    @CacheEvict(value = "users", key = "#user.id")
    public void updateUser(User user) {
        userMapper.update(user);
    }
}
```

### 12. 代码质量

#### ✅ 命名规范

```java
// 类名：大驼峰，见名知意
public class UserService {}
public class OrderController {}

// 方法名：小驼峰，动词开头
public void createUser() {}
public UserDTO getUserById() {}
public boolean existsByUsername() {}

// 变量名：小驼峰，见名知意
String username;
Long userId;
List<UserDTO> userList;
```

#### ✅ 注释规范

```java
/**
 * 用户服务接口
 * 
 * @author YourName
 * @since 2024-02-12
 */
public interface UserService {
    
    /**
     * 用户注册
     * 
     * @param registerDTO 注册信息
     * @return 用户信息
     * @throws BusinessException 如果用户名已存在或验证码错误
     */
    UserDTO register(UserRegisterDTO registerDTO);
}
```

### 13. 测试

#### ✅ 单元测试

```java
@SpringBootTest
class UserServiceTest {
    
    @Autowired
    private UserService userService;
    
    @Test
    void testRegister() {
        UserRegisterDTO dto = new UserRegisterDTO();
        dto.setUsername("testuser");
        dto.setEmail("test@example.com");
        dto.setPassword("123456");
        dto.setVerificationCode("123456");
        
        UserDTO result = userService.register(dto);
        
        assertNotNull(result);
        assertEquals("testuser", result.getUsername());
    }
}
```

### 14. 配置管理

#### ✅ 环境配置分离

```yaml
# application.yml - 公共配置
spring:
  application:
    name: zdmj

# application-dev.yml - 开发环境
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/zdmj

# application-prod.yml - 生产环境
spring:
  datasource:
    url: jdbc:postgresql://prod-server:5432/zdmj
```

### 15. API设计规范

#### ✅ RESTful风格

```java
// 资源命名：使用名词，复数形式
GET    /api/users          // 获取用户列表
GET    /api/users/{id}     // 获取单个用户
POST   /api/users          // 创建用户
PUT    /api/users/{id}     // 更新用户
DELETE /api/users/{id}     // 删除用户
```

#### ✅ HTTP状态码

```java
200 OK           // 成功
201 Created      // 创建成功
400 Bad Request  // 请求参数错误
401 Unauthorized // 未认证
403 Forbidden    // 无权限
404 Not Found    // 资源不存在
500 Internal Server Error // 服务器错误
```

## 当前项目检查清单

### 已实现 ✅

- [x] 统一响应格式（Result类）
- [x] 全局异常处理（GlobalExceptionHandler）
- [x] 参数校验（Bean Validation）
- [x] JWT认证（JwtAuthenticationFilter）
- [x] 用户信息持有者（UserHolder）
- [x] 分布式追踪（TraceFilter）
- [x] 密码加密（BCrypt）
- [x] 事务管理（@Transactional）

### 建议改进

- [ ] 添加API版本号（/api/v1/users）
- [ ] 添加缓存支持（Redis）
- [ ] 完善单元测试
- [ ] 添加API文档（Swagger/OpenAPI）
- [ ] 日志文件配置（按日期分割）
- [ ] 数据库连接池优化
- [ ] 添加限流功能

## 常见反模式（避免）

### ❌ 1. 在Controller中写业务逻辑

```java
// ❌ 错误
@PostMapping("/register")
public Result<UserDTO> register(@RequestBody UserRegisterDTO dto) {
    // 业务逻辑不应该在Controller中
    if (userMapper.existsByUsername(dto.getUsername())) {
        return Result.error("用户名已存在");
    }
    User user = new User();
    // ...
    userMapper.insert(user);
    return Result.success(convertToDTO(user));
}

// ✅ 正确
@PostMapping("/register")
public Result<UserDTO> register(@Valid @RequestBody UserRegisterDTO dto) {
    UserDTO userDTO = userService.register(dto);
    return Result.success("注册成功", userDTO);
}
```

### ❌ 2. Service层直接返回Entity

```java
// ❌ 错误
public User getUserById(Long id) {
    return userMapper.selectById(id); // 直接返回Entity
}

// ✅ 正确
public UserDTO getUserById(Long id) {
    User user = userMapper.selectById(id);
    return convertToDTO(user); // 返回DTO
}
```

### ❌ 3. 忽略异常处理

```java
// ❌ 错误
public void deleteUser(Long id) {
    userMapper.deleteById(id); // 如果失败怎么办？
}

// ✅ 正确
public void deleteUser(Long id) {
    User user = userMapper.selectById(id);
    if (user == null) {
        throw new BusinessException(404, "用户不存在");
    }
    int result = userMapper.deleteById(id);
    if (result <= 0) {
        throw new BusinessException(500, "删除用户失败");
    }
}
```

## 总结

**单体服务的优势**：
- ✅ 开发简单，部署方便
- ✅ 性能好（无网络调用）
- ✅ 事务管理简单（本地事务）
- ✅ 调试方便（单进程）

**关键原则**：
1. **清晰的代码组织**：按业务模块组织
2. **分层架构**：Controller → Service → Mapper
3. **统一规范**：响应格式、异常处理、日志
4. **代码质量**：命名规范、注释、测试
5. **安全性**：密码加密、SQL注入防护
6. **性能优化**：数据库查询、缓存

遵循这些原则，可以构建出高质量、易维护的单体服务。
