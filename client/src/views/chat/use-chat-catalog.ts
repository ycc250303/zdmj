import { computed, ref } from 'vue';
import {
  fetchCreateConversation,
  fetchGetConversationById,
  fetchGetConversations,
  fetchUpdateConversationConfig
} from '@/service/api/conversation';
import type { ConversationApi } from '@/service/api/conversation';
import { fetchGetKnowledgeDocumentList } from '@/service/api/knowledge';
import type { KnowledgeApi } from '@/service/api/knowledge';

export type DocumentRangeMode = 'all' | 'selected' | 'none';

/** 会话与文档按页读取；当前会话和检索范围独立于列表页面保存。 */
export function useChatCatalog(showError: (text: string) => void) {
  const conversations = ref<ConversationApi.Conversation[]>([]);
  const currentConversation = ref<ConversationApi.Conversation | null>(null);
  const currentConversationId = computed(() => currentConversation.value?.id ?? null);
  const conversationPage = ref(1);
  const conversationTotal = ref(0);
  const conversationLoading = ref(false);
  const knowledgeDocuments = ref<KnowledgeApi.KnowledgeDocumentDTO[]>([]);
  const knowledgePage = ref(1);
  const knowledgeTotal = ref(0);
  const knowledgeLoading = ref(false);
  const pageSize = 20;
  const documentRangeMode = ref<DocumentRangeMode>('all');
  const selectedDocumentIds = ref<number[] | null>(null);
  const useSystemKnowledge = ref(false);
  const configSaving = ref(false);
  let conversationRequest = 0;
  let knowledgeRequest = 0;
  let selectionRequest = 0;

  function syncConfig() {
    const config = currentConversation.value?.config;
    selectedDocumentIds.value =
      config?.ragDocumentIds === null || config?.ragDocumentIds === undefined ? null : [...config.ragDocumentIds];
    if (selectedDocumentIds.value === null) documentRangeMode.value = 'all';
    else documentRangeMode.value = selectedDocumentIds.value.length > 0 ? 'selected' : 'none';
    useSystemKnowledge.value = config?.useSystemKnowledge ?? false;
  }

  function acceptConversation(conversation: ConversationApi.Conversation) {
    currentConversation.value = conversation;
    const index = conversations.value.findIndex(item => item.id === conversation.id);
    if (index >= 0) conversations.value[index] = conversation;
    syncConfig();
  }

  async function loadConversations(page = conversationPage.value): Promise<boolean> {
    conversationRequest += 1;
    const request = conversationRequest;
    conversationLoading.value = true;
    try {
      const { data, error } = await fetchGetConversations({ page, limit: pageSize });
      if (request !== conversationRequest) return false;
      if (error || !data) {
        showError('会话列表加载失败');
        return false;
      }
      const lastPage = Math.max(1, data.totalPages);
      if (page > lastPage) return await loadConversations(lastPage);
      conversations.value = data.list;
      conversationPage.value = data.page;
      conversationTotal.value = data.total;
      return true;
    } finally {
      if (request === conversationRequest) conversationLoading.value = false;
    }
  }

  async function loadKnowledgeDocuments(page = knowledgePage.value) {
    knowledgeRequest += 1;
    const request = knowledgeRequest;
    knowledgeLoading.value = true;
    try {
      const { data, error } = await fetchGetKnowledgeDocumentList({ page, limit: pageSize });
      if (request !== knowledgeRequest) return;
      if (error || !data) {
        showError('知识文档加载失败');
        return;
      }
      knowledgeDocuments.value = data.list;
      knowledgePage.value = data.page;
      knowledgeTotal.value = data.total;
    } finally {
      if (request === knowledgeRequest) knowledgeLoading.value = false;
    }
  }

  async function selectConversation(id: number) {
    selectionRequest += 1;
    const request = selectionRequest;
    const { data, error } = await fetchGetConversationById(id);
    if (request !== selectionRequest) return false;
    if (error || !data) {
      showError('会话详情加载失败');
      return false;
    }
    acceptConversation(data);
    return true;
  }

  async function refreshCurrentConversation() {
    const id = currentConversationId.value;
    const selection = selectionRequest;
    if (id === null) return;
    const { data, error } = await fetchGetConversationById(id);
    if (selection !== selectionRequest || currentConversationId.value !== id) return;
    if (error || !data) {
      showError('会话详情加载失败');
      return;
    }
    acceptConversation(data);
  }

  function buildConversationConfig(): ConversationApi.ConversationConfig {
    return {
      ragDocumentIds: selectedDocumentIds.value === null ? null : [...selectedDocumentIds.value],
      useSystemKnowledge: useSystemKnowledge.value
    };
  }

  async function createConversation() {
    selectionRequest += 1;
    const selection = selectionRequest;
    const { data, error } = await fetchCreateConversation({ config: buildConversationConfig() });
    if (error || !data) {
      showError('创建会话失败');
      return null;
    }
    if (selection !== selectionRequest) return null;
    acceptConversation(data);
    await loadConversations(1);
    return data;
  }

  function clearCurrentConversation() {
    selectionRequest += 1;
    currentConversation.value = null;
    syncConfig();
  }

  async function persistConfig(patch: ConversationApi.ConversationConfig) {
    const id = currentConversationId.value;
    if (id === null) return true;
    const selection = selectionRequest;
    const rangeMode = documentRangeMode.value;
    configSaving.value = true;
    try {
      const { data, error } = await fetchUpdateConversationConfig(id, patch);
      if (error || !data) {
        showError('知识库选择保存失败');
        if (selection === selectionRequest) syncConfig();
        return false;
      }
      if (selection === selectionRequest && currentConversationId.value === id) {
        acceptConversation(data);
        // 指定范围尚未选取文档时，保留当前选择界面。
        if (selectedDocumentIds.value?.length === 0 && rangeMode === 'selected') {
          documentRangeMode.value = 'selected';
        }
      }
      return true;
    } finally {
      configSaving.value = false;
    }
  }

  const configFrozen = computed(() => (currentConversation.value?.messageCount ?? 0) > 0);

  async function changeDocumentRange(mode: DocumentRangeMode) {
    if (configSaving.value || configFrozen.value) return;
    documentRangeMode.value = mode;
    selectedDocumentIds.value = mode === 'all' ? null : [];
    await persistConfig({ ragDocumentIds: selectedDocumentIds.value });
  }

  async function toggleDocument(documentId: number, enabled: boolean) {
    if (configSaving.value || configFrozen.value || documentRangeMode.value !== 'selected') return;
    const ids = selectedDocumentIds.value ?? [];
    if (enabled) selectedDocumentIds.value = ids.includes(documentId) ? ids : [...ids, documentId];
    else selectedDocumentIds.value = ids.filter(id => id !== documentId);
    await persistConfig({ ragDocumentIds: selectedDocumentIds.value });
  }

  async function toggleSystemKnowledge(enabled: boolean) {
    if (configSaving.value || configFrozen.value) return;
    useSystemKnowledge.value = enabled;
    await persistConfig({ useSystemKnowledge: enabled });
  }

  function isDocumentEnabled(id?: number) {
    return id !== undefined && (selectedDocumentIds.value === null || selectedDocumentIds.value.includes(id));
  }

  return {
    conversations,
    currentConversation,
    currentConversationId,
    conversationPage,
    conversationTotal,
    conversationLoading,
    knowledgeDocuments,
    knowledgePage,
    knowledgeTotal,
    knowledgeLoading,
    pageSize,
    documentRangeMode,
    selectedDocumentIds,
    useSystemKnowledge,
    configSaving,
    loadConversations,
    loadKnowledgeDocuments,
    selectConversation,
    refreshCurrentConversation,
    createConversation,
    clearCurrentConversation,
    changeDocumentRange,
    toggleDocument,
    toggleSystemKnowledge,
    isDocumentEnabled
  };
}
