# PostgreSQL JSON/JSONB 查询指南

## 概述

PostgreSQL 支持两种 JSON 数据类型：
- **JSON**：存储 JSON 数据的精确副本（保留空格、键顺序等）
- **JSONB**：二进制格式，查询性能更好，推荐使用

## 基本操作符

### 1. 访问操作符

#### `->` 操作符：返回 JSON 对象（保持 JSON 格式）

```sql
-- 获取 JSON 对象字段
SELECT basic_info->'name' FROM user_profiles;
-- 结果：字符串类型，如 "张三"

-- 获取嵌套字段
SELECT basic_info->'address'->'city' FROM user_profiles;
```

#### `->>` 操作符：返回文本（转换为字符串）

```sql
-- 获取文本值
SELECT basic_info->>'name' FROM user_profiles;
-- 结果：文本类型，如 张三（不带引号）

-- 可以用于 WHERE 条件
SELECT * FROM user_profiles WHERE basic_info->>'name' = '张三';
```

#### `#>` 操作符：通过路径数组访问

```sql
-- 使用路径数组访问嵌套字段
SELECT basic_info#>'{address,city}' FROM user_profiles;
-- 等价于：basic_info->'address'->'city'
```

#### `#>>` 操作符：通过路径数组返回文本

```sql
-- 返回文本值
SELECT basic_info#>>'{address,city}' FROM user_profiles;
```

### 2. 包含操作符

#### `@>` 操作符：左侧 JSON 是否包含右侧 JSON

```sql
-- 检查 basic_info 是否包含指定的键值对
SELECT * FROM user_profiles 
WHERE basic_info @> '{"gender": "male"}'::jsonb;

-- 检查嵌套对象
SELECT * FROM user_profiles 
WHERE basic_info @> '{"address": {"city": "北京"}}'::jsonb;
```

#### `<@` 操作符：左侧 JSON 是否被右侧 JSON 包含

```sql
-- 检查 basic_info 是否被指定的 JSON 包含
SELECT * FROM user_profiles 
WHERE '{"age": 25}'::jsonb <@ basic_info;
```

#### `?` 操作符：检查键是否存在（仅 JSONB）

```sql
-- 检查 basic_info 中是否存在 'name' 键
SELECT * FROM user_profiles WHERE basic_info ? 'name';

-- 检查多个键（使用 OR）
SELECT * FROM user_profiles 
WHERE basic_info ? 'name' OR basic_info ? 'email';
```

#### `?&` 操作符：检查所有键是否存在（仅 JSONB）

```sql
-- 检查 basic_info 中是否同时存在 'name' 和 'age' 键
SELECT * FROM user_profiles WHERE basic_info ?& ARRAY['name', 'age'];
```

#### `?|` 操作符：检查任意键是否存在（仅 JSONB）

```sql
-- 检查 basic_info 中是否存在 'name' 或 'email' 键
SELECT * FROM user_profiles WHERE basic_info ?| ARRAY['name', 'email'];
```

### 3. 数组操作

#### 访问数组元素

```sql
-- 假设 skills 是一个数组：["Java", "Python", "Go"]
SELECT skills->0 FROM user_profiles;  -- 获取第一个元素
SELECT skills->>0 FROM user_profiles; -- 获取第一个元素的文本值

-- 获取数组长度
SELECT jsonb_array_length(skills) FROM user_profiles;
```

#### 检查数组是否包含值

```sql
-- 检查 skills 数组是否包含 "Java"
SELECT * FROM user_profiles WHERE skills @> '"Java"'::jsonb;

-- 检查数组是否包含多个值
SELECT * FROM user_profiles 
WHERE skills @> '["Java", "Python"]'::jsonb;
```

## 常用函数

### 1. 类型转换函数

```sql
-- JSON 转文本
SELECT jsonb_pretty(basic_info) FROM user_profiles; -- 格式化输出

-- 文本转 JSON
SELECT '{"name": "张三"}'::jsonb;

-- JSON 转数组
SELECT jsonb_array_elements(skills) FROM user_profiles;
```

### 2. 提取函数

