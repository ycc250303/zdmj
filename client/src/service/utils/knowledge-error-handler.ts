import type { ErrorMessage } from '@/service/request/type';

/**
 * 知识库相关错误处理工具
 */

/**
 * 知识库相关错误消息映射
 */
const KnowledgeErrorMessages: Record<number, string> = {
  8001: '知识库保存失败，请重试',
  8002: '知识库ID不能为空',
  8003: '知识库更新失败，请重试',
  8004: '知识库删除失败，请重试',
  8005: '知识库不存在或已被删除',
  8006: '知识库名称已存在，请使用其他名称',
  8009: '不支持的文件类型，仅支持PDF���Markdown文件',
  8010: '知识库向量化失败'
};

/**
 * 获取友好的错误消息
 */
export function getKnowledgeErrorMessage(error: ErrorMessage): string {
  return KnowledgeErrorMessages[error.code] || error.msg || '操作失败，请重试';
}

/**
 * 显示知识库操作错误
 */
export function showKnowledgeError(error: ErrorMessage): void {
  const message = getKnowledgeErrorMessage(error);
  window.$message?.error(message);
}

/**
 * 显示知识库操作成功消息
 */
export function showKnowledgeSuccess(message: string): void {
  window.$message?.success(message);
}
