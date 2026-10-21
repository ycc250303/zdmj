# 岗位方向识别与提示词归属

## 模块职责

- `jobService.api.JobRole` 定义岗位方向，数据库 `target_role_type` 和提示词路径末段使用 hyphen slug，如 `java-backend`。
- `JobRole.fromString` 兼容 underscore、大小写及首尾空白；无法识别的历史值解析为 `UNKNOWN`，其 slug 为 `default`。
- `jobService.api.JobRoleDetectionService` 提供方向识别，返回不可变的 `JobRoleDetectionResult`。
- `JobRoleDetectionResult` 构造时要求方向非空、置信度为 `[0, 1]` 内的有限数值、识别依据非空白；简历画像直接使用该公开结果类型。
- `jobService.service.impl.JobRoleDetectionServiceImpl` 拥有关键词规则、识别提示词及模型响应校验，通过构造器注入 `ModelGateway`。
- 简历模块通过岗位模块的公开接口识别方向；模型模块处理通用请求和结构化返回类型。

## 方向识别

识别入口为 `detect(CurrentActor actor, String text)`，调用方显式传入身份。

1. 空文本返回 `UNKNOWN`，置信度为 `0.0`。
2. 关键词命中至少四项时直接返回方向，置信度为 `min(0.9, 0.45 + hit * 0.1)`。
3. 其余文本通过 `ModelGateway.generateStructured` 调用 `job-detect` 提示词。
4. 校验返回对象、方向代码及置信度；置信度必须为 `[0, 1]` 内的有限数值，缺失字段直接失败。
5. 模型响应允许规范 slug、underscore 写法及 `unknown` / `default`。非法方向或模型调用失败直接向调用方传播。

各方向的关键词数量一致。ASCII 关键词按单词边界匹配，中文关键词按文本包含关系匹配。

简历画像和岗位画像生成时识别方向，并保存规范方向及置信度。匹配等下游流程读取已有画像的方向。查询接口不发起识别。

## 提示词路径

业务模块维护自己的提示词路径，模型请求携带具体路径名称：

- 简历模块：`resume-import-parse`、`resume-analysis/{slug}`。
- 岗位模块：`job-detect`、`job-requirement/{slug}`。
- 匹配模块：`job-student-match/{slug}`。
- 知识库模块：`system`、`knowledgebase-rag-system`、`knowledgebase-query-rewrite`。
- 会话模块：`generate-conversation-title`。
- 报告模块：`career-report/generate`、`career-report/polish`、`career-report/integrity-check`。

路径常量声明在实际使用的模块内。未知方向明确使用 `{directory}/default`；已知方向的专属文件缺失直接失败。

## 文件加载

`common.prompt.PromptLoader.load(promptName)` 负责读取 UTF-8 文本及缓存结果，由 `ModelGatewayImpl` 使用。

- 名称遵循 `[a-z0-9-]+(?:/[a-z0-9-]+)*`，不包含 `.md` 后缀。
- 实际资源限定为 `classpath:prompts/{promptName}.md`。
- 空名称、绝对路径、目录穿越、外部 URL 和非法字符直接失败。
- 文件缺失抛出 `IllegalArgumentException`，读取失败抛出 `IllegalStateException`。
- 文件输入流读取后关闭，缓存仅保存提示词正文。

## 验证

- `JobRoleTest`：方向代码兼容规则及关键词约束。
- `JobRoleDetectionServiceImplTest`：规则阈值、ASCII 边界、模型输出校验和异常传播。
- `PromptLoaderTest`：路径限制、文件读取、缓存和失败行为。
- `PromptResourcesTest`：支持方向的模板及固定提示词完整性。
- `ProfilePromptFlowIT`：画像接口入队、消费、方向识别、提示词读取、数据库保存及结果查询。
- `ModuleArchitectureTest`：加载器不依赖业务，模型模块不依赖岗位类型，识别实现仅在岗位模块内部使用。

完整验收命令为 `./mvnw -B -ntp clean verify`。
