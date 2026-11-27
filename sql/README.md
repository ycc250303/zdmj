# PostgreSQL 数据库维护

`pgsql.sql` 用于创建空数据库，`pgsql.md` 是字段、约束与索引字典。现有数据库使用 `migrations/` 中的增量脚本，执行前备份并停止应用写入。禁止对已有数据直接执行初始化脚本。

使用 PostgreSQL 15、pgvector 和 pg_trgm，嵌入维度为1024。知识文档直接保存范围和归属：范围1为用户私有，用户编号为正数；范围2为系统通用，范围3为学习路径，两者用户编号为空。向量通过文档取得访问范围。表间采用逻辑外键，由业务写入校验、事务和一致性检查保证关联。

## 结构变更脚本

`migrations/` 仅保存表、字段、约束、索引的结构变更，按对应旧结构选择执行。执行前停写备份，并保证已有数据满足目标字段与约束要求；新增非空字段、唯一约束及检查约束不包含自动清理或回填。禁止将整个目录作为可从任意旧版本直接运行的升级流程。

文档归属结构文件为 `20261126_remove_knowledge_bases.sql`，添加文档范围、调整用户归属约束、删除重复归属字段与知识库表，保留已有 HNSW 索引。会话结构文件为 `20261122_unify_conversation_memory.sql`，添加回复关联与状态字段、约束及索引，删除独立记忆表。这两个脚本各自使用事务，约束失败需回滚；已完成对应结构变更的服务器无需重复执行。

结构脚本通过 `psql -X -v ON_ERROR_STOP=1` 执行，未内置事务的脚本增加 `-1`。凭据由环境变量提供。恢复使用同一备份中的数据库和原文件目录；正式发布遵守 `docs/backend/ci-cd.md` 的固定提交与 CI 要求。

## 系统资料手工导入与评测

`import_system_knowledge.py --scope 2|3 --source-dir <目录>` 导入 Markdown。分块及全部模型嵌入完成后，以单事务保存文档和全部向量；相同范围、相同来源路径保留文档编号。数据库或模型失败立即终止。凭据通过环境变量提供，`--dry-run` 只预览分块。

`eval_rag_retrieval.py` 的样本保存 `scope`、`document_id`、`chunk_id`，检索按文档范围及成功状态授权。历史评测样本升级时显式填写对应范围并删除 `knowledge_id`、`user_id`，保留问题和编号；范围须与评测参数一致。

数据库结构集成测试纳入后端 `clean verify`。脚本测试使用独立测试数据库及环境变量 `KNOWLEDGE_TEST_DSN`，执行 `python -m unittest discover -s sql -p 'test_knowledge_tools.py'`；依赖 `psycopg2-binary openai tiktoken`，测试会重建指定数据库中的表，只允许使用隔离临时数据库。
