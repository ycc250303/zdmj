# PostgreSQL 数据库初始化说明

## 概述

本目录包含PostgreSQL数据库的完整建表脚本，用于替代原MongoDB存储方案。所有数据使用PostgreSQL的JSONB字段存储，同时使用pgvector扩展支持向量检索功能。

## 文件说明

- `2026-02-10.sql`: 完整的数据库建表脚本，包含所有表结构和索引

## 安装步骤

### 1. 安装PostgreSQL

确保已安装PostgreSQL 12或更高版本。

### 2. 安装pgvector扩展

在创建数据库后，需要安装pgvector扩展：

```bash
# 使用psql连接到数据库
psql -U postgres -d your_database_name

# 或者直接执行SQL文件
psql -U postgres -d your_database_name -f 2026-02-10.sql
```

pgvector扩展会在SQL脚本中自动创建（`CREATE EXTENSION IF NOT EXISTS vector;`）。

### 3. 执行建表脚本

```bash
psql -U postgres -d your_database_name -f 2026-02-10.sql
```

## 数据库表结构

### 用户模块
- `users`: 用户表
- `user_profiles`: 用户画像表（使用JSONB存储基础信息、技能、求职意向等）
- `user_behavior_logs`: 用户行为日志表

### 简历模块
- `educations`: 教育经历表
- `skills`: 技能表
- `careers`: 工作/实习经历表
- `project_experiences`: 项目经历表
- `resumes`: 简历表
- `resume_matches`: 专用简历表（针对岗位定制）

### 项目模块
- `projects`: 项目经验表（使用JSONB替代MongoDB）
- `projects_mined`: 项目挖掘表
- `projects_polished`: 项目打磨表

### 岗位模块
- `jobs`: 岗位表（使用JSONB替代MongoDB）

### 知识库模块
- `knowledge_bases`: 知识库表（使用JSONB替代MongoDB）

### 向量检索模块
- `knowledge_vectors`: 知识库向量表（使用pgvector）
- `job_vectors`: 岗位向量表（使用pgvector）
- `project_code_vectors`: 项目代码向量表（使用pgvector）

## 向量维度说明

所有向量表使用768维向量，对应SBERT模型（如all-MiniLM-L6-v2）的输出维度。

如需使用OpenAI的embedding模型（1536维），需要修改：
- `VECTOR(768)` 改为 `VECTOR(1536)`
- 相应调整向量化服务的配置

## 索引说明

### 向量索引

所有向量表都使用ivfflat索引进行余弦相似度搜索：

```sql
CREATE INDEX ... USING ivfflat (embedding vector_cosine_ops) WITH (lists = 100);
```

`lists = 100` 参数可根据数据量调整：
- 小数据集（< 10万条）：lists = 10-50
- 中等数据集（10-100万条）：lists = 100
- 大数据集（> 100万条）：lists = 100-200

### 业务索引

所有表都创建了必要的业务索引，包括：
- 用户ID索引
- 外键关联索引
- 状态字段索引
- 复合索引（用于常见查询场景）

## 注意事项

1. **外键约束**: 所有表都设置了外键约束和级联删除，确保数据一致性
2. **时间戳**: 统一使用 `TIMESTAMP WITH TIME ZONE` 类型
3. **JSONB字段**: 原MongoDB的文档数据存储在JSONB字段中，支持高效的JSON查询
4. **向量索引**: 向量索引需要在有数据后创建，但脚本中已包含创建语句

## 数据迁移

从MongoDB迁移到PostgreSQL时，需要：
1. 将MongoDB文档转换为JSONB格式
2. 保持原有的数据结构
3. 确保向量数据正确导入到向量表

## 性能优化建议

1. **向量索引**: 根据实际数据量调整ivfflat的lists参数
2. **JSONB索引**: 对频繁查询的JSONB字段创建GIN索引
3. **连接池**: 配置合适的数据库连接池大小
4. **查询优化**: 使用EXPLAIN ANALYZE分析慢查询

## 维护

定期执行：
- `VACUUM ANALYZE`: 清理和更新统计信息
- 检查向量索引的使用情况
- 监控数据库性能指标
