# 测试安全网实施方案

本文供后续 Agent 直接执行，目标是在重构 `common` 前建立可重复、可定位、覆盖真实基础设施的测试安全网。执行过程中不得改变既有业务语义；发现缺陷时新增能够复现缺陷的测试，再按独立修复任务处理。

## 完成定义

- 唯一验收命令为 `./mvnw -B -ntp clean verify`，本地和 CI 均返回 0。
- 必需测试为 0 failures、0 errors、0 skipped，测试初始化错误同样阻断验收。
- 单元测试、接口契约测试、架构测试和集成测试具有明确的命名与执行阶段。
- PostgreSQL、Redis 集成测试只连接 Testcontainers 创建的实例，禁止读取开发环境连接信息。
- 简历、岗位、知识库、匹配和会话各有一条贯通 Controller、Service、Mapper 与数据库的正常路径。
- 关键资源具备跨用户查询、修改和删除拒绝测试。
- 当前异步任务具备创建、成功、失败、状态查询和重复消息测试。
- `common`、Security、异步任务关键包行覆盖率不低于 80%，分支覆盖率不低于 70%；其他纳入重构的包行覆盖率不低于 70%，分支覆盖率不低于 55%。
- 测试报告能够从失败名称定位业务能力、场景和预期结果。

## 当前基线

- 已有约 405 个测试，主要为 Mockito Service 单元测试。
- `GlobalExceptionHandlerTest` 已覆盖主要 Problem Details 场景。
- JWT、权限、异步消费者、JSONB 和主要业务服务已有部分单元测试。
- 当前 JDK 21 环境下 Mockito 动态附加 Agent 失败，导致大量测试初始化错误。
- 尚无 Testcontainers、Failsafe 集成测试、真实 PostgreSQL/Redis 测试和 ArchUnit。
- CI 尚未将单元测试、集成测试、覆盖率和架构规则统一为一条验收命令。

执行 Agent 开始工作时，应重新运行基线命令并以最新 Surefire 报告为准，不依赖本文中的数量判断。

## 执行进度

- 第一步至第四步已完成。
- 第五步已完成。契约测试位于 `contract/PaginationContractTest`、`TimeSerializationContractTest`、`EnumSerializationContractTest`。数据库往返位于 `integration/persistence`：`JsonbPersistenceIT`、`PaginationQueryIT`、`TimePersistenceIT`、`EnumCodePersistenceIT`、`TransactionBoundaryIT`、`LogicalReferenceIntegrityIT`。
- 会话消息先提交用户消息与助手占位，再调用模型和 SSE。应用时钟由 `TimeConfiguration` 提供，测试用 `MutableClock` 固定时刻。
- 第六步已完成。`integration/async` 在关闭消费循环的前提下读取一条消息并调用 `consumeRecord`，覆盖生命周期、失败、幂等、越权查询和 Stream 契约。双写窗口、固定消费者名称和缺少自动恢复不写入断言，留作后续异步重构验收项。人岗匹配等方法仍在事务内调用模型。
- 第七步、第八步已完成。`ModuleArchitectureTest` 用 `FreezingArchRule` 记录当前跨模块内部依赖，`archunit.properties` 禁止更新冻结记录。`./mvnw -B -ntp clean verify` 包含 Surefire、Failsafe、JaCoCo 和 ArchUnit。CI 使用 Temurin JDK 21 与 `./mvnw`；`main` 的 Pull Request 执行该门禁；后端部署等待同一提交的 CI 成功。

## 测试分类与目录

```text
backend/zdmj/src/test/java/com/zdmj/
├── architecture/                 # ArchUnit，类名 *ArchitectureTest
├── contract/                     # HTTP、异常、序列化，类名 *ContractTest
├── integration/                  # 容器集成测试，类名 *IT
│   ├── auth/
│   ├── resume/
│   ├── job/
│   ├── knowledge/
│   ├── conversation/
│   ├── async/
│   └── persistence/
└── testsupport/                  # 容器、数据清理、用户和外部服务替身

backend/zdmj/src/test/resources/
├── application-test.yml
├── archunit.properties
├── archunit_store/
└── empty.env
```