```sql
-- 提取所有键
SELECT jsonb_object_keys(basic_info) FROM user_profiles;

-- 提取所有值
SELECT jsonb_each(basic_info) FROM user_profiles;

-- 提取键值对
SELECT jsonb_each_text(basic_info) FROM user_profiles;
```

### 3. 修改函数

```sql
-- 设置/更新字段值
UPDATE user_profiles 
SET basic_info = jsonb_set(basic_info, '{age}', '26'::jsonb)
WHERE user_id = 1;

-- 删除字段
UPDATE user_profiles 
SET basic_info = basic_info - 'age'
WHERE user_id = 1;

-- 删除多个字段
UPDATE user_profiles 
SET basic_info = basic_info - ARRAY['age', 'phone']
WHERE user_id = 1;

-- 合并 JSON 对象
UPDATE user_profiles 
SET basic_info = basic_info || '{"newField": "value"}'::jsonb
WHERE user_id = 1;
```

### 4. 聚合函数

```sql
-- JSON 对象聚合
SELECT jsonb_object_agg(key, value) 
FROM jsonb_each(basic_info);

-- JSON 数组聚合
SELECT jsonb_agg(skill) 
FROM (SELECT jsonb_array_elements_text(skills) AS skill FROM user_profiles) t;
```

## 实际应用示例

### 示例 1：查询用户画像信息

```sql
-- 查询所有用户的姓名和年龄
SELECT 
    user_id,
    basic_info->>'name' AS name,
    basic_info->>'age' AS age
FROM user_profiles;

-- 查询特定城市的用户
SELECT * FROM user_profiles 
WHERE basic_info->>'city' = '北京';
```

### 示例 2：查询技能信息

```sql
-- 查询拥有 "Java" 技能的用户
SELECT * FROM user_profiles 
WHERE skills @> '"Java"'::jsonb;

-- 查询拥有多个技能的用户（同时拥有 Java 和 Python）
SELECT * FROM user_profiles 
WHERE skills @> '["Java", "Python"]'::jsonb;

-- 查询技能数量大于 3 的用户
SELECT * FROM user_profiles 
WHERE jsonb_array_length(skills) > 3;
```

### 示例 3：查询工作意向

```sql
-- 假设 job_intention 结构：{"position": "Java开发", "salary": 15000, "city": "北京"}

-- 查询期望薪资大于 10000 的用户
SELECT * FROM user_profiles 
WHERE (job_intention->>'salary')::int > 10000;

-- 查询期望职位包含 "Java" 的用户
SELECT * FROM user_profiles 
WHERE job_intention->>'position' LIKE '%Java%';
```

### 示例 4：复杂查询

```sql
-- 查询年龄在 25-30 之间，且拥有 Java 技能的用户
SELECT 
    user_id,
    basic_info->>'name' AS name,
    basic_info->>'age' AS age,
    skills
FROM user_profiles 
WHERE (basic_info->>'age')::int BETWEEN 25 AND 30
  AND skills @> '"Java"'::jsonb;

-- 查询所有技能（展开数组）
SELECT 
    user_id,
    jsonb_array_elements_text(skills) AS skill
FROM user_profiles;
```

### 示例 5：更新 JSON 字段

```sql
-- 更新用户年龄
UPDATE user_profiles 
SET basic_info = jsonb_set(basic_info, '{age}', '26'::jsonb)
WHERE user_id = 1;

-- 添加新字段
UPDATE user_profiles 
SET basic_info = basic_info || '{"phone": "13800138000"}'::jsonb
WHERE user_id = 1;

-- 在数组中添加元素
UPDATE user_profiles 
SET skills = skills || '["Go"]'::jsonb
WHERE user_id = 1;

-- 删除字段
UPDATE user_profiles 
SET basic_info = basic_info - 'phone'
WHERE user_id = 1;
```

### 示例 6：在 MyBatis 中使用

#### Mapper 接口

```java
@Mapper
public interface UserProfileMapper {
    
    // 查询特定技能的用户
    List<UserProfile> selectBySkill(@Param("skill") String skill);
    
    // 查询特定城市的用户
    List<UserProfile> selectByCity(@Param("city") String city);
    
    // 更新 JSON 字段
    int updateBasicInfo(@Param("userId") Long userId, 
                        @Param("field") String field, 
                        @Param("value") String value);
}
```

