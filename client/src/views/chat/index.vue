<script setup lang="ts">
import { computed, nextTick, onMounted, ref } from 'vue';
import { useDialog, useMessage } from 'naive-ui';
import { fetchChatStream, fetchDeleteConversation, fetchGetMessages } from '@/service/api/conversation';
import type { ConversationApi } from '@/service/api/conversation';
import type { KnowledgeApi } from '@/service/api/knowledge';
import { useChatCatalog } from './use-chat-catalog';
import { renderMessageMarkdown } from './markdown';
import 'highlight.js/styles/github-dark.css';

defineOptions({
  name: 'Chat'
});

const message = useMessage();
const dialog = useDialog();

// 状态定义
const {
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
} = useChatCatalog(text => message.error(text));
const messageList = ref<(ConversationApi.Message & { thinking?: boolean })[]>([]);
const inputText = ref('');
const loading = ref(false);
const sending = ref(false);
const messagesReady = ref(true);
const scrollRef = ref<any>(null);
const sidebarCollapsed = ref(false);

const SIDEBAR_WIDTH_KEY = 'chat-sidebar-width';
const DEFAULT_SIDEBAR_WIDTH = 260;
const MIN_SIDEBAR_WIDTH = 200;
const MAX_SIDEBAR_WIDTH = 480;

const sidebarWidth = ref(DEFAULT_SIDEBAR_WIDTH);
const isResizingSidebar = ref(false);

const clampSidebarWidth = (width: number) => Math.min(MAX_SIDEBAR_WIDTH, Math.max(MIN_SIDEBAR_WIDTH, width));

const loadSidebarWidth = () => {
  const saved = Number(localStorage.getItem(SIDEBAR_WIDTH_KEY));
  if (!Number.isNaN(saved) && saved > 0) {
    sidebarWidth.value = clampSidebarWidth(saved);
  }
};

const persistSidebarWidth = () => {
  localStorage.setItem(SIDEBAR_WIDTH_KEY, String(sidebarWidth.value));
};

// 切换侧边栏折叠状态
const toggleSidebar = () => {
  sidebarCollapsed.value = !sidebarCollapsed.value;
};

const startSidebarResize = (event: MouseEvent) => {
  event.preventDefault();
  isResizingSidebar.value = true;

  const startX = event.clientX;
  const startWidth = sidebarWidth.value;

  const handleMouseMove = (moveEvent: MouseEvent) => {
    sidebarWidth.value = clampSidebarWidth(startWidth + moveEvent.clientX - startX);
  };

  const handleMouseUp = () => {
    isResizingSidebar.value = false;
    document.removeEventListener('mousemove', handleMouseMove);
    document.removeEventListener('mouseup', handleMouseUp);
    document.body.style.cursor = '';
    document.body.style.userSelect = '';
    persistSidebarWidth();
  };

  document.body.style.cursor = 'col-resize';
  document.body.style.userSelect = 'none';
  document.addEventListener('mousemove', handleMouseMove);
  document.addEventListener('mouseup', handleMouseUp);
};

const KNOWLEDGE_PANEL_WIDTH_KEY = 'chat-knowledge-panel-width';
const DEFAULT_KNOWLEDGE_PANEL_WIDTH = 280;
const MIN_KNOWLEDGE_PANEL_WIDTH = 220;
const MAX_KNOWLEDGE_PANEL_WIDTH = 420;

const knowledgePanelCollapsed = ref(false);
const configFrozen = computed(() => (currentConversation.value?.messageCount ?? 0) > 0 || messageList.value.length > 0);
const knowledgePanelWidth = ref(DEFAULT_KNOWLEDGE_PANEL_WIDTH);
const isResizingKnowledgePanel = ref(false);

const clampKnowledgePanelWidth = (width: number) =>
  Math.min(MAX_KNOWLEDGE_PANEL_WIDTH, Math.max(MIN_KNOWLEDGE_PANEL_WIDTH, width));

const loadKnowledgePanelWidth = () => {
  const saved = Number(localStorage.getItem(KNOWLEDGE_PANEL_WIDTH_KEY));
  if (!Number.isNaN(saved) && saved > 0) {
    knowledgePanelWidth.value = clampKnowledgePanelWidth(saved);
  }
};

