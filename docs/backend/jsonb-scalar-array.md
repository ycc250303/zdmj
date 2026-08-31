# JSONB 标量数组（第 1 类）

同质标量数组（`number[]` / `string[]`）在 Entity 上就是 `List<Long>` 或 `List<String>`，由 MyBatis-Plus **3.5.6+** 的 `JacksonTypeHandler` 完成 JSONB ↔ List。Service **不再**对这些列做 `readValue` / `writeValueAsString`。

结构化对象 / 对象数组（技能 content、画像 score_detail、匹配 weights 等）仍走 `String` + `JsonbStringTypeHandler`，见各域 Service。本篇只管第 1 类。

---

## 约定

```java
@TableName(value = "jobs", autoResultMap = true)
public class Job {
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> keywords;
}

@TableName(value = "resumes", autoResultMap = true)
public class Resume {
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<Long> projects;
}
```

- 必须 `autoResultMap = true`，MP 才会用 `(Class, Field)` 构造器保留 `List<Long>` 泛型。裸 `JacksonTypeHandler(List.class)` 会把 `[1,2]` 读成 `List<Integer>`。
- 手写 XML 查询 Entity 时复用自动 ResultMap，id 为 `{Mapper 命名空间}.mybatis-plus_{实体简单名}`，同文件内写 `resultMap="mybatis-plus_Resume"`。
- 映射到 DTO（如岗位列表）时 XML 仍写 `typeHandler=JacksonTypeHandler`；`List<String>` 即使擦成 `List` 也能正确解析。
- 读：空 / NULL → handler 返回 `null`（与 MP 默认一致）；调用方用 `CollectionUtils.isEmpty`。
- 写：非空 List 序列化为 JSON 数组；`null` 则 SQL NULL。

---

## 覆盖列

| 表.列 | Java |
| --- | --- |
| `resumes.projects/careers/educations/awards/resume_matched_ids` | `List<Long>` |
| `jobs.content/requirements/keywords` | `List<String>` |
| `companies.industries` | `List<String>` |
| `project_experiences.tech_stack` | `List<String>` |
| `job_capability_profiles.strengths/missing_skills/weak_evidence_items` | `List<String>` |
| `job_student_matches.matched_highlights/critical_gaps/matched_keywords/missing_keywords` | `List<String>` |

未纳入：`highlights`（schema 与代码形状不一致）、`knowledge_sources`（对象数组）、第 3/4 类对象。

已删除自定义 `JsonbListTypeHandler`，勿再新增按表的 List TypeHandler。
