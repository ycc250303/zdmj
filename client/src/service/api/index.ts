export * from './auth';
export * from './route';
// knowledge 与 profile 都导出了同名的 fetchUploadFile，
// 通过显式重导出避免歧义；如需该函数，请直接从子模块按需引入：
//   import { fetchUploadFile } from '@/service/api/knowledge';
//   import { fetchUploadFile } from '@/service/api/profile';
export {
  fetchCreateKnowledgeBases,
  fetchGetKnowledgeBases,
  fetchClearKnowledgeBases,
  fetchGetKnowledgeDocumentList,
  fetchGetKnowledgeDocumentDetail,
  fetchCreateKnowledgeDocument,
  fetchUpdateKnowledgeDocument,
  fetchDeleteKnowledgeDocument,
  fetchCreateKnowledge,
  fetchGetKnowledgeList,
  fetchGetKnowledgeDetail,
  fetchUpdateKnowledge,
  fetchDeleteKnowledge
} from './knowledge';
export type { KnowledgeApi } from './knowledge';
export * from './resume';
export {
  fetchGetCurrentCapabilityProfile,
  fetchQueryCapabilityProfile,
  fetchGenerateCapabilityProfile
} from './profile';
export type { CapabilityProfileApi, FileUploadResult } from './profile';
export * from './conversation';
export * from './job';
export * from './match';