const persistKnowledgePanelWidth = () => {
  localStorage.setItem(KNOWLEDGE_PANEL_WIDTH_KEY, String(knowledgePanelWidth.value));
};

const toggleKnowledgePanel = () => {
  knowledgePanelCollapsed.value = !knowledgePanelCollapsed.value;
};

const startKnowledgePanelResize = (event: MouseEvent) => {
  event.preventDefault();
  isResizingKnowledgePanel.value = true;

  const startX = event.clientX;
  const startWidth = knowledgePanelWidth.value;

  const handleMouseMove = (moveEvent: MouseEvent) => {
    knowledgePanelWidth.value = clampKnowledgePanelWidth(startWidth + startX - moveEvent.clientX);
  };

  const handleMouseUp = () => {
    isResizingKnowledgePanel.value = false;
    document.removeEventListener('mousemove', handleMouseMove);
    document.removeEventListener('mouseup', handleMouseUp);
    document.body.style.cursor = '';
    document.body.style.userSelect = '';
    persistKnowledgePanelWidth();
  };

  document.body.style.cursor = 'col-resize';
  document.body.style.userSelect = 'none';
  document.addEventListener('mousemove', handleMouseMove);
  document.addEventListener('mouseup', handleMouseUp);
};

const knowledgeEmbeddingReady = (item: KnowledgeApi.KnowledgeDocumentDTO) => item.embeddingStatus === 'SUCCESS';

let messageRequest = 0;

// 新建会话
const handleNewChat = async () => {
  const created = await createConversation();
  if (created) {
    messageRequest += 1;
    messagesReady.value = true;
    messageList.value = [];
    inputText.value = '';
  }
};

async function handleSelectConversation(id: number) {
  messageRequest += 1;
  const request = messageRequest;
  loading.value = true;
  try {
    if (!(await selectConversation(id)) || request !== messageRequest) return;
    messageList.value = [];
    messagesReady.value = false;
    const { data, error } = await fetchGetMessages({ conversationId: id, page: 1, limit: 100 });
    if (request !== messageRequest || currentConversationId.value !== id) return;
    if (error || !data) {
      message.error('消息加载失败');
      return;
    }
    messagesReady.value = true;
    messageList.value = data.list.map(msg => ({ ...msg, thinking: false }));
    scrollToBottom();
  } finally {
    if (request === messageRequest) loading.value = false;
  }
}

// 删除会话
const handleDeleteConversation = (id: number, event: Event) => {
  event.stopPropagation();
  dialog.warning({
    title: '确认删除',
    content: '确定要删除该对话吗？',
    positiveText: '确定',
    negativeText: '取消',
    onPositiveClick: async () => {
      const { error } = await fetchDeleteConversation(id);
      if (!error) {
        message.success('删除成功');
        if (currentConversationId.value === id) {
          messageRequest += 1;
          clearCurrentConversation();
          messagesReady.value = true;
          messageList.value = [];
        }
        await loadConversations();
      } else {
        message.error('删除失败');
      }
    }
  });
};

// 发送消息
const handleSend = async () => {
  if (!inputText.value.trim() || sending.value || configSaving.value || loading.value || !messagesReady.value) return;

  // 如果没有当前会话，先创建一个（此时尚无消息，config 仍可改）
  if (!currentConversationId.value) {
    const created = await createConversation();
    if (!created) return;
  }

  const userText = inputText.value.trim();
  inputText.value = '';

  // 添加用户消息到列表
  const tempUserMsg: ConversationApi.Message & { thinking?: boolean } = {
    id: Date.now(),
    conversationId: currentConversationId.value!,
    userId: 0,
    role: 1, // 1=user, 2=assistant
    sequence: 0,
    content: userText,
    createdAt: new Date().toISOString()
  };
  messageList.value.push(tempUserMsg);
  scrollToBottom();

  sending.value = true;

  // 准备接收AI消息
  const tempAiMsg: ConversationApi.Message & { thinking?: boolean } = {
    id: Date.now() + 1,
    conversationId: currentConversationId.value!,
    userId: 0,
    role: 2, // 1=user, 2=assistant
    sequence: 0,
    content: '',
    thinking: true, // 初始状态为思考中
    createdAt: new Date().toISOString()
  };
  messageList.value.push(tempAiMsg);

  const requestData: ConversationApi.MessageDTO = {
    conversationId: currentConversationId.value!,
    message: userText
  };

  let hasReceivedContent = false;

  await fetchChatStream(requestData, {
    onMessage(text) {
      const lastMsg = messageList.value.find(
        item => item.id === tempAiMsg.id && item.conversationId === requestData.conversationId
      );
      if (lastMsg && lastMsg.role === 2) {
        if (!hasReceivedContent && lastMsg.thinking) {
          lastMsg.thinking = false;
          hasReceivedContent = true;
        }
        lastMsg.content += text;
      }
      scrollToBottom();
    },
    onError(error) {
      const detail = error instanceof Error ? error.message : '未知错误';
      console.error('发送消息详细错误:', error);
      message.error(`发送消息失败: ${detail}`);
      sending.value = false;
      messageList.value = messageList.value.filter(item => item.id !== tempAiMsg.id);
    },
    async onComplete() {
      sending.value = false;
      if (currentConversationId.value === requestData.conversationId) await refreshCurrentConversation();
      await loadConversations();
    }
  });
};

