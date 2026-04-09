<script setup lang="ts">
import { ref, onMounted, nextTick, computed } from 'vue';
import { useMessage, useDialog } from 'naive-ui';
import type { ConversationApi } from '@/service/api/conversation';
import {
  fetchCreateConversation,
  fetchDeleteConversation,
  fetchGetConversations,
  fetchGetMessages,
  fetchChatStream
} from '@/service/api/conversation';
import { marked } from 'marked';
import hljs from 'highlight.js/lib/core';
import bash from 'highlight.js/lib/languages/bash';
import css from 'highlight.js/lib/languages/css';
import java from 'highlight.js/lib/languages/java';
import javascript from 'highlight.js/lib/languages/javascript';
import json from 'highlight.js/lib/languages/json';
import python from 'highlight.js/lib/languages/python';
import sql from 'highlight.js/lib/languages/sql';
import typescript from 'highlight.js/lib/languages/typescript';
import xml from 'highlight.js/lib/languages/xml';
import 'highlight.js/styles/github-dark.css';

defineOptions({
  name: 'Chat'
});

// 注册语言
hljs.registerLanguage('javascript', javascript);
hljs.registerLanguage('typescript', typescript);
hljs.registerLanguage('python', python);
hljs.registerLanguage('java', java);
hljs.registerLanguage('json', json);
hljs.registerLanguage('xml', xml);
hljs.registerLanguage('html', xml);
hljs.registerLanguage('sql', sql);
hljs.registerLanguage('bash', bash);
hljs.registerLanguage('css', css);
hljs.registerLanguage('shell', bash);

// 配置marked - 使用@ts-ignore绕过类型检查
// @ts-ignore
marked.use({
  breaks: true,
  gfm: true
});

// 自定义渲染器以支持代码高亮
// @ts-ignore
const customRenderer = {
  code(code: string, language: string) {
    const validLang = language && hljs.getLanguage(language);
    const lang = validLang ? language : 'plaintext';
    const highlighted = hljs.highlight(code, { language: lang }).value;
    return `<pre><code class="hljs language-${language || 'plaintext'}">${highlighted}</code></pre>`;
  }
};

// 使用自定义渲染器
// @ts-ignore
marked.use({ renderer: customRenderer });

const message = useMessage();
const dialog = useDialog();

// 状态定义
const conversations = ref<ConversationApi.Conversation[]>([]);
const currentConversationId = ref<number | null>(null);
const messageList = ref<(ConversationApi.Message & { thinking?: boolean })[]>([]);
const inputText = ref('');
const loading = ref(false);
const sending = ref(false);
const scrollRef = ref<any>(null);
const sidebarCollapsed = ref(false);

// 切换侧边栏折叠状态
const toggleSidebar = () => {
  sidebarCollapsed.value = !sidebarCollapsed.value;
};

// 获取会话列表
const loadConversations = async () => {
  const { data, error } = await fetchGetConversations();
  if (!error && data) {
    conversations.value = data;
    // 如果有会话且当前没有选中会话，自动选中第一个
    if (data.length > 0 && !currentConversationId.value) {
      handleSelectConversation(data[0].id);
    }
  }
};

// 新��会话
const handleNewChat = async () => {
  const { data, error } = await fetchCreateConversation({ config: {}, context: [] });
  if (!error && data) {
    // 重新加载会话列表
    const { data: convData, error: convError } = await fetchGetConversations();
    if (!convError && convData) {
      conversations.value = convData;
    }
    // 选中新创建的会话
    handleSelectConversation(data.id);
  } else {
    message.error('创建会话失败');
  }
};

