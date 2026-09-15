# 职点迷津（zdmj）

面向计算机类校招场景的求职学习规划平台：简历画像、岗位要求画像、可解释人岗匹配、知识库 RAG 与职业生涯发展报告。

[![Java](https://img.shields.io/badge/Java-21-orange?logo=openjdk)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5-green?logo=springboot)](https://spring.io/projects/spring-boot)
[![Vue](https://img.shields.io/badge/Vue-3.5-42b883?logo=vuedotjs)](https://vuejs.org/)
[![TypeScript](https://img.shields.io/badge/TypeScript-5.9-blue?logo=typescript)](https://www.typescriptlang.org/)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-pgvector-336791?logo=postgresql)](https://www.postgresql.org/)

---

## 项目介绍

职点迷津面向计算机类专业学生的校招准备阶段，把「了解岗位 → 看清自己 → 量化匹配 → 落地规划」串成一条主线。系统用大语言模型生成岗位能力画像与学生就业能力画像，按基础要求、职业技能、职业素养、发展潜力四个维度做可解释的人岗匹配，再聚合画像与匹配结果生成可编辑的职业生涯发展报告。知识库与智能对话用于学习答疑和材料梳理，与面试模拟等后续环节区分。

学生可以：

- 浏览校招岗位，把冗长 JD 归纳为结构化能力要求
- 上传或编辑简历，得到七维能力画像、评分与改进建议
- 对目标岗位发起匹配，看到分项得分、证据、亮点与技能缺口
- 获得含行动计划的生涯报告，并按需润色、检查与手工修改
- 上传课程笔记、项目材料等到个人知识库，基于材料提问
- 在独立对话中按需引用系统知识库与个人文档，流式获得回答

---

## 系统架构

前后端分离：浏览器使用 Vue 管理端，业务由 Spring Boot 统一编排；关系数据与向量检索落在 PostgreSQL（pgvector），登录态、限流与长耗时 LLM 任务走 Redis，简历与知识文档存放腾讯云 COS，对话与结构化分析经 Spring AI 接入通义 / DeepSeek。

```text
┌─────────────────────────────────────────────────────────┐
│  Vue 3 前端（Soybean Admin / Naive UI）                  │
│  岗位 · 简历 · 画像 · 匹配 · 报告 · 知识库 · 对话         │
└──────────────────────────┬──────────────────────────────┘
                           │ HTTP / SSE
┌──────────────────────────▼──────────────────────────────┐
│  Spring Boot 3.5（按业务域分包）                          │
│  userAuth / resume / job / match / careerReport          │
│  knowledge / conversation + common（鉴权、LLM、RAG、异步） │
└───────┬────────────┬────────────┬────────────┬──────────┘
        │            │            │            │
   PostgreSQL    Redis 7     腾讯云 COS     LLM API
   + pgvector    会话/限流    简历/文档      DashScope
                 Stream                    / DeepSeek
```

长耗时分析（画像、匹配、报告、简历识别、知识库向量化）入队 Redis Stream，前端轮询任务状态；多轮对话保持 SSE 直出。

---

## 技术栈

### 后端

| 技术 | 版本 | 说明 |
| --- | --- | --- |
| Spring Boot | 3.5 | 应用框架 |
| Java | 21 | 开发语言 |
| Spring AI | 1.1 | OpenAI 兼容接入（DashScope / DeepSeek） |
| MyBatis-Plus | 3.5 | ORM |
| Spring Security + JWT | — | 认证授权，登录态缓存在 Redis |
| PostgreSQL + pgvector | 15 | 业务库 + 向量检索 |
| Redis | 7 | 会话缓存、接口限流、Stream 异步任务 |
| Apache Tika | 3.3 | 简历 / 知识文档解析 |
| MapStruct | 1.5 | 对象映射 |
| 腾讯云 COS | — | 对象存储 |
| Maven | 3.9+ | 构建工具 |

数据层用 PostgreSQL + pgvector，关系数据与向量检索共用一套库，避免再引入独立向量组件。Redis 承担登录态、限流计数，以及画像 / 匹配 / 报告等长耗时 LLM 任务的 Stream 队列；对话仍走 SSE，不入队。

### 前端

| 技术 | 版本 | 说明 |
| --- | --- | --- |
| Vue | 3.5 | UI 框架（Soybean Admin） |
| TypeScript | 5.9 | 开发语言 |
| Vite | 7 | 构建工具 |
| Naive UI | 2.43 | 组件库 |
| UnoCSS | 66 | 原子化样式 |
| Pinia | 3 | 状态管理 |
| Vue Router | 4 | 路由 |
| ECharts | 6 | 画像 / 匹配可视化 |
| pnpm | 10+ | 包管理器（Node ≥ 20.19） |

---

## 功能特性

### 简历与学生能力画像

- **一份简历**：每名用户维护一份结构化档案（教育、实习/工作、项目、技能、奖项），整页编辑后一次全量保存。
- **PDF / 文本导入**：上传 PDF 或粘贴文本，经文档解析与模型结构化识别后写入档案。
- **七维画像**：从专业技能、荣誉与证书、创新 / 学习 / 抗压 / 沟通 / 实习实践等维度生成就业能力画像，附能力评分与改进建议。
- **方向识别**：按岗位关键词与模型推断求职方向（Java 后端、前端、C++、测试、AI Agent、算法、数据分析、大数据、DevOps/SRE、网络安全等），后续分析走对应提示词。
- **简历导出**：在线预览后导出可投递的 PDF。

### 岗位与人岗匹配

- **岗位库**：浏览、筛选校招岗位（行业、公司、薪资、关键词等），查看完整招聘信息。
- **岗位能力画像**：将 JD 归纳为与学生画像对齐的能力要求，支持按岗位方向选用专用提示词。
- **四维匹配**：在已有双方画像的前提下，从基础要求、职业技能、职业素养、发展潜力对比打分，给出综合匹配度、证据、亮点、差距与关键词覆盖情况。
- **历史结果**：同一岗位保留最近一次匹配；画像更新后可重新分析。

### 职业生涯发展报告

- **结构化生成**：针对目标岗位聚合画像与匹配结果，输出职业探索、目标、发展路径、短中期行动计划与评估建议。
- **学习路径 RAG**：生成时检索个人知识库与学习路线专库，把可执行的学习建议写入相关章节。
- **润色 / 检查 / 编辑**：智能润色生成新版本，完整性检查给出缺项与建议，支持手工修改并保留最新版。

### 知识库与智能对话

- **个人知识库**：每用户一份准备阶段资料库，支持 PDF / Markdown 上传、异步分块与向量化；学习答疑只检索本人材料。
- **系统知识库**：平台维护通用资料库与学习路线专库（只读），供生涯报告与对话按需检索。
- **RAG**：pgvector 相似度检索，配合查询改写与阈值截断；无有效命中时说明并退回一般作答，不编造材料中不存在的经历。
- **流式对话**：多会话管理、SSE 打字机输出；创建会话时注入当前简历摘要；可按会话开关系统知识库与个人文档。

### 账号与模型

- **注册登录**：邮箱验证码、JWT 鉴权，用户数据按账号隔离。
- **多模型配置**：用户可自选通义千问 3.8 Flash / Max、DeepSeek V4 Flash / Pro，并加密保存 API Key；未配置时回退平台默认模型。简历结构化识别固定走平台 DeepSeek Flash（无 Key 时回退千问 Flash）。
- **限流**：长耗时生成接口按用户维度限流，超限返回 429。

---

## 项目结构

```text
zdmj/
├── backend/zdmj/                         # Spring Boot 后端（Maven）
│   └── src/main/java/com/zdmj/
│       ├── userAuthService/              # 用户鉴权、LLM 配置
│       ├── resumeService/                # 简历、学生能力画像
│       ├── jobService/                   # 岗位、岗位能力画像
│       ├── matchService/                 # 人岗匹配
│       ├── careerReportService/          # 职业发展报告
│       ├── knowledgeService/             # 知识库与向量化
│       ├── conversationService/          # 会话与 SSE 对话
│       └── common/                       # LLM 路由、RAG、鉴权、异步任务、存储
├── client/                               # Vue 3 前端
├── deploy/                               # Docker Compose、Nginx、部署脚本
├── sql/                                  # 建表脚本与字段字典
├── docs/backend/                         # 后端设计文档
├── .env.example                          # 环境变量模板
└── README.md
```

---

## 快速开始

环境要求：JDK 21、Maven 3.9+、Node.js 20+、pnpm 10+、Docker（推荐，用于 PostgreSQL / Redis）。大模型至少配置 DashScope 或 DeepSeek 之一。

### 1. 克隆并配置

```bash
git clone https://github.com/ycc250303/zdmj.git
cd zdmj
cp .env.example .env
# 编辑 .env：数据库、Redis、DashScope / DeepSeek、COS、邮件、JWT 等
```

根目录 `.env` 由 `application.yml` 的 `spring.config.import` 自动加载，勿提交真实密钥。

### 2. 启动依赖

```bash
cd deploy
docker compose --env-file ../.env up -d postgres redis
```

本地直连容器中的数据库时，`.env` 里 `APP_REMOTE_HOST` 一般为 `127.0.0.1`。

### 3. 启动后端

```bash
cd backend/zdmj
mvn -B -ntp clean spring-boot:run
```

- 接口：`http://localhost:8080`
- Swagger：`http://localhost:8080/swagger-ui/index.html`

### 4. 启动前端

```bash
cd client
pnpm install
pnpm dev
```

前端默认：`http://localhost:5173`。

---

## Docker 一键部署

Compose 会启动 PostgreSQL（pgvector）、Redis、Spring Boot 后端和 Nginx 前端。

```bash
cp .env.example .env   # 填入 DASHSCOPE_API_KEY / DEEPSEEK_API_KEY、COS、JWT 等
cd deploy
docker compose --env-file ../.env up -d --build
```

| 服务 | 地址 |
| --- | --- |
| 前端 | http://localhost |
| 后端 API | http://localhost:8080 |
| Swagger | http://localhost:8080/swagger-ui/index.html |
| PostgreSQL | localhost:5432 |
| Redis | localhost:6379 |

---

## 许可证

[AGPL-3.0](LICENSE)
