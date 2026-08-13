# zdmj（职点迷津）

`zdmj` 是一个面向求职场景的 AI 辅助平台，围绕简历分析、岗位匹配、知识问答和会话管理，提供从「准备简历」到「求职决策」的完整支持流程。

---

## 项目介绍

项目以 Spring Boot 后端为核心，结合前端管理端与容器化部署能力，目标是构建一个可扩展、可复现、可二次开发的求职服务平台。

核心业务域包括：

- 用户与鉴权：账号体系、登录鉴权、权限控制
- 简历服务：简历上传、文本解析、结构化分析
- 岗位服务：职位信息处理、匹配分析
- 知识服务：知识库问答与内容检索
- 会话服务：对话上下文与历史管理

---

## 技术栈

### 后端

- Java 21
- Spring Boot 3.5
- Spring AI（`spring-ai-starter-model-openai`，DashScope 兼容模式接入通义）
- MyBatis-Plus
- Spring Security + JWT
- Maven

### 前端

- Vue 3
- TypeScript
- Vite
- Pinia / Vue Router

### 数据与中间件

- PostgreSQL 15（`pgvector` 镜像）
- Redis 7
- 腾讯云 COS（对象存储）

### 工程化与部署

- Docker / Docker Compose
- GitHub Actions（CI）

---

## 功能特性

### 用户与鉴权

- 基于 Spring Security + JWT 的认证授权机制，支持登录态校验与接口级权限控制
- 支持用户基础信息管理，便于后续按用户维度隔离简历、会话、知识数据
- 提供统一异常返回与鉴权失败处理，降低前后端联调复杂度

### 简历服务

- 支持简历文件上传与内容提取，可对接多种文本解析策略
- 将非结构化简历信息转为结构化数据，便于画像分析与岗位匹配
- 支持简历分析结果持久化，方便后续复用与多轮优化

### 岗位与匹配服务

- 支持岗位信息录入与标准化处理（职责、技能、经验要求等）
- 结合岗位需求与简历画像进行匹配分析，输出可解释的匹配建议
- 支持为求职场景提供差距提示（能力缺口、关键词覆盖不足、方向建议）

### 知识库与智能问答

- 支持知识内容管理与检索，构建可扩展的求职知识库
- 结合大模型能力进行问答生成，提升回答的可读性与场景适配度
- 支持检索增强式问答流程，减少泛化回答，增强内容相关性

### 会话与上下文管理

- 支持多轮对话会话管理，保留上下文信息，提升连续问答体验
- 支持会话历史查询与持久化，便于回溯用户行为与分析过程
- 支持按业务场景管理会话数据（如简历分析、岗位咨询、知识问答）

### 工程化与可复现能力

- 提供 Docker Compose 一键启动 PostgreSQL、Redis、后端服务
- 支持本地开发模式（后端 + 前端分离启动），便于调试与模块化迭代
- 提供 CI 流水线与测试支持，提升交付稳定性与团队协作效率

---

## 开发记录（TODO）

- [x] 完成项目基础架构搭建（后端骨架、数据库、CI/CD 基础流程）
- [x] 完成用户注册/登录与鉴权能力（含 CORS、JWT、登录态缓存）
- [x] 完成岗位数据导入脚本与岗位 CRUD 接口
- [x] 完成知识库基础管理（上传、删除、向量化、检索问答）
- [x] 完成基础 LLM 对话能力（含流式返回、消息编辑重发、会话缓存）
- [x] 完成学生就业能力画像生成（支持上传文件与文本输入）
- [x] 完成岗位要求画像分析能力，并持续优化画像字段与返回结构
- [x] 完成岗位关联图谱能力（垂直晋升路径 + 换岗路径）
- [x] 完成人岗匹配相关能力建设（岗位画像与学生画像查询/关联支撑）
- [x] 完成简历分析结果导出 PDF 能力
- [x] 完成前端核心页面打通（登录、岗位、用户画像、知识库、聊天）
- [x] 完成异常处理、业务码体系与接口稳定性重构
- [x] 完成单元测试补充与覆盖率门禁（岗位模块、登录模块、CI 覆盖率检查）
- [x] 完成从 Python 侧向 Java 侧向量化框架迁移（下线 Python 运行链路）
- [ ] 完善职业生涯发展报告全流程（目标设定、路径规划、阶段性行动计划一体化输出）
- [ ] 增强报告编辑优化能力（智能润色、完整性检查、可视化对比）
- [ ] 补齐赛题指标量化验证（关键技能匹配准确率/画像关键信息准确率评测）
- [ ] 优化岗位图谱的前端可视化与交互解释能力（路径可解释性、切换视图）