#### Mapper XML

```xml
<!-- 查询拥有特定技能的用户 -->
<select id="selectBySkill" resultMap="BaseResultMap">
    SELECT * FROM user_profiles
    WHERE skills @> #{skill}::jsonb
</select>

<!-- 查询特定城市的用户 -->
<select id="selectByCity" resultMap="BaseResultMap">
    SELECT * FROM user_profiles
    WHERE basic_info->>'city' = #{city}
</select>

<!-- 更新 JSON 字段 -->
<update id="updateBasicInfo">
    UPDATE user_profiles
    SET basic_info = jsonb_set(basic_info, 
                               CONCAT('{', #{field}, '}')::text[], 
                               #{value}::jsonb),
        updated_at = CURRENT_TIMESTAMP
    WHERE user_id = #{userId}
</update>
```

## 性能优化建议

### 1. 使用 GIN 索引

```sql
-- 为 JSONB 字段创建 GIN 索引（支持 @>, ?, ?&, ?| 操作符）
CREATE INDEX idx_user_profiles_basic_info ON user_profiles 
USING GIN (basic_info);

CREATE INDEX idx_user_profiles_skills ON user_profiles 
USING GIN (skills);

-- 为特定路径创建索引
CREATE INDEX idx_user_profiles_city ON user_profiles 
USING BTREE ((basic_info->>'city'));
```

### 2. 使用表达式索引

```sql
-- 为经常查询的 JSON 字段创建表达式索引
CREATE INDEX idx_user_profiles_age ON user_profiles 
USING BTREE (((basic_info->>'age')::int));

CREATE INDEX idx_user_profiles_salary ON user_profiles 
USING BTREE (((job_intention->>'salary')::int));
```

### 3. 查询优化

```sql
-- ✅ 好的查询（可以使用索引）
SELECT * FROM user_profiles 
WHERE basic_info @> '{"city": "北京"}'::jsonb;

-- ❌ 不好的查询（无法使用索引）
SELECT * FROM user_profiles 
WHERE basic_info->>'city' = '北京';

-- 但如果创建了表达式索引，第二种查询也可以很快
```

## 常见问题

### Q1: JSON 和 JSONB 的区别？

**JSON**：
- 保留原始格式（空格、键顺序等）
- 插入时不做验证
- 查询较慢

**JSONB**：
- 二进制格式，查询更快
- 插入时验证格式
- 不保留键顺序和多余空格
- **推荐使用 JSONB**

### Q2: 如何判断 JSON 字段是否存在？

```sql
-- 检查键是否存在
SELECT * FROM user_profiles WHERE basic_info ? 'name';

-- 检查值是否为空
SELECT * FROM user_profiles 
WHERE basic_info->>'name' IS NOT NULL 
  AND basic_info->>'name' != '';
```

### Q3: 如何查询 JSON 数组中的元素？

```sql
-- 方法1：使用 @> 操作符
SELECT * FROM user_profiles WHERE skills @> '"Java"'::jsonb;

-- 方法2：展开数组后查询
SELECT * FROM user_profiles 
WHERE EXISTS (
    SELECT 1 FROM jsonb_array_elements_text(skills) AS skill
    WHERE skill = 'Java'
);
```

### Q4: 如何更新嵌套的 JSON 字段？

```sql
-- 更新嵌套字段
UPDATE user_profiles 
SET basic_info = jsonb_set(
    basic_info, 
    '{address,city}', 
    '"上海"'::jsonb
)
WHERE user_id = 1;
```

## 总结

PostgreSQL 的 JSON/JSONB 查询功能强大，主要操作符包括：

- **访问**：`->`, `->>`, `#>`, `#>>`
- **包含**：`@>`, `<@`, `?`, `?&`, `?|`
- **修改**：`jsonb_set()`, `-`, `||`

**最佳实践**：
1. 使用 JSONB 而不是 JSON
2. 为常用查询字段创建 GIN 索引或表达式索引
3. 使用 `@>` 操作符进行包含查询（可以利用索引）
4. 避免在 WHERE 子句中使用函数（除非创建了表达式索引）
