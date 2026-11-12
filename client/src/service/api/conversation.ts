import { request } from '../request';
import { getAuthorization } from '../request/shared';
import type * as ConversationApi from './conversation.types';

/**
 * =====================================================================
 * TypeScript 类型定义区域 (DTOs)
 * =====================================================================
 */

export type { ConversationApi };

/**
 * =====================================================================
 * API 请求封装区域
 * =====================================================================
 */

/** 创建会话 */
export function fetchCreateConversation(data: ConversationApi.ConversationDTO = {}) {
  return request<ConversationApi.Conversation>({ url: '/conversations', method: 'post', data });
}

/** 查询所有会话列表 */
export function fetchGetConversations() {
  return request<ConversationApi.Conversation[]>({ url: '/conversations', method: 'get' });
}

/** 根据ID查询会话 */
export function fetchGetConversationById(id: number) {
  return request<ConversationApi.Conversation>({ url: `/conversations/${id}`, method: 'get' });
}

/** 修改会话标题 */
export function fetchUpdateConversationTitle(id: number, title: string) {
  return request<ConversationApi.Conversation>({
    url: `/conversations/${id}/title`,
    method: 'put',
    params: { title }
  });
}

/** 更新会话检索配置（仅尚未发出首条消息时） */
export function fetchUpdateConversationConfig(id: number, config: Record<string, any>) {
  return request<ConversationApi.Conversation>({
    url: `/conversations/${id}/config`,
    method: 'put',
    data: config
  });
}

/** 删除会话 */
export function fetchDeleteConversation(id: number) {
  return request<string>({ url: `/conversations/${id}`, method: 'delete' });
}

/** 分页查询会话消息列表 */
export function fetchGetMessages(params: ConversationApi.MessageQueryParams) {
  return request<ConversationApi.PageResult<ConversationApi.Message>>({
    url: '/messages',
    method: 'get',
    params
  });
}

interface ChatStreamHandlers {
  onMessage: (text: string) => void;
  onError?: (error: unknown) => void;
  onComplete?: () => void;
}

function chatStreamUrl() {
  const baseUrl = import.meta.env.VITE_SERVICE_BASE_URL || '/api';
  const useProxy = import.meta.env.DEV && import.meta.env.VITE_HTTP_PROXY === 'Y';
  return useProxy ? '/proxy-default/messages/chat' : `${baseUrl}/messages/chat`;
}

function chatStreamHeaders() {
  const headers: Record<string, string> = {
    'Content-Type': 'application/json'
  };
  const token = getAuthorization();
  if (token) {
    headers.Authorization = token;
  }
  return headers;
}

function emitSseLine(line: string, onMessage: (text: string) => void) {
  if (!line.startsWith('data:')) {
    return;
  }
  const dataStr = line.replace('data:', '').trim();
  if (!dataStr || dataStr === '[DONE]') {
    return;
  }
  try {
    const jsonData = JSON.parse(dataStr);
    const content = jsonData.choices?.[0]?.delta?.content;
    if (content) {
      onMessage(content);
    }
  } catch (error) {
    console.error('解析SSE数据失败:', error, dataStr);
    onMessage(dataStr);
  }
}

function consumeSseBuffer(buffer: string, onMessage: (text: string) => void) {
  const lines = buffer.split('\n');
  const rest = lines.pop() ?? '';
  lines.forEach(line => emitSseLine(line, onMessage));
  return rest;
}

async function readSseBody(reader: ReadableStreamDefaultReader<Uint8Array>, onMessage: (text: string) => void) {
  const decoder = new TextDecoder('utf-8');
  let buffer = '';
  let done = false;

  while (!done) {
    // 流式读取必须按顺序等待下一块，不能并行拉取。
    // eslint-disable-next-line no-await-in-loop
    const chunk = await reader.read();
    done = chunk.done;
    if (!done && chunk.value) {
      buffer = consumeSseBuffer(`${buffer}${decoder.decode(chunk.value, { stream: true })}`, onMessage);
    }
  }
}

/**
 * 发送流式消息。Axios 不能按 SSE 分片回调，这里用 Fetch 读取响应体。
 */
export async function fetchChatStream(data: ConversationApi.MessageDTO, handlers: ChatStreamHandlers) {
  try {
    const response = await fetch(chatStreamUrl(), {
      method: 'POST',
      headers: chatStreamHeaders(),
      body: JSON.stringify(data)
    });
    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`);
    }
    const reader = response.body?.getReader();
    if (reader) {
      await readSseBody(reader, handlers.onMessage);
    }
    handlers.onComplete?.();
  } catch (error) {
    handlers.onError?.(error);
  }
}
