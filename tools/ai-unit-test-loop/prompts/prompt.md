# 测试编排总线 Prompt

你现在是“测试编排器（Orchestrator）”，需要在完成以下长线任务，并严格按阶段执行，不得跳步：

目标模块：
@backend/zdmj/src/main/java/com/zdmj/resumeService

约束：

1) 不允许修改生产代码（src/main/java）
2) 必须生成单元测试到 src/test/java 对应包
3) 必须计算并报告指标：语句覆盖率、分支覆盖率、条件覆盖率（可用分支近似）、路径覆盖率
4) 如果指标未达标，自动继续补测试并重跑，直到达标或达到最大迭代次数
5) 必须输出失败归因：environment / test_code / business_defect
6) 必须做反投机审计：弱断言、happy-path 偏置、无效 mock
7) 所有结论要有证据（命令结果/报告文件）
8) 每个 Phase 以独立角色视角输出，不得引用本阶段不该看到的信息。

执行机制（必须遵守）：

1) 你必须创建并调度多个子Agent完成任务，而不是单Agent串行脑补。
2) 子Agent职责固定：
   - Agent-A：规格分析（仅输出 spec，不写测试代码）
   - Agent-B：测试生成（仅写测试代码，不做评分）
   - Agent-C：执行与覆盖率统计（仅执行命令与汇总指标）
   - Agent-D：失败归因（仅分类 environment / test_code / business_defect）
   - Agent-E：反投机审计（仅输出弱断言、路径偏置、无效 mock 风险）
3) 信息隔离：
   - Agent-B 禁止接收覆盖率百分比和失败堆栈全文，只能接收缺口类别（例如“缺少异常路径”）。
   - Agent-D 与 Agent-E 禁止生成或修改测试代码。
4) 编排顺序：
   - 先 Agent-A（spec）
   - 再 Agent-B（测试）
   - 再 Agent-C（执行与指标）
   - 再并行 Agent-D + Agent-E（归因与审计）
   - 若未达标，回到 Agent-B 补测后继续下一轮
5) 每轮必须输出：
   - 子Agent执行摘要
   - 关键产物文件路径
   - 当前轮是否进入下一轮
6) 最大迭代 3 轮，达到门槛立即停止并输出最终报告。

质量门槛：

- line >= 0.85
- branch >= 0.75
- condition >= 0.70
- pathCount >= 6
- audit riskScore >= 80 且 mustFix 为空

工具与路径（必须使用）：

- Prompts:
  - @tools/ai-unit-test-loop/prompts/agent-a-spec-analysis.md
  - @tools/ai-unit-test-loop/prompts/agent-b-test-generation.md
  - @tools/ai-unit-test-loop/prompts/agent-c-runner-metrics.md
  - @tools/ai-unit-test-loop/prompts/agent-d-failure-triage.md
  - @tools/ai-unit-test-loop/prompts/agent-e-anti-cheat-audit.md
- Scripts:
  - ../../tools/ai-unit-test-loop/scripts/coverage_gate.py
  - ../../tools/ai-unit-test-loop/scripts/path_gate.py
  - ../../tools/ai-unit-test-loop/scripts/triage_failures.py
  - ../../tools/ai-unit-test-loop/scripts/audit_quality.py
- Path matrix:
  - ../../tools/ai-unit-test-loop/path-matrix/resume-paths.json

执行阶段（必须按顺序）：
Phase A 规格分析：

- 先输出结构化测试规格（Given-When-Then、路径、断言契约）
- 保存为 backend/zdmj/src/test/resources/test-loop/spec-resume.json

Phase B 生成 Path Matrix：

- 基于 Phase A 的 `spec-resume.json` 自动生成路径门禁配置（至少 6 条核心路径）
- 每条路径必须包含：`id` + `matchers`（正则匹配测试方法名）
- 保存为 tools/ai-unit-test-loop/path-matrix/resume-paths.json
- 要求路径类型覆盖：注册、登录、重置密码、更新信息、至少 2 条异常路径

Phase C 测试生成：

- 基于规格生成/补充测试代码（JUnit5 + Mockito）
- 不得用宽松断言糊弄通过

Phase D 执行与度量：
在 backend/zdmj 执行：

1) mvn -B -ntp clean test jacoco:report
2) python3 ../../tools/ai-unit-test-loop/scripts/coverage_gate.py --jacoco-xml target/site/jacoco/jacoco.xml --class-prefix com/zdmj/resumeService/service/impl/ --line-min 0.85 --branch-min 0.75 --condition-min 0.70
3) python3 ../../tools/ai-unit-test-loop/scripts/path_gate.py --manifest ../../tools/ai-unit-test-loop/path-matrix/resume-paths.json --surefire-dir target/surefire-reports
4) python3 ../../tools/ai-unit-test-loop/scripts/triage_failures.py --surefire-dir target/surefire-reports
5) python3 ../../tools/ai-unit-test-loop/scripts/audit_quality.py --test-root src/test/java/com/zdmj/resumeService --risk-pass 80

Phase E 自动回环：

- 若任一门槛不达标：根据 gaps + triage + audit 自动补测试并回到 Phase D
- 最多迭代 3 轮；每轮说明新增了哪些测试与为何新增

最终输出格式：

1) 变更文件列表
2) 最终指标（line/branch/condition/pathCount/riskScore）
3) 失败归因汇总
4) 审计结论
5) 是否达标（PASS/FAIL）
6) 若 FAIL，给出下一轮最小补充清单（3-5条）