const handleKeyDown = (e: KeyboardEvent) => {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault();
    handleSend();
  }
};

function scrollToBottom() {
  nextTick(() => {
    if (scrollRef.value) {
      scrollRef.value.scrollTo({ position: 'bottom', behavior: 'smooth' });
    }
  });
}

// 格式化日期
const formatTime = (timeStr: string | null) => {
  if (!timeStr) return '';
  const date = new Date(timeStr);
  return `${date.getMonth() + 1}-${date.getDate()} ${date.getHours().toString().padStart(2, '0')}:${date.getMinutes().toString().padStart(2, '0')}`;
};

// 判断是否显示居中布局（空状态）
const isCenteredLayout = computed(() => messageList.value.length === 0 && !loading.value);

onMounted(() => {
  loadSidebarWidth();
  loadKnowledgePanelWidth();
  loadKnowledgeDocuments();
  loadConversations(1).then(loaded => {
    if (loaded && conversations.value.length > 0 && currentConversationId.value === null && !loading.value) {
      handleSelectConversation(conversations.value[0].id);
    }
  });
});
</script>

<template>
  <div class="box-border h-screen flex gap-0 overflow-hidden bg-white dark:bg-dark-200">
    <!-- 侧边栏：会话列表 -->
    <div
      v-show="!sidebarCollapsed"
      class="relative h-full flex flex-col flex-shrink-0 border-r border-gray-200 bg-gray-50 dark:border-gray-700 dark:bg-dark-100"
      :class="{ 'transition-all duration-300': !isResizingSidebar }"
      :style="{ width: `${sidebarWidth}px` }"
    >
      <!-- 新对话按钮和折叠按钮 -->
      <div class="flex gap-2 p-3">
        <NButton
          class="flex-1 transition-all duration-200 !border !border-blue-200 !rounded-lg !bg-white !text-gray-700 hover:shadow-md [&_.n-button__content]:!justify-center dark:!border-blue-800 hover:!border-blue-300 dark:!bg-gray-800 hover:!bg-blue-50 dark:!text-gray-300 hover:!text-blue-600 dark:hover:!border-blue-600 dark:hover:!bg-gray-700 dark:hover:!text-blue-400"
          :disabled="configSaving || sending || loading"
          @click="handleNewChat"
        >
          <template #icon>
            <div class="i-icon-park-outline:plus mr-1.5 !text-blue-500 dark:!text-blue-400" />
          </template>
          {{ $t('page.chat.newChat') }}
        </NButton>
        <NTooltip placement="bottom">
          <template #trigger>
            <NButton quaternary circle class="!text-gray-500 hover:!text-gray-700" @click="toggleSidebar">
              <template #icon>
                <svg
                  xmlns="http://www.w3.org/2000/svg"
                  width="18"
                  height="18"
                  viewBox="0 0 24 24"
                  fill="none"
                  stroke="currentColor"
                  stroke-width="2"
                  stroke-linecap="round"
                  stroke-linejoin="round"
                >
                  <polyline points="11 17 6 12 11 7"></polyline>
                  <polyline points="18 17 13 12 18 7"></polyline>
                </svg>
              </template>
            </NButton>
          </template>
          {{ $t('page.chat.collapseChatList') }}
        </NTooltip>
      </div>

      <!-- 对话列表 -->
      <NScrollbar class="flex-1">
        <div class="px-2">
          <div
            v-for="item in conversations"
            :key="item.id"
            class="group mb-1 cursor-pointer rounded-lg px-3 py-2.5 transition-all duration-200"
            :class="[
              currentConversationId === item.id
                ? 'bg-blue-50 dark:bg-blue-900/20'
                : 'hover:bg-gray-100 dark:hover:bg-gray-800/50'
            ]"
          >
            <div class="w-full flex items-center justify-between gap-2">
              <div class="min-w-0 flex flex-col flex-1 gap-0.5 pr-2" @click="handleSelectConversation(item.id)">
                <span
                  class="block truncate text-sm font-medium"
                  :class="
                    currentConversationId === item.id
                      ? 'text-blue-700 dark:text-blue-300'
                      : 'text-gray-700 dark:text-gray-300'
                  "
                  :title="item.title || $t('page.chat.newChat')"
                >
                  {{ item.title || $t('page.chat.newChat') }}
                </span>
                <span class="truncate text-xs text-gray-400 dark:text-gray-500">
                  {{ formatTime(item.lastMessageAt) }}
                </span>
              </div>
              <div
                class="h-8 w-8 flex flex-shrink-0 cursor-pointer items-center justify-center rounded text-gray-500 transition-colors hover:bg-gray-100 hover:text-blue-700 dark:hover:bg-gray-800/50 dark:hover:text-blue-300"
                @click="e => handleDeleteConversation(item.id, e)"
              >
                <svg
                  xmlns="http://www.w3.org/2000/svg"
                  width="16"
                  height="16"
                  viewBox="0 0 24 24"
                  fill="none"
                  stroke="currentColor"
                  stroke-width="2"
                  stroke-linecap="round"
                  stroke-linejoin="round"
                >
                  <path d="M3 6h18"></path>
                  <path d="M19 6v14c0 1-1 2-2 2H7c-1 0-2-1-2-2V6"></path>
                  <path d="M8 6V4c0-1 1-2 2-2h4c1 0 2 1 2 2v2"></path>
                </svg>
              </div>
            </div>
          </div>
        </div>
      </NScrollbar>

      <NPagination
        :page="conversationPage"
        :page-size="pageSize"
        :item-count="conversationTotal"
        :disabled="conversationLoading"
        simple
        class="justify-center p-3"
        aria-label="会话分页"
        @update:page="page => loadConversations(page)"
      />

      <div
        class="sidebar-resize-handle"
        :class="{ 'sidebar-resize-handle--active': isResizingSidebar }"
        @mousedown="startSidebarResize"
      />
    </div>

    <!-- 主区域：对话详情 -->
    <div class="relative h-full flex flex-col flex-1 bg-white dark:bg-dark-200">
      <!-- 侧边栏切换按钮（当侧边栏隐藏时显示） -->
      <div v-if="sidebarCollapsed" class="absolute left-2 top-2 z-10">
        <NTooltip placement="bottom">
          <template #trigger>
            <NButton quaternary circle class="!text-gray-500 hover:!text-gray-700" @click="toggleSidebar">
              <template #icon>
                <svg
                  xmlns="http://www.w3.org/2000/svg"
                  width="18"
                  height="18"
                  viewBox="0 0 24 24"
                  fill="none"
                  stroke="currentColor"
                  stroke-width="2"
                  stroke-linecap="round"
                  stroke-linejoin="round"
                >
                  <polyline points="13 17 18 12 13 7"></polyline>
                  <polyline points="6 17 11 12 6 7"></polyline>
                </svg>
              </template>
            </NButton>
          </template>
          {{ $t('page.chat.expandChatList') }}
        </NTooltip>
      </div>

      <div v-if="knowledgePanelCollapsed" class="absolute right-2 top-2 z-10">
        <NTooltip placement="bottom">
          <template #trigger>
            <NButton quaternary circle class="!text-gray-500 hover:!text-gray-700" @click="toggleKnowledgePanel">
              <template #icon>
                <svg
                  xmlns="http://www.w3.org/2000/svg"
                  width="18"
                  height="18"
                  viewBox="0 0 24 24"
                  fill="none"
                  stroke="currentColor"
                  stroke-width="2"
                  stroke-linecap="round"
                  stroke-linejoin="round"
                >
                  <polyline points="11 17 16 12 11 7"></polyline>
                  <polyline points="18 17 23 12 18 7"></polyline>
                </svg>
              </template>
            </NButton>
          </template>
          {{ $t('page.chat.expandKnowledgePanel') }}
        </NTooltip>
      </div>

      <!-- 居中布局（空状态） -->
      <Transition name="layout-slide">
        <div
          v-if="isCenteredLayout"
          key="centered"
          class="h-full flex flex-col items-center justify-center bg-white dark:bg-dark-200"
          style="transform: translateY(-60px)"
        >
          <!-- 提示语 -->
          <div class="mb-12">
            <p class="text-2xl text-gray-400 font-semibold dark:text-gray-500">
              {{ $t('page.chat.startNewConversation') }}
            </p>
          </div>

          <!-- 居中输入区 -->
          <div class="max-w-4xl w-full px-8">
            <div class="flex items-end gap-3">
              <div class="relative flex-1">
                <NInput
                  v-model:value="inputText"
                  type="textarea"
                  :placeholder="$t('page.chat.inputPlaceholder')"
                  :autosize="{ minRows: 2, maxRows: 6 }"
                  class="!rounded-2xl !text-base"
                  @keydown="handleKeyDown"
                />
              </div>
              <div class="flex gap-2">
                <NButton circle quaternary class="!text-gray-400 hover:!text-gray-600">
                  <template #icon>
                    <div class="i-icon-park-outline:refresh text-lg" />
                  </template>
                </NButton>
                <NButton
                  circle
                  type="primary"
                  size="medium"
                  :disabled="!inputText.trim() || sending || configSaving || loading || !messagesReady"
                  :loading="sending"
                  class="!bg-blue-500 hover:!bg-blue-600"
                  @click="handleSend"
                >
                  <template #icon>
                    <svg
                      xmlns="http://www.w3.org/2000/svg"
                      width="20"
                      height="20"
                      viewBox="0 0 24 24"
                      fill="none"
                      stroke="currentColor"
                      stroke-width="2"
                      stroke-linecap="round"
                      stroke-linejoin="round"
                    >
                      <path d="m22 2-7 20-4-9-9-4Z" />
                      <path d="M22 2 11 13" />
                    </svg>
                  </template>
                </NButton>
              </div>
            </div>
          </div>
        </div>

        <!-- 正常布局（有消息时） -->
        <div v-else key="normal" class="h-full flex flex-col bg-white dark:bg-dark-200">
          <!-- 聊天头部 -->
          <div
            class="h-14 flex flex-shrink-0 items-center border-b border-gray-200 bg-white px-6 dark:border-gray-700 dark:bg-dark-200"
          >
            <h2
              class="m-0 text-base text-gray-800 font-medium transition-all duration-300 dark:text-gray-200"
              :class="[sidebarCollapsed ? 'ml-10' : '']"
            >
              {{ currentConversation?.title || $t('page.chat.aiAssistant') }}
            </h2>
          </div>

          <!-- 消息列表区 -->
          <div class="flex-1 overflow-hidden bg-white dark:bg-dark-200">
            <NScrollbar ref="scrollRef" class="h-full">
              <div v-if="loading" class="flex justify-center py-10">
                <NSpin size="large" />
              </div>

              <div v-else class="max-w-full flex flex-col gap-6 px-6 py-4">
                <div
                  v-for="msg in messageList"
                  :key="msg.id"
                  class="flex gap-3"
                  :class="msg.role === 1 ? 'flex-row-reverse' : ''"
                >
                  <ChatMessageAvatar :role="msg.role" />

                  <!-- 消息气泡 -->
                  <div
                    class="max-w-[calc(100%-120px)] overflow-hidden rounded-2xl px-4 py-2.5"
                    :class="msg.role === 1 ? 'bg-blue-500 text-white' : 'bg-gray-100 dark:bg-dark-400'"
                  >
                    <!-- AI正在思考 -->
                    <div
                      v-if="msg.role === 2 && msg.thinking"
                      class="flex items-center gap-1 text-gray-500 dark:text-gray-400"
                    >
                      <span class="thinking-dot"></span>
                      <span class="thinking-dot"></span>
                      <span class="thinking-dot"></span>
                    </div>
                    <!-- 显示消息内容 -->
                    <div
                      v-else-if="msg.content"
                      class="ai-message-content text-sm leading-relaxed"
                      v-html="renderMessageMarkdown(msg.content)"
                    ></div>
                  </div>
                </div>
              </div>
            </NScrollbar>
          </div>

          <!-- 输入区 -->
          <div class="flex-shrink-0 border-t border-gray-200 bg-white p-4 dark:border-gray-700 dark:bg-dark-200">
            <div class="mx-auto max-w-full">
              <div class="flex items-end gap-3">
                <div class="relative flex-1">
                  <NInput
                    v-model:value="inputText"
                    type="textarea"
                    :placeholder="$t('page.chat.inputPlaceholder')"
                    :autosize="{ minRows: 1, maxRows: 5 }"
                    class="!rounded-2xl"
                    @keydown="handleKeyDown"
                  />
                </div>
                <div class="flex gap-2">
                  <NButton circle quaternary class="!text-gray-400 hover:!text-gray-600">
                    <template #icon>
                      <div class="i-icon-park-outline:refresh text-lg" />
                    </template>
                  </NButton>
                  <NButton
                    circle
                    type="primary"
                    size="medium"
                    :disabled="!inputText.trim() || sending || configSaving || loading || !messagesReady"
                    :loading="sending"
                    class="!bg-blue-500 hover:!bg-blue-600"
                    @click="handleSend"
                  >
                    <template #icon>
                      <svg
                        xmlns="http://www.w3.org/2000/svg"
                        width="20"
                        height="20"
                        viewBox="0 0 24 24"
                        fill="none"
                        stroke="currentColor"
                        stroke-width="2"
                        stroke-linecap="round"
                        stroke-linejoin="round"
                      >
                        <path d="m22 2-7 20-4-9-9-4Z" />
                        <path d="M22 2 11 13" />
                      </svg>
                    </template>
                  </NButton>
                </div>
              </div>
            </div>
          </div>
        </div>
      </Transition>
    </div>

    <!-- 右侧：知识库 RAG 选择 -->
    <div
      v-show="!knowledgePanelCollapsed"
      class="relative h-full flex flex-col flex-shrink-0 border-l border-gray-200 bg-gray-50 dark:border-gray-700 dark:bg-dark-100"
      :class="{ 'transition-all duration-300': !isResizingKnowledgePanel }"
      :style="{ width: `${knowledgePanelWidth}px` }"
    >
      <div
        class="sidebar-resize-handle sidebar-resize-handle--left"
        :class="{ 'sidebar-resize-handle--active': isResizingKnowledgePanel }"
        @mousedown="startKnowledgePanelResize"
      />

      <div class="flex items-start justify-between gap-2 border-b border-gray-200 p-3 dark:border-gray-700">
        <div class="min-w-0">
          <h3 class="m-0 text-sm text-gray-800 font-semibold dark:text-gray-200">
            {{ $t('page.chat.knowledgePanelTitle') }}
          </h3>
          <p class="mb-0 mt-1 text-xs text-gray-400 dark:text-gray-500">
            {{ configFrozen ? $t('page.chat.knowledgeConfigFrozen') : $t('page.chat.knowledgePanelDesc') }}
          </p>
        </div>
        <NTooltip placement="bottom">
          <template #trigger>
            <NButton quaternary circle class="!text-gray-500 hover:!text-gray-700" @click="toggleKnowledgePanel">
              <template #icon>
                <svg
                  xmlns="http://www.w3.org/2000/svg"
                  width="18"
                  height="18"
                  viewBox="0 0 24 24"
                  fill="none"
                  stroke="currentColor"
                  stroke-width="2"
                  stroke-linecap="round"
                  stroke-linejoin="round"
                >
                  <polyline points="13 17 18 12 13 7"></polyline>
                  <polyline points="6 17 11 12 6 7"></polyline>
                </svg>
              </template>
            </NButton>
          </template>
          {{ $t('page.chat.collapseKnowledgePanel') }}
        </NTooltip>
      </div>

      <NScrollbar class="flex-1">
        <div class="px-2 py-2">
          <div
            class="mb-2 border border-gray-200 rounded-lg bg-white px-3 py-2.5 dark:border-gray-700 dark:bg-dark-200/40"
          >
            <div class="flex items-start justify-between gap-2">
              <div class="min-w-0 flex-1">
                <div class="text-sm text-gray-700 font-medium dark:text-gray-300">
                  {{ $t('page.chat.systemKnowledgeTitle') }}
                </div>
                <div class="mt-1 text-xs text-gray-400 dark:text-gray-500">
                  {{ $t('page.chat.systemKnowledgeDesc') }}
                </div>
              </div>
              <NSwitch
                :value="useSystemKnowledge"
                :disabled="configFrozen || configSaving || loading || sending"
                size="small"
                @update:value="toggleSystemKnowledge"
              />
            </div>
          </div>

          <NRadioGroup
            :value="documentRangeMode"
            :disabled="configFrozen || configSaving || loading || sending"
            class="mb-3 px-3"
            aria-label="用户文档范围"
            @update:value="changeDocumentRange"
          >
            <NSpace vertical>
              <NRadio value="all">全部文档</NRadio>
              <NRadio value="selected">指定文档</NRadio>
              <NRadio value="none">不使用</NRadio>
            </NSpace>
          </NRadioGroup>
          <p v-if="documentRangeMode === 'selected'" class="px-3 text-xs text-gray-500">请选择文档，跨页选择会保留</p>

          <div v-if="knowledgeLoading" class="flex justify-center py-8">
            <NSpin size="small" />
          </div>

          <div
            v-else-if="knowledgeDocuments.length === 0"
            class="px-3 py-6 text-center text-xs text-gray-400 leading-relaxed dark:text-gray-500"
          >
            {{ $t('page.chat.knowledgeEmpty') }}
          </div>

          <template v-else>
            <div
              v-for="item in knowledgeDocuments"
              :key="item.id"
              class="mb-1 border border-transparent rounded-lg px-3 py-2.5 transition-colors hover:bg-gray-100 dark:hover:bg-gray-800/50"
            >
              <div class="flex items-start justify-between gap-2">
                <div class="min-w-0 flex-1">
                  <div class="truncate text-sm text-gray-700 font-medium dark:text-gray-300" :title="item.title">
                    {{ item.title }}
                  </div>
                  <div class="mt-1 text-xs text-gray-400 dark:text-gray-500">
                    <span v-if="knowledgeEmbeddingReady(item)">{{ item.embeddingStatus }}</span>
                    <span v-else class="text-amber-600 dark:text-amber-400">
                      {{ $t('page.chat.knowledgeNotEmbedded') }}
                    </span>
                  </div>
                </div>
                <NSwitch
                  :value="isDocumentEnabled(item.id)"
                  :disabled="
                    configFrozen ||
                    configSaving ||
                    documentRangeMode !== 'selected' ||
                    !knowledgeEmbeddingReady(item) ||
                    item.id === null ||
                    item.id === undefined
                  "
                  size="small"
                  @update:value="
                    (enabled: boolean) => item.id !== null && item.id !== undefined && toggleDocument(item.id, enabled)
                  "
                />
              </div>
            </div>
          </template>
        </div>
      </NScrollbar>
      <NPagination
        :page="knowledgePage"
        :page-size="pageSize"
        :item-count="knowledgeTotal"
        :disabled="knowledgeLoading"
        simple
        class="justify-center p-3"
        aria-label="知识文档分页"
        @update:page="page => loadKnowledgeDocuments(page)"
      />
    </div>
  </div>