---

## 项目结构

```text
zdmj/
├── backend/
│   └── zdmj/                           # Spring Boot 后端工程（Maven）
│       ├── src/main/java/com/zdmj/
│       │   ├── userAuthService/        # 用户鉴权
│       │   ├── resumeService/          # 简历服务
│       │   ├── jobService/             # 岗位服务
│       │   ├── knowledgeService/       # 知识服务
│       │   ├── conversationService/    # 会话服务
│       │   ├── matchService/           # 匹配能力
│       │   └── common/                 # 通用组件
│       └── src/main/resources/
│           └── application-example.yml # 配置示例
├── client/                             # 前端工程（Vue + Vite）
├── deploy/
│   ├── docker-compose.yml              # 容器编排（PostgreSQL/Redis/backend）
│   ├── nginx.conf                      # Nginx 配置
│   └── deploy.sh                       # 部署脚本
├── sql/                                # 数据库脚本
├── docs/                               # 项目文档
├── .cursor/skills/                     # Cursor Agent Skills
└── README.md
```

---

## 快速开始（复现）

提供两种方式：`Docker 一键复现（推荐）` 与 `本地开发复现`。

### 方式一：Docker 一键复现（推荐）

#### 1. 环境准备

- Docker
- Docker Compose
- DashScope API Key

#### 2. 配置环境变量

在项目根目录从模板创建 `.env` 并填入真实值（`.env` 已加入 `.gitignore`）：

```bash
cp .env.example .env
# 编辑 .env 填入数据库、Redis、DashScope、COS、邮件、JWT 等密钥
```

#### 3. 启动服务

```bash
cd deploy
docker compose --env-file ../.env up -d --build
```

#### 4. 访问服务

- 后端接口：`http://localhost:8080`
- Swagger：`http://localhost:8080/swagger-ui/index.html`
- PostgreSQL：`localhost:5432`
- Redis：`localhost:6379`

---

### 方式二：本地开发复现

#### 1. 环境准备

- JDK 21
- Maven 3.9+
- Node.js 20+（前端）
- pnpm 10+（前端）
- PostgreSQL 15+
- Redis 7

#### 2. 启动依赖服务（可选）

可只用 Docker 启动数据库与缓存：

```bash
cd deploy
docker compose --env-file ../.env up -d postgres redis
```

#### 3. 配置环境变量并启动后端

```bash
cp .env.example .env   # 若尚未创建（在项目根目录）
# 编辑 .env：本地直连 Docker 中的 postgres/redis 时，APP_REMOTE_HOST 一般为 127.0.0.1

cd backend/zdmj
mvn -B -ntp clean spring-boot:run
```

`application.yml` 会通过 `spring.config.import` 自动加载项目根目录 `.env`；也可在 IDE 中指定同一文件。

按本地环境修改 `application.yml` 或环境变量（数据库、Redis、DashScope、COS）。

#### 4. 启动前端

```bash
cd client
pnpm install
pnpm dev
```

前端默认访问地址：`http://localhost:5173`

#### 5. 运行测试（可选）

```bash
cd backend/zdmj
mvn -B -ntp test
```

---

如果你计划二次开发，建议优先使用「方式二」，便于本地调试和分模块迭代。