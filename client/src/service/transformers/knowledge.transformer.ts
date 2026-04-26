import type { KnowledgeApi } from '@/service/api/knowledge';

/**
 * 知识库数据转换工具
 * 处理前后端数据格式差异
 */

/**
 * 标签标准化处理
 * 处理后端可能返回的字符串或数组格式
 */
export function normalizeTag(tag: string[] | string | undefined): string[] {
  if (!tag) return [];
  if (Array.isArray(tag)) return tag;
  try {
    const parsed = JSON.parse(tag);
    return Array.isArray(parsed) ? parsed : [];
  } catch {
    return [];
  }
}

/**
 * 向量ID标准化处理
 * 将数字数组转换为字符串数组（前端统一使用字符串）
 */
export function normalizeVectorIds(vectorIds: number[] | string[] | undefined): string[] {
  if (!vectorIds) return [];
  if (Array.isArray(vectorIds)) {
    return vectorIds.map(String);
  }
  return [];
}

/**
 * 知识库数据标准化
 * 确保所有字段格式正确
 */
export function normalizeKnowledgeData(data: KnowledgeApi.KnowledgeDTO): KnowledgeApi.KnowledgeDTO {
  return {
    ...data,
    tag: normalizeTag(data.tag as unknown as string),
    vectorIds: normalizeVectorIds(data.vectorIds as unknown as number[])
  };
}

/**
 * 批量标准化知识库数据
 */
export function normalizeKnowledgeList(list: KnowledgeApi.KnowledgeDTO[]): KnowledgeApi.KnowledgeDTO[] {
  return list.map(normalizeKnowledgeData);
}
