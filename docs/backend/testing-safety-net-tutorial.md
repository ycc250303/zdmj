# 测试安全网前四步源码课程

本课程讲解 [`testing-safety-net-plan.md`](testing-safety-net-plan.md) 的前四步：测试运行基础、隔离环境、公共契约与认证、核心业务贯通与越权测试。

## 学习目标与覆盖范围

- 基准提交：`07918d9`，分支 `main`；课程依据当前工作区中的未提交修改。
- 四课状态均为已展开。
- 读完后应当能够解释 Maven 怎样加载 JaCoCo 与 Mockito Agent，以及 `test`、`verify` 分别运行哪些测试。
- 读完后应当能够说明 PostgreSQL、Redis、Schema、数据清理和外部服务替身怎样组成隔离环境。
- 读完后应当能够沿 MVC 与 Security 两个错误出口解释 RFC 9457 契约，并说明合法 JWT 怎样恢复用户和清理上下文。
- 读完后应当能够设计贯通测试，验证成功响应、数据库状态、事务回滚、稳定排序、SSE 与跨用户资源隔离。
- 已覆盖前四步。第五步的持久化测试见实施方案进度；异步消费、ArchUnit 与 CI 最终命令迁移属于后续步骤。

## 前置知识

- Maven 生命周期：`test`、`integration-test`、`verify`。
- Spring Boot 测试：`@WebMvcTest`、`@SpringBootTest`、`@DynamicPropertySource`、`@TestConfiguration`。
- Testcontainers：测试进程启动临时容器并取得随机映射端口。
- PostgreSQL Schema、Redis allowlist、Spring Security Filter Chain、RFC 9457 Problem Details。

## 课程大纲

### 课程 1：测试进程怎样稳定启动并进入正确生命周期

- 状态：已展开。
- 主链：`Maven → JaCoCo prepare-agent → Surefire/Failsafe argLine → 测试进程 → 报告与门禁`。
- 必读：`backend/zdmj/pom.xml`。

### 课程 2：容器隔离环境怎样从启动走到每个用例前的干净状态

- 状态：已展开。
- 主链：`IntegrationTestBase → TestContainers → SchemaInitializer → Spring 测试配置 → DatabaseCleaner → 测试辅助对象`。
- 必读：`backend/zdmj/src/test/java/com/zdmj/testsupport/` 与 `ContainerIsolationIT`。

### 课程 3：公共契约与认证怎样从请求走到稳定响应

- 状态：已展开。
- 主链 A：`Controller/参数绑定 → GlobalExceptionHandler → Problem Details`。
- 主链 B：`Security Filter Chain → JwtAuthenticationFilter → ProblemDetailHttpWriter → Problem Details`。
- 主链 C：`合法 JWT → Redis allowlist → 用户上下文 → 当前用户接口 → finally 清理`。
- 主链 D：`固定样本 → Result/PageDTO/DTO/枚举 → Jackson → 成功响应 JSON`。

### 课程 4：核心业务怎样贯通并拒绝跨用户访问

- 状态：已展开。
- 主链：`用户与 JWT → MockMvc → Security → Controller → Service → Mapper → PostgreSQL → HTTP 与数据库双重断言`。
- 必读：四个业务 `*FlowIT`、`AsyncTaskLifecycleIT`、`CrossUserAuthorizationIT` 与 `ApiClient`。

## 课程 1：测试运行基础

### 场景与目标

JDK 21 下 Mockito 动态附加 Agent 可能失败。第一步把 JaCoCo Agent 与 Mockito Agent 都放入 Maven 启动参数，并让单元测试、契约测试和集成测试进入各自生命周期。

```text
Maven test/verify
→ jacoco:prepare-agent 写入 jacoco.agent.argLine
→ Surefire 与 Failsafe 组合 JaCoCo、Mockito 参数
→ Surefire 运行 *Test、*ContractTest、*ArchitectureTest
→ Failsafe 运行 *IT
→ 生成报告并执行覆盖率门禁
```

### 必读顺序

