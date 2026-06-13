# 简历全量保存与单用户单简历

## 概述

- 每用户仅一份简历（`resumes.user_id` 唯一）
- 简历内容 = 该用户全部教育 / 工作 / 项目经历 + 关联技能（不再使用 `visible` 筛选）
- 新增 `GET/PUT /api/zdmj/resumes/me/content` 支持整页一次保存

## 接口

### 获取当前用户简历

```
GET /resumes/me/content
```

若用户尚无简历，自动创建默认壳（名称「我的简历」+ 默认技能）。

### 全量保存

```
PUT /resumes/me/content
```

请求体 `ResumeContentSaveRequest`：

| 字段 | 说明 |
|------|------|
| `name` | 可选，简历名称 |
| `skill` | 必填，无 `id` 则绑定/创建，有 `id` 则更新 |
| `educations` | 必填列表，无 `id` 新建，有 `id` 更新，DB 有但未提交则删除 |
| `careers` | 同上 |
| `projects` | 同上 |

事务内完成 diff；任一步失败整体回滚。

### 兼容接口

- `GET /resumes/content`：返回 0 或 1 条（当前用户唯一简历）
- `POST /resumes`：若用户已有简历返回 `3003 RESUME_ALREADY_EXISTS`

## 数据库

迁移脚本：`sql/migrations/20260612_resume_single_user_drop_visible.sql`

- 删除 `educations/careers/project_experiences.visible`
- `resumes.user_id` 唯一约束
- 保留 `projects/careers/educations` JSONB 列（暂不再写入）

## 测试

```bash
cd backend/zdmj
mvn test -Dtest=ResumeServiceImplTest
mvn test -Dtest=com.zdmj.resumeService.**.*Test
```
