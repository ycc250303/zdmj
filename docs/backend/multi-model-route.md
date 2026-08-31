# 多模型路由（UserLlmRouter）

业务代码**禁止**注入全局 `ChatClient` / `ChatModel`。Chat 一律经 `ChatUtil` → `UserLlmRouter`：按当前用户解析厂商、模型与 API Key，再懒创建并缓存 `ChatClient`。Embedding **不走**该路由，仍用 Spring AI 自动装配的 `EmbeddingModel`。

---

## 为何收口

直连 `spring.ai.openai.*` 只能绑一套平台 Key。本产品需要：

| 需求 | 做法 |
| --- | --- |
| 用户自配 DashScope / DeepSeek Key | `user_llm_config` 一行一用户 |
| 未配置时仍能用平台默认模型 | YAML `spring.ai.openai.*` 兜底 |
| 简历识别固定用 DeepSeek | `getPlatformChatClient`，忽略用户自配 |
| 结构化输出不受会话记忆污染 | 同一用户 plain / memory 两份 Client |
| 改配置立即生效、无需重启 | `evict(userId)` 清缓存 |

---

## 模块结构

```
com.zdmj.common.ai
  UserLlmRouter          # 路由、缓存、探测、加解密委托
  ChatUtil               # 业务门面（once / structured / stream / conversation）
  ModelEnum              # 可选模型目录（code → baseUrl + apiModelName）
  PromptUtil / PromptNames
  LlmInputLimits / LlmRateLimits
  config/ChatModelConfig # 仅 ChatMemory（JDBC）；ChatClient 不再注册 Bean
  config/EmbeddingConfig # Token 切分 + embedding 线程池
  config/RagConfig       # RAG 检索阈值（与 Chat 路由无关）

com.zdmj.common.util.UserApiKeyCipher   # Encryptors.text 加解密 + 掩码
com.zdmj.userAuthService                # /users/llm-config CRUD + 连通性测试
```

```
业务 Service
  └─ ChatUtil
       ├─ chatOnce(userId, …) / chatStructuredOnce(userId, …)
       │     → getChatClient(userId)            # plain，无记忆
       ├─ chatStreamInConversation(userId, conversationId, …)
       │     → getChatClientWithMemory(userId)  # JDBC MessageWindow
       └─ chatStructuredOnceWithPlatformModel
             → getPlatformChatClient(model)     # 忽略用户自配；不传 userId
```

用户模型路径由调用方传入 `userId`（通常来自 `UserHolder.requireUserId()`）；`null` 抛 `USER_NOT_LOGIN`，不再静默走平台兜底。异步消费者把任务记录里的 `user_id` 原样传入即可，不必伪造 ThreadLocal（见 [`llm-async-stream.md`](llm-async-stream.md)）。

---

## 路由决策

`UserLlmRouter.resolveProvider(userId)`：

```
查 user_llm_config
  ├─ 有记录 → ModelEnum.fromCode(modelCode) + 解密 api_key_ciphertext
  └─ 无记录
        ├─ require-user-config 或关闭 platform-fallback → 2010
        ├─ 平台 API Key 为空 → 2010
        └─ 否则 → spring.ai.openai.{base-url, api-key, chat.options.model}
```

平台指定模型（简历识别）走 `resolvePlatformProvider`：DeepSeek 用 `DEEPSEEK_API_KEY`，其余用 `SPRING_AI_OPENAI_API_KEY`。`resolveResumeImportModel()`：有 DeepSeek Key → `DEEPSEEK_FLASH`，否则回退平台默认模型（不在目录则 `QWEN_PLUS`）。

### 模型目录（`ModelEnum`）

| code | 展示名 | baseUrl | API 模型名 |
| --- | --- | --- | --- |
| `qwen3.6-plus` | 通义千问 3.6 Plus | DashScope compatible-mode | `qwen3.6-plus` |
| `qwen3.7-max` | 通义千问 3.7 Max | 同上 | `qwen3.7-max` |
| `deepseek-v4-flash` | DeepSeek V4 Flash | `https://api.deepseek.com` | `deepseek-v4-flash` |
| `deepseek-v4-pro` | DeepSeek V4 Pro | 同上 | `deepseek-v4-pro` |

用户只能选目录内 code；非法 code → `USER_LLM_CONFIG_INVALID`（2011）。扩容：改枚举即可，无需动态 Provider 表。

---

## 两类用户 ChatClient + 平台 Client

| 出口 | 缓存键 | Advisor | 场景 |
| --- | --- | --- | --- |
| `getChatClient` | `{userId}:plain` | 无 | 画像、匹配、图谱、报告、标题、查询改写 |
| `getChatClientWithMemory` | `{userId}:memory` | `MessageChatMemoryAdvisor` | 多轮对话 / RAG 答疑 |
| `getPlatformChatClient` | `platform:{code}` | 无 | 简历 PDF/文本结构化识别 |

记忆：PostgreSQL `SPRING_AI_CHAT_MEMORY`，窗口 40 条；对话时 `CONVERSATION_ID` = 会话主键字符串。删除会话时 `ChatMemory.clear(id)`，并物理删除该会话 `messages`。结构化调用必须用 plain，避免历史串进 JSON。

`ChatModelConfig` 里原先的全局 `@Bean ChatClient` 已注释掉，避免业务误注入平台单例。