1. `pom.xml` 的 `<properties>` 固定 JaCoCo、ArchUnit 版本，并预先声明空的 `jacoco.agent.argLine`，保证跳过 JaCoCo 时属性仍然合法。
2. `jacoco-maven-plugin:prepare-agent` 在 `initialize` 阶段把 Agent 参数写入独立属性，防止覆盖 Mockito 参数。
3. `maven-surefire-plugin` 将 `@{jacoco.agent.argLine}` 与 Mockito JAR 的 `-javaagent` 一起传给单元测试进程。
4. `maven-failsafe-plugin` 使用相同 Agent 参数，接收仓库根目录、空环境文件和单进程配置；`forkCount=1` 让集成测试共享容器。
5. JaCoCo 在 `verify` 生成报告，并分别检查 `common` 与普通重构包的覆盖率。

新增测试依赖包括 Spring Boot Testcontainers、JUnit Jupiter Testcontainers、PostgreSQL Testcontainers 和 ArchUnit JUnit 5。JUnit 4.12 直接依赖已经删除，测试目录没有 JUnit 4 Runner、Rule 或 `org.junit.Test`。

### 流程回顾任务

请自行复述：`jacoco.agent.argLine` 为什么需要独立保存，以及 Surefire 与 Failsafe 为什么都要包含 JaCoCo 和 Mockito Agent。

## 课程 2：隔离测试环境

### 场景与主链

集成测试使用真实 PostgreSQL、真实 Redis、真实 Mapper 与完整 Spring 上下文，同时隔离开发配置、开发数据和外部服务。

```text
集成测试继承 IntegrationTestBase
→ DynamicPropertySource 访问 TestContainers
→ 启动 pgvector/pgvector:pg15 与 redis:7
→ SchemaInitializer 执行 sql/pgsql.sql
→ Spring 加载 test Profile 与 ExternalAiStubConfig
→ 每个用例前 DatabaseCleaner.clean
→ 验证容器目标后清空数据库与 Redis
→ TestUsers/TestJwt 创建业务测试条件
```

### 启动与清理顺序

1. `IntegrationTestBase.registerContainers` 注入数据源、Redis、JWT 密钥和用户模型加密密钥；`@ActiveProfiles("test")` 加载 `application-test.yml`。
2. `TestContainers` 持有 JVM 级 PostgreSQL 与 Redis；首次访问静态字段时依次启动容器并调用 `SchemaInitializer.apply`。
3. `SchemaInitializer` 从 Failsafe 提供的 `zdmj.repo.root` 定位仓库，只执行 `sql/pgsql.sql`；属性缺失、文件不存在或 SQL 失败都会立即终止。
4. `ExternalAiStubConfig` 注册固定对话模型、1024 维 Embedding、邮件替身、对象存储替身、清理器、测试用户与 JWT 辅助 Bean；异步消费者在 test Profile 下禁用。
5. `DatabaseCleaner.clean` 取得实际 JDBC URL 与 Redis 端口，同当前容器映射信息比较；验证通过后执行 `TRUNCATE ... RESTART IDENTITY CASCADE` 和 `FLUSHDB`。

### `testsupport` 文件职责

- `IntegrationTestBase`：统一注解、动态属性、替身配置与每用例清理入口。
- `TestContainers`：持有共享容器和测试密钥。
- `SchemaInitializer`：用仓库唯一 Schema 初始化临时数据库。
- `DatabaseCleaner`：验证目标后清空数据库与 Redis；`DatabaseCleanerTest` 固定误清理拒绝行为。
- `TestUsers`：通过真实 `UserMapper` 创建用户 A、用户 B，并使用数据库生成的主键。
- `TestJwt`：调用真实 `JwtTokenService` 签发令牌，再写入 Redis `JwtSessionStore`。
- `ToggleJwtSessionStore`：正常状态委托真实 Redis，实现可控的认证存储故障。
- `ExternalAiStubConfig`、`TestMailSender`、`TestObjectStorage`：提供确定性结果，不访问供应商服务。

### 直接关联修改与边界

