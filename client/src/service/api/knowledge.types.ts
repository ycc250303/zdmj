/** 知识类型枚举 */
export type KnowledgeType = 1 | 2 | 3;
// 1=项目文档, 2=GitHub仓库代码, 3=项目DeepWiki文档

/** 向量化状态枚举 */
export type EmbeddingStatus = 'PENDING' | 'RUNNING' | 'SUCCESS' | 'FAILED';

/** 创建知识文档 DTO */
export interface KnowledgeDocumentCreate {
  title: string;
  type: KnowledgeType;
  content: string;
}

/** 更新知识文档 DTO */
export interface KnowledgeDocumentUpdate extends KnowledgeDocumentCreate {
  id: number;
}

/** 知识文档响应 DTO (公开视图) */
export interface KnowledgeDocumentDTO {
  id?: number;
  type: KnowledgeType;
  content: string;
  title: string;
  embeddingStatus: EmbeddingStatus | null;
  lastEmbeddedAt: string | null;
  lastError: string | null;
  metadata: Record<string, any> | null;
  createdAt?: string;
  updatedAt?: string;
}

/** 知识库响应 DTO */
export interface KnowledgeBasesDTO {
  id: number;
  userId: number;
  scope: number;
  createdAt: string;
  updatedAt: string;
}

/** 分页查询参数 */
export type KnowledgeQueryParams = Api.Common.PageQueryParams;

/** 分页结果 */
export type PageResult<T> = Api.Common.PageDTO<T>;

/** 文件上传结果 */
export interface FileUploadResult {
  key: string;
  url: string;
  fileName: string;
  fileSize: number;
  contentType: string;
}
