package com.zdmj.knowledgeService.service;

import com.zdmj.common.context.CurrentActor;
import com.zdmj.aiService.api.ModelRequest;
import java.util.List;
import reactor.core.publisher.Flux;

import com.zdmj.knowledgeService.dto.KnowledgeRetrievalResponse;

/**
 * 知识模块内部排序检索入口，检索响应由本模块 Controller 返回。
 */
public interface KnowledgeRagService {

    /**
     * 排序层检索：改写、每句一次 embed、双路 ANN、minScore、合并后截断 topK。
     * 知识问答复用本方法；两库都关时不改写、空 hits。
     *
     * @param actor 已认证操作者，包含用户编号
     */
    KnowledgeRetrievalResponse retrieveRanked(CurrentActor actor, String userMessage, List<Long> ragDocumentIds,
            boolean useSystemKnowledge);

    /**
     * 订阅后检索知识并生成回复，三个检索分支使用同一份历史；取消检索后不再启动回答模型。
     *
     * @param actor              已认证操作者
     * @param conversationId     用于记录调用的会话编号，不用于自动记忆
     * @param request            当前问题、历史快照与提示词变量
     * @param ragDocumentIds     私有文档范围，空列表关闭私有检索，null 表示全部
     * @param useSystemKnowledge 是否检索系统知识库
     * @return 模型增量文本，检索及生成错误立即传播
     */
    Flux<String> streamAnswer(CurrentActor actor, Long conversationId, ModelRequest request,
            List<Long> ragDocumentIds, boolean useSystemKnowledge);
}
