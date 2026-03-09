package com.zdmj.conversationService.service;

import java.util.List;

import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.http.codec.ServerSentEvent;

import com.zdmj.conversationService.entity.Conversations;
import com.zdmj.conversationService.entity.Messages;

import reactor.core.publisher.Flux;

/**
 * AI服务接口
 * 负责处理与AI模型相关的逻辑，包括Prompt构建、流式调用、标题生成等
 */
public interface AIService {

    /**
     * 构建包含历史消息的Prompt
     * 
     * @param conversation       会话对象
     * @param historyMessages    历史消息列表
     * @param currentUserMessage 当前用户消息
     * @return Prompt对象
     */
    Prompt buildPromptWithHistory(Conversations conversation, List<Messages> historyMessages,
            String currentUserMessage);

    /**
     * 流式调用AI生成回复
     * 
     * @param prompt     Prompt对象
     * @param messageId  消息ID（用于构建OpenAI格式JSON）
     * @param model      模型名称
     * @param onChunk    每个chunk的回调函数
     * @param onComplete 完成时的回调函数
     * @param onError    错误时的回调函数
     * @return SSE流式事件
     */
    Flux<ServerSentEvent<String>> streamChat(Prompt prompt, String messageId, String model,
            java.util.function.Consumer<String> onChunk,
            java.util.function.Consumer<String> onComplete,
            java.util.function.Consumer<Throwable> onError);

    /**
     * 非流式调用AI生成回复
     * 
     * @param prompt Prompt对象
     * @return 完整的回复文本
     */
    String callForContent(Prompt prompt);

    /**
     * 构建OpenAI兼容的流式响应JSON格式
     * 用于Apifox等工具自动合并SSE消息
     * 
     * @param messageId 消息ID
     * @param created   创建时间戳（秒）
     * @param model     模型名称
     * @param content   内容chunk
     * @return JSON字符串
     */
    String buildOpenAIChunkJson(String messageId, long created, String model, String content);
}
