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
