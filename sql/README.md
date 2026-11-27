# PostgreSQL 数据库维护

`pgsql.sql` 用于创建空数据库，`pgsql.md` 是字段、约束与索引字典。现有数据库使用 `migrations/` 中的增量脚本，执行前备份并停止应用写入。禁止对已有数据直接执行初始化脚本。

使用 PostgreSQL 15、pgvector 和 pg_trgm，嵌入维度为1024。知识文档直接保存范围和归属：范围1为用户私有，用户编号为正数；范围2为系统通用，范围3为学习路径，两者用户编号为空。向量通过文档取得访问范围。表间采用逻辑外键，由业务写入校验、事务和一致性检查保证关联。

## 结构变更脚本

`migrations/` 仅保存表、字段、约束、索引的结构变更，按对应旧结构选择执行。执行前停写备份，并保证已有数据满足目标字段与约束要求；新增非空字段、唯一约束及检查约束不包含自动清理或回填。禁止将整个目录作为可从任意旧版本直接运行的升级流程。

文档归属结构文件为 `20261126_remove_knowledge_bases.sql`，添加文档范围、调整用户归属约束、删除重复归属字段与知识库表，保留已有 HNSW 索引。会话结构文件为 `20261122_unify_conversation_memory.sql`，添加回复关联与状态字段、约束及索引，删除独立记忆表。这两个脚本各自使用事务，约束失败需回滚；已完成对应结构变更的服务器无需重复执行。

结构脚本通过 `psql -X -v ON_ERROR_STOP=1` 执行，未内置事务的脚本增加 `-1`。凭据由环境变量提供。恢复使用同一备份中的数据库和原文件目录；正式发布遵守 `docs/backend/ci-cd.md` 的固定提交与 CI 要求。

## 系统资料导入与评测

### JavaGuide 文档清理

选取范围和推广规则维护在 `javaguide_sources.json`，选中的技术、求职和学习路线资料统一为 `SYSTEM`。从固定 Git 提交读取原文，按 Markdown 结构清理推广；推广章节先校验已审查摘要，未知引用片段、代码变化、已知推广残留、正文为空或重复清理不一致立即终止。输出原文和正文均校验 SHA-256，此阶段不连接数据库或模型。

需要 Python ≥3.10，依赖固定在 `requirements-knowledge-cleaner.txt`。本地环境和导出示例：

```bash
python3 -m venv knowledge/cleaner-venv
knowledge/cleaner-venv/bin/python -m pip install -r sql/requirements-knowledge-cleaner.txt
knowledge/cleaner-venv/bin/python sql/clean_javaguide.py \
  --repo JavaGuide --output knowledge/javaguide/cleaned
```

本地先将上游仓库克隆到 `JavaGuide/`；`--ref` 可指定提交，默认 HEAD。输出目录须尚不存在且位于源仓库之外。`JavaGuide/` 和 `knowledge/` 均忽略提交。

### 服务器定时获取、清理与向量化

工具目录为 `/opt/zdmj/knowledge-tools/javaguide`，保留当前一份 `deploy/update_javaguide.sh`、`deploy/javaguide.cron` 和 `sql/` 中的清理脚本、规则、依赖声明；专用 `.venv/` 按依赖声明安装。宿主机需 Python ≥3.10、Git、Docker Compose、GNU coreutils 和 flock。资料根目录为 `/opt/zdmj/data/knowledge-sync/javaguide`，可通过 `JAVAGUIDE_DATA_ROOT` 修改，Python 可通过 `JAVAGUIDE_PYTHON` 指定。

Java 使用统一文本提取、800 token 分块、短尾部保留和每批最多10条的嵌入策略，调用平台配置的1024维嵌入模型。与个人文档共用向量化业务实现中的片段处理方法，统一组装文档关联和来源，每批最多500条并逐批校验写入数量；两类流程保留各自的受限删除SQL与事务边界。以 `javaguide:docs/<路径>` 匹配数据库文档，比较清理正文SHA-256与最后成功入库的 `content_hash`。首次导入处理全部文档；后续仅新增或正文变化的文档重新分块与嵌入，正文未变化且已有可用向量的文档保留chunkId、向量和上次向量化时间，标题与来源信息正常同步。上游提交、推广或清理规则变化未影响清理正文时不重新嵌入。同编号成功后重复提交只返回成功记录。

每次先读取来源内实际片段编号，校验成功文档的片段数量；事务外生成全部待更新文档的向量，提交时按文档编号锁定并重新核对比较依据。单事务按文档关联删除变化文档的旧片段、核对删除数量并插入新片段，保留文档编号；清单移除的资料及向量仅在此来源内删除，文件更名作为新来源导入并删除旧来源。失败保留原有可检索文档与向量。