- `*Test`、`*ContractTest` 和 `*ArchitectureTest` 由 Surefire 在 `test` 阶段执行。
- `*IT` 由 Failsafe 在 `integration-test` 与 `verify` 阶段执行。
- 单元测试不得启动 Spring 上下文或容器。
- 接口契约测试优先使用 `@WebMvcTest`；需要完整过滤器链时使用集成测试。
- 数据库、Redis、事务、Mapper、完整鉴权和 SSE 流程使用 `@SpringBootTest` 集成测试。

## 第一步：修复测试运行基础

修改 `backend/zdmj/pom.xml`：

1. 为 Mockito 配置显式 `-javaagent`，禁止依赖运行时自附加。
2. JaCoCo 使用独立属性保存 Agent 参数；Surefire 与 Failsafe 的 `argLine` 同时包含 JaCoCo 和 Mockito Agent，避免互相覆盖。
3. 增加 Maven Failsafe Plugin，并绑定 `integration-test` 与 `verify`。
4. 增加 `testcontainers-junit-jupiter`、PostgreSQL Testcontainers、`spring-boot-testcontainers` 和 ArchUnit JUnit 5 测试依赖。
5. 搜索 `org.junit.Test`、JUnit 4 Runner 和 Rule；确认没有使用后删除直接声明的 `junit:junit:4.12`。
6. 固定测试插件版本；Testcontainers 优先使用 Spring Boot 依赖管理提供的兼容版本，ArchUnit 使用独立属性固定版本。
7. 将 JaCoCo `check` 绑定到 `verify`，分别配置关键包与普通重构包的门禁。

验收：执行现有测试时不出现 MockMaker、Byte Buddy 或 Agent 初始化错误；现有业务断言失败应逐项处理，禁止通过排除测试、添加 `@Disabled` 或放宽断言取得绿色结果。

## 第二步：建立隔离测试环境

新增 `IntegrationTestBase`：

- 使用 `pgvector/pgvector:pg15` 和 `redis:7`，与 `deploy/docker-compose.yml` 保持一致。
- 容器为测试 JVM 内共享的静态实例，集成测试串行执行，避免共享数据库并发污染。
- 通过 `@DynamicPropertySource` 注入数据源与 Redis 地址、端口、用户名和密码。
- `application-test.yml` 不提供开发地址或默认凭据；缺少容器属性时立即终止启动。
- 禁用真实邮件、对象存储和外部模型访问，使用测试配置提供确定性替身。

新增数据库初始化与清理支持：

- `pom.xml` 向测试 JVM 传入仓库根目录的绝对路径。
- 初始化器只读取根目录 `sql/pgsql.sql`，作为当前 Schema 的唯一来源，禁止复制一份测试 Schema。
- 每个集成测试开始前清空业务表并重置序列，同时执行 Redis `FLUSHDB`。
- 清理器启动时验证 JDBC URL 和 Redis 端口属于当前容器；验证失败立即终止，防止清理开发数据。
- 测试数据通过 Builder 或 Fixture 创建，不依赖固定数据库主键和预置开发数据。

新增通用支持类：

- `TestUsers`：创建用户 A、用户 B 和必要角色。
- `TestJwt`：生成 JWT 并写入容器内的 Session Store。
- `DatabaseCleaner`：清理测试 Schema。
- `ExternalAiStubConfig`：提供固定模型、Embedding 和流式输出。
- `TestObjectStorage`、`TestMailSender`：记录调用并返回确定结果，不访问供应商服务。

## 第三步：公共契约与认证

`GlobalExceptionHandlerTest` 保留业务异常与未捕获异常。新增：

