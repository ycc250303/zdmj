<script setup lang="ts">
import { ref, onMounted, nextTick } from 'vue';
import { useMessage, useDialog } from 'naive-ui';
import {
  fetchGetConversations,
  fetchCreateConversation,
  fetchChatStream,
  fetchDeleteConversation,
  ConversationApi
} from '@/service/api/conversation';

const message = useMessage();
const dialog = useDialog();

// 状态定义
const conversations = ref<ConversationApi.Conversation[]>([]);
const currentConversationId = ref<number | null>(null);
const messageList = ref<ConversationApi.Message[]>([]);
const inputText = ref('');
const loading = ref(false);
const sending = ref(false);
const scrollRef = ref<any>(null);

// 获取会话列表
const loadConversations = async () => {
  const { data, error } = await fetchGetConversations();
  if (!error && data) {
    conversations.value = data;
  }
};

// 新建会话
const handleNewChat = async () => {
  const { data, error } = await fetchCreateConversation({ config: {}, context: [] });
  if (!error && data) {
    await loadConversations();
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
  // 不再加载历史消息，因为后端接口有问题
  // 直接显示空消息列表，允许用户发送新消息
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
    if (!error && data) {
      currentConversationId.value = data.id;
      await loadConversations();
    } else {
      message.error('创建会话失败');
      return;
    }
  }

  const userText = inputText.value.trim();
  inputText.value = '';
  
  // 添加用户消息到列表
  const tempUserMsg: ConversationApi.Message = {
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
  const tempAiMsg: ConversationApi.Message = {
    id: Date.now() + 1,
    conversationId: currentConversationId.value!,
    userId: 0,
    role: 2, // 1=user, 2=assistant
    sequence: 0,
    content: '',
    createdAt: new Date().toISOString()
  };
  messageList.value.push(tempAiMsg);

  const requestData = { conversationId: currentConversationId.value!, message: userText };
  console.log('发送消息请求:', requestData);

  await fetchChatStream(
    requestData,
    (text) => {
      const lastMsg = messageList.value[messageList.value.length - 1];
      if (lastMsg && lastMsg.role === 2) { // 2=assistant
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

onMounted(() => {
  loadConversations();
});
</script>

<template>
  <div class="h-full flex gap-4 p-4 box-border">
    <!-- 侧边栏：会话列表 -->
    <n-card class="h-full w-280px flex-shrink-0" content-class="p-0 flex flex-col h-full">
      <div class="p-4 border-b border-gray-200 dark:border-gray-800">
        <n-button type="primary" block @click="handleNewChat">
          <template #icon>
            <div class="i-icon-park-outline:plus" />
          </template>
          新对话
        </n-button>
      </div>
      
      <n-scrollbar class="flex-1">
        <n-list hoverable clickable class="!bg-transparent">
          <n-list-item 
            v-for="item in conversations" 
            :key="item.id"
            :class="['cursor-pointer transition-colors px-4 py-3', currentConversationId === item.id ? 'bg-primary-50 dark:bg-primary-900/30' : '']"
            @click="handleSelectConversation(item.id)"
          >
            <div class="flex justify-between items-center group">
              <div class="flex flex-col gap-1 overflow-hidden">
                <span class="text-sm font-medium truncate">{{ item.title || '新对话' }}</span>
                <span class="text-xs text-gray-400">{{ formatTime(item.updatedAt) }}</span>
              </div>
              <n-button 
                quaternary 
                circle 
                type="error" 
                size="small"
                class="opacity-0 group-hover:opacity-100 transition-opacity"
                @click="(e) => handleDeleteConversation(item.id, e)"
              >
                <template #icon>
                  <div class="i-icon-park-outline:delete" />
                </template>
              </n-button>
            </div>
          </n-list-item>
        </n-list>
      </n-scrollbar>
    </n-card>

    <!-- 主区域：对话详情 -->
    <n-card class="h-full flex-1" content-class="p-0 flex flex-col h-full">
      <!-- 聊天头部 -->
      <div class="h-14 border-b border-gray-200 dark:border-gray-800 flex items-center px-6">
        <h2 class="text-lg font-medium m-0">
          {{ conversations.find(c => c.id === currentConversationId)?.title || 'AI 助手' }}
        </h2>
      </div>

      <!-- 消息列表区 -->
      <div class="flex-1 overflow-hidden bg-gray-50 dark:bg-dark-200">
        <n-scrollbar ref="scrollRef" class="h-full p-6">
          <div v-if="loading" class="flex justify-center py-10">
            <n-spin size="large" />
          </div>
          
          <div v-else-if="messageList.length === 0" class="flex flex-col items-center justify-center h-full text-gray-400 gap-4">
            <div class="i-icon-park-outline:message-emoji text-6xl opacity-50" />
            <p>开始一段新的对话吧</p>
          </div>

          <div v-else class="flex flex-col gap-6 max-w-4xl mx-auto">
            <div
              v-for="msg in messageList"
              :key="msg.id"
              class="flex gap-4"
              :class="msg.role === 1 ? 'flex-row-reverse' : ''"
            >
              <!-- 头像 -->
              <n-avatar
                round
                :size="40"
                :class="msg.role === 2 ? 'bg-primary-100 text-primary-600' : 'bg-gray-200'"
              >
                <template v-if="msg.role === 2">
                  <div class="i-icon-park-outline:robot text-xl" />
                </template>
                <template v-else>
                  <div class="i-icon-park-outline:user text-xl" />
                </template>
              </n-avatar>

              <!-- 消息气泡 -->
              <div
                class="max-w-[75%] rounded-2xl px-5 py-3 whitespace-pre-wrap leading-relaxed shadow-sm"
                :class="msg.role === 1
                  ? 'bg-primary text-white rounded-tr-sm'
                  : 'bg-white dark:bg-dark-400 rounded-tl-sm border border-gray-100 dark:border-gray-800'"
              >
                <span v-if="msg.content">{{ msg.content }}</span>
                <span v-else-if="msg.role === 2 && sending" class="flex items-center gap-1">
                  <span class="i-icon-park-outline:more text-xl animate-pulse" />
                </span>
              </div>
            </div>
          </div>
        </n-scrollbar>
      </div>

      <!-- 输入区 -->
      <div class="p-4 border-t border-gray-200 dark:border-gray-800 bg-white dark:bg-dark-300">
        <div class="max-w-4xl mx-auto flex gap-3 items-end">
          <n-input
            v-model:value="inputText"
            type="textarea"
            placeholder="输入消息，Enter 发送，Shift + Enter 换行..."
            :autosize="{ minRows: 1, maxRows: 5 }"
            class="flex-1 !rounded-xl"
            @keydown="handleKeyDown"
          />
          <n-button 
            type="primary" 
            class="mb-1 !rounded-xl !h-9" 
            :disabled="!inputText.trim() || sending"
            :loading="sending"
            @click="handleSend"
          >
            <template #icon>
              <div class="i-icon-park-outline:send" />
            </template>
            发送
          </n-button>
        </div>
      </div>
    </n-card>
  </div>
</template>

<style scoped>
/* 可选的 Markdown 样式可在此补充 */
</style>