</template>

<style scoped>
.sidebar-resize-handle {
  position: absolute;
  top: 0;
  right: -3px;
  z-index: 10;
  width: 6px;
  height: 100%;
  cursor: col-resize;
  touch-action: none;
}

.sidebar-resize-handle::after {
  content: '';
  position: absolute;
  top: 0;
  left: 2px;
  width: 2px;
  height: 100%;
  border-radius: 1px;
  background-color: transparent;
  transition: background-color 0.2s ease;
}

.sidebar-resize-handle:hover::after,
.sidebar-resize-handle--active::after {
  background-color: rgb(59 130 246 / 0.55);
}

.dark .sidebar-resize-handle:hover::after,
.dark .sidebar-resize-handle--active::after {
  background-color: rgb(96 165 250 / 0.65);
}

.sidebar-resize-handle--left {
  right: auto;
  left: -3px;
}

.sidebar-resize-handle--left::after {
  left: auto;
  right: 2px;
}

/* 新对话按钮内容居中 */
:deep(.n-button__content) {
  justify-content: center !important;
}

/* 布局切换滑动动画 */
.layout-slide-enter-active {
  transition: all 0.7s cubic-bezier(0.25, 0.46, 0.45, 0.94);
}

.layout-slide-leave-active {
  transition: all 0.3s ease-in;
}

