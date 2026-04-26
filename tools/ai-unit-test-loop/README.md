# AI 单元测试生成与验证闭环（zdmj）

这个目录是独立工具区，不属于业务功能代码。

## 1. 目标

- 输入：任一业务模块源码目录（示例：`backend/zdmj/src/main/java/com/zdmj/userAuthService`）。
- 输出：模块对应的单元测试、覆盖率报告、失败归因报告、反投机审计报告。
- 约束：测试代码不能修改生产代码；分析与生成、执行与评分必须由不同 Agent 承担。

## 2. Agent 契约与隔离边界

| Agent | 职责 | 可读输入 | 禁止输入 | 输出 |
| --- | --- | --- | --- | --- |
| Agent-A (Spec) | 提炼 Given-When-Then、覆盖目标、风险路径 | 模块源码、异常码定义 | 覆盖率数值、失败堆栈 | `spec.json` |
| Agent-B (TestGen) | 编写 JUnit5/Mockito 单测 | `spec.json`、源码片段 | 失败明细、覆盖率百分比 | 测试代码 |
| Agent-C (Runner) | 执行测试、收集覆盖率 | 测试代码、Maven 输出 | 业务需求文本 | `coverage-summary.json` |
| Agent-D (Triage) | 失败归因（环境/测试/业务） | surefire 报告、堆栈、`spec.json` | 覆盖率百分比 | `triage-report.json` |
| Agent-E (Audit) | 审计弱断言/无效 mock/重复 happy-path | 测试源码、测试结果 | 源码改写能力 | `audit-report.json` |

说明：Agent-B 不参与评分，Agent-D/E 不产出测试代码，避免“自己出题自己打分”。

## 3. 质量门槛

- 语句覆盖率（line）`>= 85%`
- 分支覆盖率（branch）`>= 75%`
- 条件覆盖率（condition）`>= 70%`（工程内按分支覆盖率近似）
- 路径覆盖率（path）`>= 6` 条核心路径
- 弱断言占比 `<= 20%`
- 每个公共方法至少一个异常路径测试

## 4. 失败归因标准

- 环境错误：上下文启动失败、端口占用、外部服务不可达、配置缺失。
- 测试错误：mock 约束错误、测试数据非法、断言与规格冲突。
- 业务错误：前置条件满足且断言合理时稳定失败。

## 5. 执行顺序（本地/CI）

1. Agent-A 生成 `spec.json`
2. Agent-B 生成/补充测试代码
3. 在 `backend/zdmj` 执行 `mvn -B -ntp clean test jacoco:report`
4. 执行覆盖率门禁：`tools/ai-unit-test-loop/scripts/coverage_gate.py`
5. 执行路径门禁：`tools/ai-unit-test-loop/scripts/path_gate.py`
6. 执行失败归因：`tools/ai-unit-test-loop/scripts/triage_failures.py`
7. 执行反投机审计：`tools/ai-unit-test-loop/scripts/audit_quality.py`
8. 任一门禁失败则回环至 Agent-B（连续 2 轮覆盖率提升 <2% 时回 Agent-A）

## 6. 目录结构

- `prompts/`：多 Agent 提示词模板
- `scripts/`：覆盖率门禁、路径门禁、失败归因、反投机审计脚本
- `path-matrix/`：路径覆盖映射文件

## 7. 快速使用（userAuthService）

在 `backend/zdmj` 执行：

- `mvn -B -ntp clean test jacoco:report`
- `python3 ../../tools/ai-unit-test-loop/scripts/coverage_gate.py --jacoco-xml target/site/jacoco/jacoco.xml --class-prefix com/zdmj/userAuthService/service/impl/,com/zdmj/userAuthService/util/ --line-min 0.85 --branch-min 0.75 --condition-min 0.70`
- `python3 ../../tools/ai-unit-test-loop/scripts/path_gate.py --manifest ../../tools/ai-unit-test-loop/path-matrix/user-auth-paths.json --surefire-dir target/surefire-reports`
- `python3 ../../tools/ai-unit-test-loop/scripts/audit_quality.py --test-root src/test/java/com/zdmj/userAuthService --risk-pass 80`
- `python3 ../../tools/ai-unit-test-loop/scripts/triage_failures.py --surefire-dir target/surefire-reports`

若门禁失败，按输出回到 Agent-B 补用例；连续两轮无明显提升，回到 Agent-A 重拆规格。

## 8. 切换到其他模块（以 jobService 为例）

至少同步修改以下字段：

1. 目标模块路径
   - 从：`@backend/zdmj/src/main/java/com/zdmj/userAuthService`
   - 到：`@backend/zdmj/src/main/java/com/zdmj/jobService`
2. 规格产物文件名（spec）
   - 从：`backend/zdmj/src/test/resources/test-loop/spec-user-auth.json`
   - 到：`backend/zdmj/src/test/resources/test-loop/spec-job.json`
3. Path Matrix 文件名与命令参数
   - 从：`tools/ai-unit-test-loop/path-matrix/user-auth-paths.json`
   - 到：`tools/ai-unit-test-loop/path-matrix/job-paths.json`
4. 覆盖率统计范围（class-prefix）
   - 从：`com/zdmj/userAuthService/service/impl/,com/zdmj/userAuthService/util/`
   - 到：`com/zdmj/jobService/service/impl/,com/zdmj/jobService/util/`
5. 反投机审计测试根目录（test-root）
   - 从：`src/test/java/com/zdmj/userAuthService`
   - 到：`src/test/java/com/zdmj/jobService`
6. Path Matrix 业务路径类型描述
   - 将“注册/登录/重置密码/更新信息”替换为模块自己的核心路径与异常路径

最小检查清单：

- `spec-*.json`、`*-paths.json` 已按模块重命名
- `--class-prefix` 与包路径一致
- `--test-root` 指向正确测试目录
- `pathCountMin` 与模块复杂度匹配（默认 6，可按需提高）

## 9. CI

`.github/workflows/ci_java.yml` 已配置调用该工具目录下脚本。