// 选择会话
const handleSelectConversation = async (id: number) => {
  // 先验证会话是否存在
  const exists = conversations.value.some(c => c.id === id);
  if (!exists) {
    message.error('该会话不存在');
    currentConversationId.value = null;
    return;
  }

  currentConversationId.value = id;
  messageList.value = [];
  loading.value = true;

  try {
    const { data, error } = await fetchGetMessages({ conversationId: id, page: 1, limit: 100 });
    loading.value = false;
    if (!error && data) {
      // 后端返回的消息是按sequence升序排列的，直接使用
      // 添加thinking字段，默认为false
      messageList.value = (data.list || []).map(msg => ({ ...msg, thinking: false }));
      scrollToBottom();
    } else {
      // 如果加载消息失败，显示空列表，允许用户发送新消息
      console.error('加载消息失败，但允许发送新消息:', error);
    }
  } catch (err) {
    loading.value = false;
    console.error('加载消息异常:', err);
  }
};

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
          currentConversationId.value = null;
          messageList.value = [];
        }
        loadConversations();
      } else {
        message.error('删除失败');
      }
    }
  });
};

// 发送消息
const handleSend = async () => {
  if (!inputText.value.trim() || sending.value) return;

  // 如果没有当前会话，先创建一个
  if (!currentConversationId.value) {
    const { data, error } = await fetchCreateConversation({ config: {}, context: [] });
    if (!error && data && data.id) {
      // 创建成功后，先选中这个新会话，确保所有数据都加载完成
      await handleSelectConversation(data.id);
      return; // 选中会话后会重新触发，所以这里直接返回
    }
    message.error('创建会话失败');
    return;
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
    thinking: true, // 初始状��为思考中
    createdAt: new Date().toISOString()
  };
  messageList.value.push(tempAiMsg);

  const requestData = { conversationId: currentConversationId.value!, message: userText };
  console.log('发送消息请求:', requestData);

  let hasReceivedContent = false;

  await fetchChatStream(
    requestData,
    (text) => {
      const lastMsg = messageList.value[messageList.value.length - 1] as any;
      if (lastMsg && lastMsg.role === 2) { // 2=assistant
        // 第一次收到内容时，结束思考状态
        if (!hasReceivedContent && lastMsg.thinking) {
          lastMsg.thinking = false;
          hasReceivedContent = true;
        }
        lastMsg.content += text;
      }
      scrollToBottom();
    },
    (error) => {
      console.error('发送消息详细错误:', error);
      message.error(`发送消息失败: ${error.message || '未知错误'}`);
      sending.value = false;
      // 移��临时的AI消息气泡
      messageList.value.pop();
    },
    () => {
      sending.value = false;
      // 可以在这里刷新一下会话列表以更新标题
      loadConversations();
    }
  );
};

const handleKeyDown = (e: KeyboardEvent) => {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault();
    handleSend();
  }
};

const scrollToBottom = () => {
  nextTick(() => {
    if (scrollRef.value) {
      scrollRef.value.scrollTo({ position: 'bottom', behavior: 'smooth' });
    }
  });
};

// 格式化日期
const formatTime = (timeStr: string) => {
  if (!timeStr) return '';
  const date = new Date(timeStr);
  return `${date.getMonth() + 1}-${date.getDate()} ${date.getHours().toString().padStart(2, '0')}:${date.getMinutes().toString().padStart(2, '0')}`;
};

// 格式化消息内容 - 渲染Markdown
const formatMessageContent = (content: string) => {
  if (!content) return '';

  try {
    // 使用marked渲染markdown
    return marked.parse(content) as string;
  } catch (error) {
    console.error('Markdown解析错误:', error);
    return content;
  }
};

onMounted(() => {
  loadConversations();
});
</script>