- `application.yml` 支持 `ZDMJ_ENV_FILE`；Failsafe 指向 `empty.env`，测试进程不会读取开发 `.env`。
- `application-test.yml` 不保存数据库、Redis、JWT 或加密凭据；值由动态容器属性提供。
- `FileUploadService` 在 test Profile 下不注册生产 Bean，存储模式为 `stub` 时不初始化 COS 客户端。
- `sql/pgsql.sql` 保留 `vector` 与 `pg_trgm` 扩展，HNSW 索引由 pgvector 提供。
- 问题：test Profile 未把数据源和 Redis 属性声明为必填。影响：遗漏基类的集成测试可能继承本机默认地址。最小改法：使用无默认值占位符，并增加缺少动态属性时启动失败的测试。
- 问题：清理器当前比较 PostgreSQL 端口与数据库名、Redis 端口，尚未比较两个主机。影响：仅凭端口与库名无法完整证明连接属于容器。最小改法：同时比较容器主机并补充主机不一致拒绝测试。
- 容器共享依赖 Failsafe 单进程；开启并行执行前需要重新设计数据隔离。

### 完整流程与回顾任务

以“创建用户 A 并取得令牌”为例：Failsafe 启动测试进程；容器启动后执行仓库 Schema；Spring 注入容器属性和替身；清理器验证目标并清空存储；`TestUsers` 插入用户；`TestJwt` 签名 JWT 并写入 Redis。下一用例开始前，数据库表、序列与 Redis 会再次重置。

请自行复述：数据源误指向开发数据库时，`DatabaseCleaner.clean` 会在哪一步终止，为什么此时尚未执行 `TRUNCATE`。

## 课程 3：公共契约与认证

### 场景与两个错误出口

请求进入 Controller 后的异常由 MVC 出口处理；认证过滤阶段的错误由 Security 出口处理。两个出口统一使用 `application/problem+json`。

```text
HTTP 请求 → RequestContextCleanupFilter
├─ 认证存储不可用 → JwtAuthenticationFilter → ProblemDetailHttpWriter → 503
├─ 没有建立认证 → AuthenticationEntryPoint → ProblemDetailHttpWriter → 401
└─ 进入 Controller
   ├─ 业务或 MVC 异常 → GlobalExceptionHandler → Problem Details
   └─ 成功 → Result 与 DTO JSON
→ finally 清理 UserHolder 与 SecurityContextHolder
```

业务错误与 Security 错误使用 `ProblemDetailSupport.of`；Spring MVC 自带错误沿用框架 Problem Detail。稳定字段包含 `status`、`title`、`detail`，业务错误还包含 `code`。MVC 出口通过 `createResponseEntity` 补充 `instance`，并保留 405 的 `Allow` 响应头。

### 主链 A：MVC Problem Details

1. `ContractProbeController` 只在 `mvc-contract` Profile 注册，提供业务异常、校验、404、429、500 与序列化固定样本；`WebMvcConfig` 增加 `/api/zdmj` 前缀。
2. `ProblemDetailsContractTest` 使用 `@WebMvcTest`，关闭过滤器；`ContractWebConfiguration` 避免加载带 Mapper 扫描的主启动类。
3. `GlobalExceptionHandler` 将业务异常和校验异常转换为带业务码的 Problem Details；非法 JSON、404、405 使用框架结果；未捕获异常对外固定为 `SYSTEM_EXCEPTION`。
4. `ProblemDetailSupport` 按 `ErrorCode` 生成状态、标题、详情和顶层 `code`。`GlobalExceptionHandlerTest` 用 standalone MockMvc 固定业务异常与未捕获异常；校验、非法 JSON、404、405 和 429 由 `ProblemDetailsContractTest` 固定。

七个契约分支分别固定：业务异常 404、校验错误 400、非法 JSON 400、资源不存在 404、方法不允许 405、限流 429、未捕获异常 500。断言覆盖媒体类型、稳定字段、`instance`、405 响应头以及异常类名、秘密文本、`trace` 不出现在响应中。

### 主链 B：Security Problem Details

