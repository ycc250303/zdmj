import { request } from '../request';
import { getAuthorization } from '../request/shared';

/**
 * =====================================================================
 * TypeScript 类型定义区域 (DTOs)
 * =====================================================================
 */
export namespace ConversationApi {
  /** 会话 DTO */
  export interface ConversationDTO {
    id?: number;
    config?: Record<string, any>;
    context?: Array<Record<string, any>>;
  }

  /** 会话实体 */
  export interface Conversation {
    id: number;
    userId: number;
    title: string;
    config: Record<string, any>;
    context: Array<Record<string, any>>;
    messageCount?: number;
    createdAt: string;
    updatedAt: string;
  }

  /** 消息 DTO */
  export interface MessageDTO {
    conversationId: number;
    message: string;
  }

  /** 消息实体 */
  export interface Message {
    id: number;
    conversationId: number;
    userId: number;
    role: number; // 1=user, 2=assistant, 3=system
    content: string;
    sequence: number;
    createdAt: string;
  }

  /** 分页查询参数 */
  export type MessageQueryParams = Api.Common.PageQueryParams & {
    conversationId: number;
  };

  /** 分页结果 */
  export type PageResult<T> = Api.Common.PageDTO<T>;
}

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

/**
 * 发送流式消息 (SSE)
 * 因为 Axios 对 SSE 支持有限，这里提供一个使用 Fetch API 的封装方法
 */
export async function fetchChatStream(
  data: ConversationApi.MessageDTO,
  onMessage: (text: string) => void,
  onError?: (err: any) => void,
  onComplete?: () => void
) {
  const token = getAuthorization();
  // 假定你的 BASE_URL 是 /api 或从环境变量获取
  const baseUrl = import.meta.env.VITE_SERVICE_BASE_URL || '/api';
  const isHttpProxy = import.meta.env.DEV && import.meta.env.VITE_HTTP_PROXY === 'Y';
  // ��里简化处理，直接使用 fetch
  const url = isHttpProxy ? `/proxy-default/messages/chat` : `${baseUrl}/messages/chat`;

  // 构建headers，只有token存在时才添加Authorization
  const headers: Record<string, string> = {
    'Content-Type': 'application/json'
  };
  if (token) {
    headers.Authorization = token;
  }

  console.log('fetchChatStream 发送请求:', {
    url,
    headers,
    body: data
  });

  try {
    const response = await fetch(url, {
      method: 'POST',
      headers,
      body: JSON.stringify(data)
    });

    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`);
    }

    const reader = response.body?.getReader();
    const decoder = new TextDecoder('utf-8');
    let buffer = '';

    if (reader) {
      while (true) {
        const { done, value } = await reader.read();
        if (done) {
          break;
        }
        
        buffer += decoder.decode(value, { stream: true });
        const lines = buffer.split('\n');
        buffer = lines.pop() || ''; // 最后一部分可能不完整，留到下次处理

        for (const line of lines) {
          if (line.startsWith('data:')) {
            const dataStr = line.replace('data:', '').trim();
            if (dataStr && dataStr !== '[DONE]') {
              try {
                // 解析OpenAI格式的SSE数据
                const jsonData = JSON.parse(dataStr);
                // 提取choices[0].delta.content
                const content = jsonData.choices?.[0]?.delta?.content;
                if (content) {
                  onMessage(content);
                }
              } catch (e) {
                // 如果解析失败，直接使用原始数据
                console.error('解析SSE数据失败:', e, dataStr);
                onMessage(dataStr);
              }
            }
          }
        }
      }
    }
    
    if (onComplete) {
      onComplete();
    }
  } catch (error) {
    if (onError) {
      onError(error);
    }
  }
}