<template>
  <div class="h-screen flex gap-0 box-border bg-white dark:bg-dark-200 overflow-hidden">
    <!-- 侧边栏：会话列表 -->
    <div
      v-show="!sidebarCollapsed"
      class="h-full w-260px flex-shrink-0 flex flex-col bg-gray-50 dark:bg-dark-100 border-r border-gray-200 dark:border-gray-700 transition-all duration-300"
    >
      <!-- 新对话按钮和折叠按钮 -->
      <div class="p-3 flex gap-2">
        <n-button class="flex-1 !rounded-lg !bg-white dark:!bg-gray-800 !text-gray-700 dark:!text-gray-300 !border !border-blue-200 dark:!border-blue-800 hover:!bg-blue-50 dark:hover:!bg-gray-700 hover:!border-blue-300 dark:hover:!border-blue-600 hover:shadow-md hover:!text-blue-600 dark:hover:!text-blue-400 transition-all duration-200 [&_.n-button__content]:!justify-center" @click="handleNewChat">
          <template #icon>
            <div class="i-icon-park-outline:plus !text-blue-500 dark:!text-blue-400 mr-1.5" />
          </template>
          {{ $t('page.chat.newChat') }}
        </n-button>
        <n-tooltip placement="bottom">
          <template #trigger>
            <n-button quaternary circle @click="toggleSidebar" class="!text-gray-500 hover:!text-gray-700">
              <template #icon>
                <svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                  <polyline points="11 17 6 12 11 7"></polyline>
                  <polyline points="18 17 13 12 18 7"></polyline>
                </svg>
              </template>
            </n-button>
          </template>
          {{ $t('page.chat.collapseChatList') }}
        </n-tooltip>
      </div>

      <!-- 对话列表 -->
      <n-scrollbar class="flex-1">
        <div class="px-2">
          <div
            v-for="item in conversations"
            :key="item.id"
            :class="[
              'cursor-pointer transition-all duration-200 px-3 py-2.5 rounded-lg mb-1 group',
              currentConversationId === item.id
                ? 'bg-blue-50 dark:bg-blue-900/20'
                : 'hover:bg-gray-100 dark:hover:bg-gray-800/50'
            ]"
          >
            <div class="flex justify-between items-center gap-2 w-full">
              <div
                class="flex flex-col gap-0.5 flex-1 min-w-0 pr-2"
                @click="handleSelectConversation(item.id)"
              >
                <span class="text-sm font-medium truncate" :class="currentConversationId === item.id ? 'text-blue-700 dark:text-blue-300' : 'text-gray-700 dark:text-gray-300'">
                  {{ item.title || $t('page.chat.newChat') }}
                </span>
                <span class="text-xs text-gray-400 dark:text-gray-500">{{ formatTime(item.updatedAt) }}</span>
              </div>
              <div
                class="flex-shrink-0 w-8 h-8 flex items-center justify-center text-gray-500 cursor-pointer hover:text-blue-700 dark:hover:text-blue-300 hover:bg-gray-100 dark:hover:bg-gray-800/50 rounded transition-colors"
                @click="(e) => handleDeleteConversation(item.id, e)"
              >
                <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                  <path d="M3 6h18"></path>
                  <path d="M19 6v14c0 1-1 2-2 2H7c-1 0-2-1-2-2V6"></path>
                  <path d="M8 6V4c0-1 1-2 2-2h4c1 0 2 1 2 2v2"></path>
                </svg>
              </div>
            </div>
          </div>
        </div>
      </n-scrollbar>
    </div>

    <!-- 主区域：对话详情 -->
    <div class="h-full flex-1 flex flex-col bg-white dark:bg-dark-200 relative">
      <!-- 侧边栏切换按钮（当侧边栏隐藏时显示） -->
      <div v-if="sidebarCollapsed" class="absolute left-2 top-2 z-10">
        <n-tooltip placement="bottom">
          <template #trigger>
            <n-button quaternary circle @click="toggleSidebar" class="!text-gray-500 hover:!text-gray-700">
              <template #icon>
                <svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                  <polyline points="13 17 18 12 13 7"></polyline>
                  <polyline points="6 17 11 12 6 7"></polyline>
                </svg>
              </template>
            </n-button>
          </template>
          {{ $t('page.chat.expandChatList') }}
        </n-tooltip>
      </div>

      <!-- 聊天头部 -->
      <div class="h-14 border-b border-gray-200 dark:border-gray-700 flex items-center px-6 bg-white dark:bg-dark-200 flex-shrink-0">
        <h2 :class="[
          'text-base font-medium m-0 text-gray-800 dark:text-gray-200 transition-all duration-300',
          sidebarCollapsed ? 'ml-10' : ''
        ]">
          {{ conversations.find(c => c.id === currentConversationId)?.title || $t('page.chat.aiAssistant') }}
        </h2>
      </div>

      <!-- 消息列表区 -->
      <div class="flex-1 overflow-hidden bg-white dark:bg-dark-200">
        <n-scrollbar ref="scrollRef" class="h-full">
          <div v-if="loading" class="flex justify-center py-10">
            <n-spin size="large" />
          </div>

          <div v-else-if="messageList.length === 0" class="flex flex-col items-center justify-start h-full text-gray-400 pt-32">
            <p class="text-2xl font-semibold">{{ $t('page.chat.startNewConversation') }}</p>
          </div>

          <div v-else class="flex flex-col gap-6 px-6 py-4 max-w-full">
            <div
              v-for="msg in messageList"
              :key="msg.id"
              class="flex gap-3"
              :class="msg.role === 1 ? 'flex-row-reverse' : ''"
            >
              <!-- 头像 -->
              <n-avatar
                round
                :size="32"
                :class="msg.role === 2 ? 'bg-blue-100 text-blue-600' : 'bg-gray-200'"
              >
                <template v-if="msg.role === 2">
                  <div class="i-icon-park-outline:robot text-base" />
                </template>
                <template v-else>
                  <div class="i-icon-park-outline:user text-base" />
                </template>
              </n-avatar>

              <!-- 消息气泡 -->
              <div
                class="max-w-[calc(100%-120px)] rounded-2xl px-4 py-2.5 overflow-hidden"
                :class="msg.role === 1
                  ? 'bg-blue-500 text-white'
                  : 'bg-gray-100 dark:bg-dark-400'"
              >
                <!-- AI正在思考 -->
                <div v-if="msg.role === 2 && msg.thinking" class="flex items-center gap-1 text-gray-500 dark:text-gray-400">
                  <span class="thinking-dot"></span>
                  <span class="thinking-dot"></span>
                  <span class="thinking-dot"></span>
                </div>
                <!-- 显示消息内容 -->
                <div v-else-if="msg.content" class="ai-message-content leading-relaxed text-sm" v-html="formatMessageContent(msg.content)"></div>
              </div>
            </div>
          </div>
        </n-scrollbar>
      </div>

      <!-- 输入区 -->
      <div class="p-4 border-t border-gray-200 dark:border-gray-700 bg-white dark:bg-dark-200 flex-shrink-0">
        <div class="max-w-full mx-auto">
          <div class="flex gap-3 items-end">
            <div class="flex-1 relative">
              <n-input
                v-model:value="inputText"
                type="textarea"
                :placeholder="$t('page.chat.inputPlaceholder')"
                :autosize="{ minRows: 1, maxRows: 5 }"
                class="!rounded-2xl"
                @keydown="handleKeyDown"
              />
            </div>
            <div class="flex gap-2">
              <n-button
                circle
                quaternary
                class="!text-gray-400 hover:!text-gray-600"
              >
                <template #icon>
                  <div class="i-icon-park-outline:refresh text-lg" />
                </template>
              </n-button>
              <n-button
                circle
                type="primary"
                size="medium"
                :disabled="!inputText.trim() || sending"
                :loading="sending"
                @click="handleSend"
                class="!bg-blue-500 hover:!bg-blue-600"
              >
                <template #icon>
                  <svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                    <path d="m22 2-7 20-4-9-9-4Z"/>
                    <path d="M22 2 11 13"/>
                  </svg>
                </template>
              </n-button>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
/* 新对话按钮内容居中 */
:deep(.n-button__content) {
  justify-content: center !important;
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
  0%, 80%, 100% {
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

.ai-message-content :deep(h1) { font-size: 1.5em; }
.ai-message-content :deep(h2) { font-size: 1.3em; }
.ai-message-content :deep(h3) { font-size: 1.1em; }

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