1. `RequestContextCleanupFilter` 在请求开始前清除两个上下文，并在 `finally` 再次清除。
2. `JwtAuthenticationFilter` 读取 `Authorization`，调用 `JwtTokenService` 验签并提取用户编号与用户名。
3. `ToggleJwtSessionStore` 委托 `RedisJwtSessionStore` 查询真实 allowlist；令牌缺失、无法验签、Redis 没有令牌或令牌不一致时不会写入认证对象。
4. 受保护接口没有认证时，`SecurityConfig` 的 `AuthenticationEntryPoint` 通过 `ProblemDetailHttpWriter` 返回 401 `USER_NOT_LOGIN`。
5. Redis 查询抛出 `DataAccessException` 时，过滤器直接返回 503 `AUTH_STORE_UNAVAILABLE` 并终止本次过滤器链。

`SecurityProblemDetailsIT` 使用完整 Spring 上下文、完整过滤器链、真实 PostgreSQL 与真实 Redis，覆盖缺少令牌、无效令牌、Session 不一致和认证存储不可用；同时确认令牌、异常类型与内部故障文本不会回显。503 用例通过 `finally` 和 `@AfterEach` 恢复存储状态。

### 主链 C：合法 JWT 与身份隔离

1. `TestUsers.createPair` 创建用户 A 与用户 B，`TestJwt.authorize` 签发 JWT 并写入 Redis。
2. JWT 过滤器完成验签和 allowlist 比较，创建 `UserContext`，写入 `UserHolder` 与 `SecurityContextHolder`。
3. `PUT /api/zdmj/users/me` 进入 `UserController.updateCurrentUser` 和 `UserServiceImpl.updateCurrentUser`。
4. Service 通过 `UserHolder.requireUserId` 取得令牌对应的用户编号，更新真实 PostgreSQL。
5. 响应断言用户编号、用户名和姓名，并确认没有密码与令牌；请求结束后断言两个上下文为空。

`AuthenticationFlowIT` 的连续请求用例在同一线程依次使用两名用户的令牌；并发用例使用两个线程、`CountDownLatch` 和五秒有界超时。两类用例都验证响应身份与令牌一致，并确认上下文没有残留。

### 主链 D：成功响应序列化

`ApiSerializationContractTest` 使用同一 MVC 契约切片验证：

- `Result` 输出 `code/msg/data`，空值保留 JSON `null`；
- `PageDTO` 只输出 `list/total/page/limit/totalPages`，不泄漏框架分页字段；
- 异步任务的时间使用 ISO 本地日期时间，类型与状态输出整数码；
- 成功响应没有 `password`、`trace`。错误响应没有 `msg`、`data`，由 `ProblemDetailsContractTest` 固定。

`GlobalExceptionHandlerTest` 保护异常处理器；`ProblemDetailsContractTest` 保护 MVC 错误契约；`SecurityProblemDetailsIT` 保护 401、503 与 Redis 交互；`AuthenticationFlowIT` 保护合法 JWT、业务更新及身份隔离；`ApiSerializationContractTest` 保护成功信封与 JSON 形状。

### 后端开发需要掌握的知识

1. **认证链路与失败语义**：读 `JwtAuthenticationFilter.doFilterInternal`。`validateToken` 失败时不建立认证，allowlist miss 或令牌不一致也不建立认证，最终由 `AuthenticationEntryPoint` 返回 401；`JwtSessionStore.find` 抛出 `DataAccessException` 时立即写出 503。需要能够说明 401 表示无有效身份、403 表示身份有效但权限不足、503 表示认证依赖不可用，并为新增受保护接口构造合法 JWT。
2. **线程上下文生命周期**：读 `RequestContextCleanupFilter.doFilterInternal`。请求开始前和 `finally` 都会调用 `UserHolder.clear`、`SecurityContextHolder.clearContext`。需要理解 Servlet 线程会被复用，遗漏清理可能让后续请求继承旧身份；应当能用 `AuthenticationFlowIT.assertContextCleared` 和并发用例验证隔离。
3. **异常分类与 HTTP 契约**：读 `GlobalExceptionHandler`。`handleBusinessException` 保留业务码，`validationProblem` 生成校验错误，框架异常由 `ResponseEntityExceptionHandler` 处理，`handleUncaughtException` 记录根因并返回通用 500。需要能够判断新增异常应该进入哪个分支，并在 `createResponseEntity` 中理解 `Content-Type`、`instance` 和原有响应头的处理。
4. **响应模型与序列化**：读 `Result`、`PageDTO.of`、`AsyncTaskDTO`、`EducationResponse.getDegreeEnum`。需要明确成功响应和 Problem Details 的字段边界，能判断时间格式、空值、枚举码、分页字段变化是否会破坏客户端契约。
5. **按风险选择测试层级**：纯 Service 分支使用 JUnit 5 与 Mockito；HTTP 状态和 JSON 形状使用 `@WebMvcTest`；Security、Mapper、PostgreSQL、Redis 共同参与时继承 `IntegrationTestBase`。修改接口后需要断言状态、媒体类型、稳定字段、数据库结果和敏感信息缺失。