- `ProblemDetailsContractTest`：业务异常、校验错误、JSON 错误、404、405、429、500 的状态码、媒体类型和字段。
- `SecurityProblemDetailsIT`：缺少令牌、令牌无效、Session 不一致和认证存储不可用。
- `AuthenticationFlowIT`：合法 JWT 恢复用户、请求结束清理上下文、连续请求与并发请求身份隔离。
- `ApiSerializationContractTest`：成功信封、分页、异步任务和空值的 JSON 契约。

断言必须覆盖 HTTP 状态、`Content-Type`、稳定字段和敏感信息缺失；禁止只验证异常类型或方法调用次数。

## 第四步：核心业务贯通与越权测试

每个 `*FlowIT` 使用真实 PostgreSQL、真实 Mapper、完整 Security Filter Chain 和 MockMvc。外部 AI、邮件与对象存储使用确定性替身。

- `ResumeFlowIT`：创建简历、保存完整内容、查询、更新和删除；验证 JSONB 与用户归属。
- `JobQueryFlowIT`：写入岗位数据，验证分页、筛选、稳定排序和详情查询。
- `KnowledgeFlowIT`：创建知识库与文档，验证归属、范围过滤和固定向量检索结果。
- `AsyncTaskLifecycleIT`：创建用户简历与岗位，提交并消费匹配任务，查询固定分析结果。
- `ConversationFlowIT`：创建会话、发送消息、接收固定 SSE、查询消息顺序和角色。

新增 `CrossUserAuthorizationIT`，对简历及子项、会话、知识库文档、匹配结果、报告、用户模型配置和上传文件逐项验证：

```text
用户 A 创建资源
→ 用户 A 查询、修改、删除成功
→ 用户 B 查询、修改、删除被拒绝
→ 数据库确认资源未被用户 B 改动
```

资源隐藏策略沿用当前契约：需要防止资源枚举的接口返回 404，其余权限错误返回既定 403。测试不得自行改变错误语义。

## 第五步：持久化与基础类型

- `JsonbPersistenceIT`：标量数组、结构化对象、空值、非法 JSON、泛型集合和 PostgreSQL `jsonb` 类型往返。
- `PaginationContractTest`：默认页码、非法页码和最大 `limit`。`PaginationQueryIT` 覆盖接口上的默认页、零页、超上限和非法页。稳定排序与跨页无重复由 `JobQueryFlowIT` 覆盖。
- `TimeSerializationContractTest`：固定 `Clock`、时区、精度、创建与更新时间以及过期边界。
- `EnumSerializationContractTest`：合法码值、非法码值、空值规则、数据库值和响应值。
- `TransactionBoundaryIT`：数据库异常回滚；远程模型调用与 SSE 期间不持有长事务。
- `LogicalReferenceIntegrityIT`：写入校验阻止非法引用，一致性检查能够发现预先构造的孤立记录。

测试日期使用固定 `Clock`，禁止使用 `Thread.sleep` 等待时间变化。并发测试使用屏障、锁存器和有界超时。

## 第六步：现有异步行为

本阶段只固定当前外部行为，不重构 Redis Stream 机制：

- `AsyncTaskLifecycleIT`：创建任务、数据库 `PENDING`、消费、`RUNNING`、`SUCCEEDED` 和结果查询。
- `AsyncTaskFailureIT`：执行器确定性失败、`FAILED`、错误摘要和用户查询。
- `AsyncTaskIdempotencyIT`：重复业务键、重复消息和已完成任务再次消费。
- `AsyncTaskAuthorizationIT`：未登录用户和其他用户查询失败，任务归属保持不变。
- `AsyncStreamContractIT`：消息字段、消费者组、ACK、非法消息和待处理消息状态。

已知的双写窗口、固定消费者名称和缺少自动恢复只记录为后续异步重构验收项，测试不得将这些缺陷声明为正确行为。

