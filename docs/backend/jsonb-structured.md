# JSONB 结构化字段（第 2/3 类）

对象或对象数组在 Entity 上是 **JSON 原文 `String`**，由 [`JsonbStringTypeHandler`](../../backend/zdmj/src/main/java/com/zdmj/common/typehandler/JsonbStringTypeHandler.java) 完成 JSONB ↔ 文本。结构解析只在本域 Service 用 Jackson 转到 DTO。

同质标量数组（`["Java"]` / `[1,2]`）见 [`jsonb-scalar-array.md`](jsonb-scalar-array.md)，用官方 `JacksonTypeHandler`，不要改成本方案。

---

## 数据流

```
写入：DTO ──Service writeValueAsString──► Entity String ──JsonbStringTypeHandler.setString──► JSONB
读出：JSONB ──getObject / PGobject──► Entity String ──Service readValue──► DTO
```

Handler **不认识** `SkillItemDTO`、`ScoreDetail`。手写 XML 查询 Entity 必须 `autoResultMap` 生成的 `resultMap="mybatis-plus_Xxx"`，或像 [`SkillMapper.xml`](../../backend/zdmj/src/main/resources/mapper/resumeService/SkillMapper.xml) 那样显式指定 `JsonbStringTypeHandler`。只写 `resultType=Entity` 时 `PGobject` 填不进 `String`。

读失败策略由 Service 决定（技能：warn + 空列表；匹配 weights：warn + null）。不要把领域 DTO 写进 TypeHandler。

---

## 约定

```java
@TableName(value = "skills", autoResultMap = true)
public class Skill {
    @TableField(typeHandler = JsonbStringTypeHandler.class)
    private String content; // [{"type":"开发语言","content":["Java"]}]
}
```

- 第 2 类：根是对象数组 → Service `TypeReference<List<Foo>>`。
- 第 3 类：根是对象（可嵌套）→ Service `Foo.class` 或嵌套 DTO 树。
- 开放键值袋（知识库 `metadata`、会话 `context`）本轮仍 `JacksonTypeHandler` + `Map`，不强制改成 String。会话 `config` 首条消息发出后不可改，写入时只保留 `useSystemKnowledge` / `ragDocumentIds`。

---

## 覆盖列

| 表.列 | 形状 | Service / API |
| --- | --- | --- |
| `skills.content` | 对象数组 | `List<SkillItemDTO>` |
| `student_capability_profiles.score_detail` | 对象 | `ScoreDetail` |
| `student_capability_profiles.suggestions` | 对象数组 | `List<Suggestion>` |
| `job_student_matches.weights` / `dimension_detail` | 对象 | 匹配 DTO |
| `job_career_graphs.current_node` / 路径 | 对象 / 嵌套数组 | 图谱 DTO |
| `career_development_reports.*` 快照与正文 | 对象 / 数组 | 报告 DTO / `Map` |
| `project_experiences.highlights` | 字符串数组原文 | API 仍为 `String`（[`ProjectHighlightsSupport`](../../backend/zdmj/src/main/java/com/zdmj/resumeService/support/ProjectHighlightsSupport.java) 归一化） |
| `project_experiences.lookup_result` | 对象原文 | API 仍为 `String`，不在 Service 拆 DTO |

`knowledge_sources` 属对象数组，Entity 已是 String + 本 handler。