1. **隔离环境**：读 `IntegrationTestBase.registerContainers`、`TestContainers`、`SchemaInitializer.apply`。需要能沿“Docker → 容器映射端口 → 动态属性 → Spring Bean → Schema”定位启动失败，并确认测试没有读取开发连接信息。
2. **安全清理**：读 `DatabaseCleaner.clean`。需要理解目标校验发生在 `TRUNCATE` 和 `FLUSHDB` 之前，能够增加新业务表后的清理验证，并知道共享数据库条件下并行测试可能互相污染。
3. **确定性替身**：读 `ExternalAiStubConfig`、`TestMailSender`、`TestObjectStorage`、`ToggleJwtSessionStore`。需要能够为外部依赖提供固定成功或失败结果，并检查调用记录，避免自动化测试访问真实模型、邮件和云存储服务。
4. **测试生命周期**：读 `pom.xml` 的 Surefire、Failsafe、JaCoCo 配置。需要会使用 `test`、定向 `-Dtest`、定向 `-Dit.test` 和 `verify`，能从对应报告判断失败属于单元测试或集成测试，并避免两个 Java Agent 参数互相覆盖。

- Java Agent 的字节码插桩、JaCoCo 探针算法、Testcontainers 资源回收协议和 Docker 客户端内部实现无需作为当前学习重点。
- 掌握标准：能够给新接口补充成功、校验、认证和异常测试；能够解释一次失败停在过滤器、Controller、Service、Mapper、数据库或测试设施的哪一层；能够说明测试已经证明的行为和仍未覆盖的风险。

### 当前边界、完整流程与回顾任务

- 问题：Security 出口当前没有 `instance`。影响：认证错误响应无法直接标识失败请求 URI。最小改法：让 `ProblemDetailHttpWriter` 接收请求 URI，并为 401、403、503 增加断言。
- 问题：第三步没有覆盖 `AccessDeniedHandler` 的 403 `NO_PERMISSION`。影响：已认证用户权限不足的契约缺少集成测试保护。最小改法：增加确定性的受限端点和 403 用例。
- `ContractProbeController` 不进入普通集成测试与生产环境；非法枚举、时区、精度和数据库往返由第五步负责。第四步的业务贯通与越权测试已经放在 `integration` 对应子目录。

以用户 A 更新姓名为例：临时 PostgreSQL 创建用户，真实 JWT 工具签发令牌并写入临时 Redis；清理过滤器清除旧上下文；JWT 过滤器验签并比较 allowlist；匹配成功后写入认证上下文；Controller 调用 Service，Service 从 `UserHolder` 取得用户编号并更新数据库；Jackson 输出 `Result<UserResponse>`；`finally` 清除两个上下文。缺少令牌、Session 不一致、Redis 故障和 Controller 异常分别进入对应的 401、503 或 MVC 出口。

请自行复述：一条受保护请求从 `Authorization` 到 `UserHolder.requireUserId` 经过哪些检查；缺少令牌、Redis 故障和 Controller 业务异常分别由哪个组件写出响应。

## 课程 4：核心业务贯通与越权测试

### 场景、主链与阅读顺序

第四步验证真实业务能贯穿 HTTP、鉴权、事务和数据库，并证明用户 B 无法读取或改动用户 A 的资源。