## 第七步：模块架构测试

新增 `ModuleArchitectureTest`，最终严格规则为：

- `common` 不依赖任何业务模块。
- Controller 不直接依赖 Mapper。
- 业务模块不访问其他模块的 Mapper、Entity、`service.impl` 和内部 DTO。
- Mapper 只被所属模块的应用服务或基础适配层访问。
- 模块之间不存在循环依赖。
- 业务模块只能通过明确的公开接口协作。

当前代码已有跨模块内部依赖。引入 ArchUnit 时使用 `FreezingArchRule` 记录一次现状，CI 禁止更新冻结记录；每修复一项依赖就删除对应冻结条目。`common` 与模块边界重构完成后，必须删除 `FreezingArchRule`、冻结存储和所有临时忽略项，改为严格规则后方可验收。

## 第八步：统一命令与 CI

- 本地与 CI 统一执行 `./mvnw -B -ntp clean verify`。
- CI 使用 Temurin JDK 21，禁止调用系统 `mvn`。
- Maven `verify` 依次完成 Surefire、Failsafe、JaCoCo 覆盖率检查和 ArchUnit 架构测试，不增加自定义脚本门禁。
- CI 上传 Surefire、Failsafe 和 JaCoCo 标准报告，失败原因直接从测试报告与日志定位。
- CI 同时监听 `backend/zdmj/**`、`sql/**` 和工作流自身的变化。
- `main` 的 Pull Request 必须执行该门禁；CD 的后端部署必须依赖同一提交的 CI 成功结果。

实施完成后同步更新 `docs/backend/ci-cd.md`，删除其中已经失效的限制说明。

## 执行与提交规则

按以下独立修改顺序执行，每项均保持测试可运行：

1. 修复 Mockito Agent 与 Maven 生命周期。
2. 建立 Testcontainers 测试支持和数据清理。
3. 补充公共契约与认证测试。
4. 补充五个核心业务贯通测试。
5. 补充跨用户权限测试。
6. 补充持久化、序列化和事务测试。
7. 补充当前异步行为测试。
8. 增加 ArchUnit 并完成严格规则迁移。
9. 统一 CI、覆盖率门禁和报告。

每项执行时遵循：

- 先运行相关测试并保存失败证据，再修改实现或测试设施。
- 测试名称使用 `方法或场景_条件_预期结果`，断言业务结果、持久化状态和外部契约。
- 不使用弱断言、无断言测试、仅覆盖代码行的测试或只验证 Mock 调用的贯通测试。
- 不修改与当前测试任务无关的业务行为。
- 修改生产代码以支持可测试性时，只允许依赖注入、`Clock` 注入和外部适配接口等行为不变调整，并补充回归测试。
- 每完成一项运行相关测试与完整 `clean verify`，同步文档后再进入下一项。

## 最终交付物

- 更新后的 `pom.xml` 与 CI 工作流。
- 隔离的 PostgreSQL、Redis 和外部服务测试支持。
- 公共契约、认证、业务贯通、权限、持久化、异步和架构测试。
- JaCoCo 覆盖率门禁与 ArchUnit 架构规则。
- Surefire、Failsafe、JaCoCo 和 ArchUnit 可读结果。
- 更新后的 `ci-cd.md`、异常、JWT、异步和数据专题文档。
- 一次全新检出环境中的 `./mvnw -B -ntp clean verify` 成功记录。

## 官方依据

- [Mockito Java Agent 配置](https://javadoc.io/doc/org.mockito/mockito-core/latest/org.mockito/org/mockito/Mockito.html#0.3)
- [Testcontainers JUnit 5](https://java.testcontainers.org/test_framework_integration/junit_5/)
- [Maven Failsafe 使用说明](https://maven.apache.org/surefire/maven-failsafe-plugin/usage.html)
- [ArchUnit 用户指南](https://www.archunit.org/userguide/html/000_Index.html)