服务器脚本通过同一后端镜像的命令行主类启动一次性容器，无HTTP入口。Java仅接受 `--run-id` 和 `--manifest-hash`，固定SYSTEM和空用户，只读取只读挂载的受控资料，验证固定来源、当前成功目录、路径和全部正文摘要。独立上下文仅装配知识持久化、文本解析与平台嵌入，不启动Web、Redis、邮件或在线业务恢复。容器以UID/GID10001运行、根目录只读，无端口，不挂载个人资料。新环境建立资料目录：

```bash
install -d -o root -g 10001 -m 0750 /opt/zdmj/data/knowledge-sync/javaguide
```

按 `docs/backend/ci-cd.md` 完成固定提交的CI与应用发布，再持有 `update.lock` 安装同版工具。Compose的 `knowledge-import` 仅显式调用时启动，脚本从已部署后端容器读取不可变镜像编号。应用 `.env` 提供数据库和平台嵌入密钥，资料根可由 `JAVAGUIDE_DATA_ROOT` 指定，应用仓库默认 `/opt/zdmj/zdmj`，可由 `JAVAGUIDE_APP_ROOT` 指定。服务器时区须为Asia/Shanghai：

```bash
# 使用当前清理资料执行首次导入、增量同步或重试
bash /opt/zdmj/knowledge-tools/javaguide/deploy/update_javaguide.sh import
# 获取新提交、清理并增量向量化
bash /opt/zdmj/knowledge-tools/javaguide/deploy/update_javaguide.sh update
install -o root -g root -m 0644 /opt/zdmj/knowledge-tools/javaguide/deploy/javaguide.cron /etc/cron.d/zdmj-javaguide
systemctl is-active cron
```

每周一00:00执行，调度上限7小时；模型连接10秒、读取120秒，调用不自动重试。任务完整持有 `update.lock`，固定容器名 `zdmj-javaguide-import`；前次容器仍存在时，在刷新正文前终止，需要先查看该容器和本次日志确认执行结果。脚本等待Java退出，退出成功且唯一结果摘要与运行编号、清单摘要和文档数量一致时，原子保存入库成功记录。失败进程返回非零状态，已提交的运行编号重试时不重复调用模型；未提交的运行编号重试继续比较数据库中的成功摘要，仅重新生成新增和变更文档的向量。清理成功后原子切换 `current`，只保留最新一份正文；模型导入失败保留数据库中的上次成功资料，清理与入库成功分别记录。提交成功后，日志逐篇记录新增、更新和移除路径，并通过 `added`、`updated`、`unchanged`、`removed`、`generatedChunks` 汇总本次变化；`SYSTEM_KNOWLEDGE_IMPORT_RESULT` 中的 `documents`、`chunks` 表示同步后当前来源的文档与片段总数。

以下路径均相对于数据目录：

| 路径 | 内容 |
| --- | --- |
| `current/raw/docs/`、`current/docs/` | 最新成功的候选原文、清理正文 |
| `current/manifest.json`、`current/SHA256SUMS` | 文档来源、路径、两种 SHA-256，以及脚本和规则摘要；可在 current 内运行 `sha256sum --check --strict SHA256SUMS` |
| `current/report.json` | 文档及代码块总数、逐篇规则命中、排除原因和验证结果 |
| `current/LICENSE.JavaGuide` | 上游许可证 |
| `current/success-history.json` | 最近两次成功摘要，包含编号、时间、来源提交、数量和验证结果 |
| `import-success-history.json` | 最近两条成功入库摘要 |
| `latest-attempt.log`、`scheduler.log` | 各覆盖保留最新一次执行日志 |

### 资料导入与检索评测

`import_system_knowledge.py --scope 2|3 --source-dir <目录>` 用于既有手工资料导入。JavaGuide定时导入使用上述Java命令行流程。每篇分块及嵌入完成后，以该篇事务保存文档和向量；相同范围、相同来源路径保留文档编号。数据库或模型失败立即终止。凭据通过环境变量提供，`--dry-run` 只预览分块。

`eval_rag_retrieval.py` 的样本保存 `scope`、`document_id`、`chunk_id`，检索按文档范围及成功状态授权。历史评测样本升级时显式填写对应范围并删除 `knowledge_id`、`user_id`，保留问题和编号；范围须与评测参数一致。

数据库结构集成测试纳入后端 `clean verify`。脚本测试使用独立测试数据库及环境变量 `KNOWLEDGE_TEST_DSN`，执行 `python -m unittest discover -s sql -p 'test_knowledge_tools.py'`；依赖 `psycopg2-binary openai tiktoken`，测试会重建指定数据库中的表，只允许使用隔离临时数据库。
