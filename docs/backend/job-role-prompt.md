# 岗位方向识别与提示词路由

`JobRole` 是岗位方向的单一事实源：落库 `targetRoleType`、提示词路径末段都用 hyphen slug（如 `java-backend`）。`fromString` 兼容 underscore（如 `java_backend`）；短码如 `qa`/`fe` 视为无法识别。

## 提示词路由

```
PromptUtil.resolve(PromptScenario, JobRole)
  → classpath:prompts/{directory}/{slug}.md
  → 缺文件则 {directory}/default.md
```

场景目录：`resume-analysis` / `job-requirement` / `job-career-graph` / `job-student-match`。与岗位无关的提示词仍走 `PromptNames`。

## 谁负责识别

`JobRoleDetector.detect` **只在实体首次生成时调用一次**，置信度用 `DetectResult.confidence` 落库，不再另算 `estimateRoleConfidence`。

| 入口 | 识别来源 |
| :--- | :--- |
| 简历画像生成 | `detect(简历文本)` |
| 岗位画像生成 | `detect(岗位上下文)` |
| 岗位图谱 / 人岗匹配 / 生涯报告 | `JobRole.fromString(jobProfile.targetRoleType)`；画像缺失则先生成画像 |

GET 查询接口不识别。当前用户对该岗尚无画像时，匹配等下游会先生成（一用户一岗一身份）。

## 识别步骤（Detector 内部）

1. 关键词命中 ≥4：直接出角色，置信度 `min(0.9, 0.45 + hit*0.1)`。各方向词表等长，覆盖语言 / 框架 / 技术栈 / 工具；同类替代可并列（pytest 与 junit），不含缩写与中英重复。ASCII 词按单词边界匹配。
2. 否则 LLM `job-detect.md`（slug 全集）
3. LLM 失败或 unknown：弱关键词 0.45 / 0.35，全空 0.2

不要把分类与画像/图谱生成合成一次 LLM 调用（结构化 JSON 路径必须走 plain ChatClient）。