.layout-slide-enter-from {
  opacity: 0;
  transform: translateY(30px);
}

.layout-slide-leave-to {
  opacity: 0;
  transform: translateY(-40px);
}

.layout-slide-enter-to,
.layout-slide-leave-from {
  opacity: 1;
  transform: translateY(0);
}

/* 思考动效 */
.thinking-dot {
  width: 8px;
  height: 8px;
  background-color: currentColor;
  border-radius: 50%;
  animation: thinking 1.4s infinite ease-in-out both;
}

.thinking-dot:nth-child(1) {
  animation-delay: -0.32s;
}

.thinking-dot:nth-child(2) {
  animation-delay: -0.16s;
}

@keyframes thinking {
  0%,
  80%,
  100% {
    transform: scale(0.6);
    opacity: 0.5;
  }
  40% {
    transform: scale(1);
    opacity: 1;
  }
}

/* AI消息内容样式 */
.ai-message-content {
  word-break: break-word;
  line-height: 1.6;
  color: #333;
}

.dark .ai-message-content {
  color: #e5e5e5;
}

/* 用户消息保持原样 */
.bg-blue-500 .ai-message-content {
  color: white;
}

/* Markdown样式 */
.ai-message-content :deep(h1),
.ai-message-content :deep(h2),
.ai-message-content :deep(h3),
.ai-message-content :deep(h4),
.ai-message-content :deep(h5),
.ai-message-content :deep(h6) {
  margin-top: 0.75em;
  margin-bottom: 0.5em;
  font-weight: 600;
  line-height: 1.25;
}