主链：`TestUsers/TestJwt → ApiClient/MockMvc → Security Filter Chain → Controller → Service（UserHolder.requireUserId）→ Mapper → PostgreSQL → HTTP、业务结果、资源归属与数据库状态断言`。

先读 `ApiClient.requireOk/assertProblem`，理解成功与 Problem Details 断言；再读 `ResumeFlowIT`、`JobQueryFlowIT`、`KnowledgeFlowIT`、`ConversationFlowIT` 与 `AsyncTaskLifecycleIT`；最后读 `CrossUserAuthorizationIT`，观察同一资源在用户 A、用户 B 下的差异。

### 五条业务流程

1. `ResumeFlowIT` 从技能和简历创建进入 `ResumeServiceImpl.saveMyResumeContent`，验证 JSONB、教育/工作/项目/奖项写入，随后验证更新、删除及用户归属；失败回滚由 `TransactionBoundaryIT` 单独验证。
2. `JobQueryFlowIT` 创建三条岗位并固定 `updated_at`，经过 `JobServiceImpl.getPage/getDetail` 验证分页无重复、相同查询顺序稳定，以及名称、公司、行业、薪资筛选和详情字段。
3. `KnowledgeFlowIT` 经过文件上传替身创建两个文档，调用 `KnowledgeEmbeddingServiceImpl.vectorizeAndStore` 写入固定向量，再验证检索范围只返回指定文档；关闭系统知识且文档范围为空时结果为空。
4. `AsyncTaskLifecycleIT` 准备用户与岗位画像，提交后验证 `PENDING`，再读取 Redis Stream 消息并驱动消费，验证 `RUNNING`、`SUCCESS`、固定分数、维度、摘要及匹配结果归属。
5. `ConversationFlowIT` 调用 `MessageController.chatStream`，先断言异步请求已经启动，再用 `asyncDispatch` 接收固定 SSE；数据库查询结果必须按序号保存用户消息和助手消息，并用固定回复更新会话标题。

### 跨用户授权与后端掌握标准

`CrossUserAuthorizationIT.createUsers` 为两名用户分别签发合法令牌。每个用例都遵循“用户 A 创建并成功访问 → 用户 B 列表不可见或单项访问被拒绝 → 数据库字段和归属保持不变 → 用户 A 仍可完成操作”。简历及子项、会话、知识文档、匹配结果、职业发展报告、用户模型配置和上传文件均进入该验证；异步任务归属由 `AsyncTaskAuthorizationIT` 验证。

- 有独立资源编号且需要隐藏存在性的接口返回 404；显式权限错误返回 403；按当前用户过滤的列表或结果返回空。测试沿用每个接口已有语义。
- 后端开发必须掌握事务边界、稳定分页排序、`user_id` 归属条件、404/403/空结果的选择、异步请求测试和数据库状态断言；应当能够为新增用户资源补充“本人成功、他人不可见、他人写入失败、失败后数据不变”四类证据。
- 问题：当前 `CrossUserAuthorizationIT` 将多个领域的越权场景集中在一个类中。影响：测试继续增加后，定位与维护成本会上升。最小改法：达到文件规模限制时按领域拆成多个 `*AuthorizationIT`，共享 `ApiClient`、`TestUsers` 与 `TestJwt`，不抽象领域请求正文。

以简历保存为例：用户 A 的令牌恢复身份；Controller 把完整内容交给事务方法；Service 以当前用户编号读写技能及五类简历数据；Mapper 写入临时 PostgreSQL；响应与 SQL 同时验证内容和归属。用户 B 查询得到空列表，修改和删除收到 403，SQL 证明用户 A 的记录没有变化；失败回滚由事务边界测试验证。

请自行复述：为什么贯通测试需要同时检查 HTTP 响应和数据库；用户 B 访问资源时，哪些接口应该返回 404、403 或空结果；匹配任务怎样从入队走到查询结果。

## 验证记录

- 前八步已完成。验收命令为 `./mvnw -B -ntp clean verify`。
- 清理后 Surefire 运行 461 个用例，Failsafe 运行 44 个用例，JaCoCo 覆盖率和 ArchUnit 架构规则全部通过。
