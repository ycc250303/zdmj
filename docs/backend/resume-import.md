# 简历 PDF/文本结构化识别

## 概述

提供 `POST /api/zdmj/resumes/import/parse`：从 COS PDF URL 或纯文本提取内容，经 **deepseek-v4-flash** 结构化解析为与简历相关表字段对齐的 JSON。**不写库**，由前端逐条调用既有 CRUD 落库。

## 流程

1. 前端 `POST /files/upload?prefix=resume` 上传 PDF，取得 `url`
2. 调用 `POST /resumes/import/parse`，body `{ "pdfUrl": "..." }` 或 `{ "rawText": "..." }`
3. 后端：Tika 抽文本 → 截断 15000 字符 → LLM 结构化 → 日期/degree 归一化
4. 识别成功后前端直接调用 `PUT /resumes/me/content` **全量覆盖**当前简历（旧经历不在请求中则删除）

## 模型与配置

- 优先使用 `ModelEnum.DEEPSEEK_FLASH`（**忽略**用户 LLM 自配）；未配置 `DEEPSEEK_API_KEY` 时回退平台默认模型（`AL_MODEL` / DashScope）
- API Key：`DEEPSEEK_API_KEY`（DeepSeek 场景）或 `SPRING_AI_OPENAI_API_KEY` / `DASHSCOPE_API_KEY`（回退场景）
- Docker Compose 须映射 `DEEPSEEK_API_KEY: ${DEEPSEEK_API_KEY}`
- 提示词：`classpath:prompts/resume-import-parse.md`

## 限流

- USER 维度：10 次/分钟（`ResumeController` 上 `@RateLimit`）

## 响应字段

| 区块 | 对应表 | 说明 |
|------|--------|------|
| `personalInfo` | `users` | name, phone, email, homepageUrl；major 仅展示 |
| `educations[]` | `educations` | school, major, degree(1–6), startDate, endDate, gpa |
| `careers[]` | `careers` | company, position, dates, details |
| `projects[]` | `project_experiences` | name, role, dates, description, contribution, techStack, highlights, url |
| `awards[]` | `awards` | awardType(1–3), name, awardDate, description |
| `skill` | `skills` | content[{type, content[]}] |
| `warnings[]` | — | 截断、解析修正等 |

日期输出 `yyyy-MM-dd`（前端按月展示）。**仅有年份无月份 → null**；有年月无日 → 该月 1 号；「至今」→ `endDate` 为 null。

## 错误码

| code | 说明 |
|------|------|
| 3013 | 简历识别失败（LLM/JSON 解析） |
| 3014 | 提取文本为空 |
| 1001 | 未提供 pdfUrl/rawText 或 PDF 解析失败 |

## 测试

```bash
cd backend/zdmj
mvn test -Dtest=ResumeServiceImplTest
```

## 已知限制

- 扫描版 PDF 文本质量差，建议上传文字版 PDF
- 不识别 `project_experiences.status`、`lookup_result`
- 识别成功后自动覆盖写库，无需用户确认
