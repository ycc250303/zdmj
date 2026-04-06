/**
 * 知识库相关类型定义
 * 与后端API严格对齐
 */

declare namespace Api {
  namespace Knowledge {
    /** 知识类型枚举 */
    type KnowledgeType = 1 | 2 | 3;
    // 1=项目文档, 2=GitHub仓库代码, 3=项目DeepWiki文档

    /** 知识库���础字段 */
    interface KnowledgeBase {
      /** 知识库ID */
      id: number;
      /** 用户ID */
      userId: number;
      /** 知识库名称 */
      name: string;
      /** 项目ID */
      projectId: number;
      /** 知识类型 */
      type: KnowledgeType;
      /** 文档内容或URL */
      content: string;
      /** 知识标签（数组） */
      tag: string[];
      /** 关联的向量ID数组（数字数组） */
      vectorIds: number[];
      /** 最近一次向量化任务ID */
      vectorTaskId: string | null;
      /** 最近一次任务状态（字符串或null） */
      vectorTaskStatus: string | null;
      /** 创建时间 */
      createdAt: string;
      /** 更新时间 */
      updatedAt: string;
    }

    /** 创建知识库DTO */
    interface KnowledgeCreate extends Pick<KnowledgeBase, 'name' | 'projectId' | 'type' | 'content' | 'tag'> {}

    /** 更新知识库DTO */
    interface KnowledgeUpdate extends KnowledgeCreate {
      id: number;
    }

    /** 分页查询参数 */
    interface KnowledgeQueryParams {
      page: number;
      limit: number;
      projectId?: number;
      type?: KnowledgeType;
    }

    /** 分页结果 - 与后端PageResult对齐 */
    interface KnowledgePageResult<T = KnowledgeBase> {
      data: T[];
      total: number;
      page: number;
      limit: number;
    }

    /** 文件上传结果 */
    interface FileUploadResult {
      key: string;
      url: string;
      fileName: string;
      fileSize: number;
      contentType: string;
    }
  }
}