---

## 缓存与热更新

两张 `ConcurrentHashMap`：`clientCache`（按用户）、`platformClientCache`（按平台模型）。`computeIfAbsent` 懒创建。

保存/删除用户配置后必须 `userLlmRouter.evict(userId)`，同时摘掉 plain 与 memory。已拿到的旧 `ChatClient` 引用仍可用（软切换）；新请求走新实例。平台 Client 目前无主动失效（Key 变更需重启）。

超时：业务连接 10s / 读 300s；连通性探测 5s / 30s 且 `maxTokens=1`。

非流式默认关思考链，缩短首 token：`qwen3*` → `enable_thinking=false`；`deepseek-v4*` → `thinking.type=disabled`。

---

## baseUrl 与 `/v1`

Spring AI `OpenAiApi` 默认会补 `/v1/chat/completions`。当前目录里 DashScope（`.../compatible-mode`）与 DeepSeek（`https://api.deepseek.com`）的 baseUrl **都不含**版本号，交给默认即可。若 baseUrl 已以 `/v1`、`/v4` 等结尾，正则 `/v\d+[a-zA-Z0-9]*$` 命中后会显式 `completionsPath("/chat/completions")`，避免拼出 `/v1/v1`。

---

## API Key 加密

落库字段 `user_llm_config.api_key_ciphertext`。实现是 Spring `Encryptors.text`（`UserApiKeyCipher`），**不是**自定义 AES-GCM。

| 项 | 约定 |
| --- | --- |
| 环境变量 | `APP_AI_USER_KEY_ENCRYPTION_KEY`（偶数位 hex） |
| 开发未配 | 内置兜底 password，应用可启动 |
| 生产 | `app.ai.user-llm.require-encryption-key=true`，缺密钥启动失败 |
| 对外 | DTO 只回 `apiKeyMasked`（前 3 + `****` + 后 4） |
| 换密钥 | 须先用旧密钥解密再重加密；无自动轮换 |

---

## Embedding 不路由

知识库入库与 RAG 查询向量使用自动装配的 `EmbeddingModel`：

```yaml
spring.ai.openai.embedding.options:
  model: text-embedding-v4
  dimensions: 1024
```

与用户 Chat 模型解耦，避免把 chat 模型误当 embedding。切分与异步线程池见 `EmbeddingConfig`；查询侧失败返回 `null`（`EmbeddingQuerySupport`）。

---

## 配置项

| Key | 默认 | 含义 |
| --- | --- | --- |
| `app.ai.user-llm.require-user-config` | `false` | true 则强制自配，不走平台 |
| `app.ai.user-llm.platform-fallback-enabled` | `true` | 无用户配置时允许平台 Key |
| `app.ai.user-llm.require-encryption-key` | `false` | 生产建议 true |
| `app.ai.user-llm.encryption-key` | 空 | 见上 |
| `app.ai.user-llm.advisors.*` | `false` | **预留，代码未读取** |
| `app.ai.deepseek.api-key` | 空 | 平台 DeepSeek |
| `spring.ai.openai.*` | DashScope + `AL_MODEL` | 平台 Chat / Embedding |

---

## 用户配置 API

前缀 `/api/zdmj`。限流见 [`rate-limit.md`](rate-limit.md)。

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/users/llm-config` | 当前配置；未配时 `configured=false`，`usingPlatformDefault` 反映兜底开关 |
| GET | `/users/llm-config/models` | 目录列表 |
| PUT | `/users/llm-config` | 保存 modelCode + 明文 apiKey；写库后 `evict` |
| DELETE | `/users/llm-config` | 删除后回落平台（若允许） |
| POST | `/users/llm-config/test` | 用请求体探测，不读库、不写缓存 |

### 错误码

| code | 枚举 | HTTP |
| --- | --- | --- |
| 2010 | `USER_LLM_NOT_CONFIGURED` | 428 |
| 2011 | `USER_LLM_CONFIG_INVALID` | 400 |
| 2012 / 2013 | 加解密失败 | 400 |
| 2014 | `USER_LLM_CONNECTION_TEST_FAILED` | 400 |

---

## 业务怎么调

| 场景 | 方法 |
| --- | --- |
| 单次文本 | `chatOnce(userId, …)` |
| 结构化 JSON | `chatStructuredOnce(userId, …)`（user 侧附 Schema；失败抛错） |
| 简历识别 | `chatStructuredOnceWithPlatformModel`（平台模型，不传 userId） |
| SSE 多轮 | `chatStreamInConversation(userId, conversationId, …)`（`POST /messages/chat`） |
| 提示词 | `classpath:prompts/{PromptNames}.md`；占位符只替换 `${key}` / `{key}`，避免 JSON 花括号被模板引擎吃掉 |

长耗时接口加 `@RateLimit(USER)`，阈值 `LlmRateLimits`。异步入队见 [`llm-async-stream.md`](llm-async-stream.md)（未落地）。

---

## 已知限制

- 无动态 Provider 表、无运行时改 YAML；扩模型改 `ModelEnum`。
- Embedding / RAG 不跟用户 Chat Key。
- 平台 Client 缓存不随 `.env` 热更新。
- `advisors.enabled` 未生效；无 Tool Calling。
- `UserLlmRouter` / `ChatUtil` 尚无独立单测（业务侧 mock `ChatUtil`）。