.ai-message-content :deep(h1) {
  font-size: 1.5em;
}
.ai-message-content :deep(h2) {
  font-size: 1.3em;
}
.ai-message-content :deep(h3) {
  font-size: 1.1em;
}

.ai-message-content :deep(p) {
  margin-bottom: 0.75em;
}

.ai-message-content :deep(ul),
.ai-message-content :deep(ol) {
  margin-bottom: 0.75em;
  padding-left: 1.5em;
}

.ai-message-content :deep(li) {
  margin-bottom: 0.25em;
}

.ai-message-content :deep(code) {
  background-color: rgba(0, 0, 0, 0.06);
  padding: 0.2em 0.4em;
  border-radius: 3px;
  font-family: 'Courier New', monospace;
  font-size: 0.9em;
}

.dark .ai-message-content :deep(code) {
  background-color: rgba(255, 255, 255, 0.1);
}

.ai-message-content :deep(pre) {
  background-color: #1e1e1e;
  border-radius: 8px;
  padding: 1em;
  overflow-x: auto;
  margin-bottom: 1em;
}

.ai-message-content :deep(pre code) {
  background-color: transparent;
  padding: 0;
  color: #d4d4d4;
}

.ai-message-content :deep(blockquote) {
  border-left: 4px solid #ddd;
  padding-left: 1em;
  margin: 0.75em 0;
  color: #666;
}

.dark .ai-message-content :deep(blockquote) {
  border-left-color: #555;
  color: #aaa;
}

.ai-message-content :deep(table) {
  border-collapse: collapse;
  width: 100%;
  margin-bottom: 1em;
}

.ai-message-content :deep(th),
.ai-message-content :deep(td) {
  border: 1px solid #ddd;
  padding: 0.5em;
}

.dark .ai-message-content :deep(th),
.dark .ai-message-content :deep(td) {
  border-color: #444;
}

.ai-message-content :deep(th) {
  background-color: #f5f5f5;
  font-weight: 600;
}

.dark .ai-message-content :deep(th) {
  background-color: #333;
}

.ai-message-content :deep(a) {
  color: #1890ff;
  text-decoration: none;
}

.ai-message-content :deep(a:hover) {
  text-decoration: underline;
}

.ai-message-content :deep(img) {
  max-width: 100%;
  height: auto;
  border-radius: 4px;
  margin: 0.5em 0;
}

.ai-message-content :deep(hr) {
  border: none;
  border-top: 1px solid #ddd;
  margin: 1.5em 0;
}

.dark .ai-message-content :deep(hr) {
  border-top-color: #444;
}
</style>
