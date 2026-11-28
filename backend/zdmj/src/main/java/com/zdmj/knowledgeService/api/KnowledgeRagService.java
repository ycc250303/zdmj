package com.zdmj.knowledgeService.api;

import com.zdmj.common.context.CurrentActor;
import com.zdmj.aiService.api.ModelRequest;
import java.util.List;
import reactor.core.publisher.Flux;

/**
 * 知识模块公开检索、问答及报告引用入口，统一资源范围和可返回字段。
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
     * 检索本人私有库和学习路线库，合并后保留最多八条并应用分类偏好。
     * @param actor 已认证操作者
     * @param query 报告构造的非空检索文本
     * @return 已授权命中，无命中时为空集合；远程与数据库异常立即传播
     */
    List<KnowledgeHit> retrieveLearningPaths(CurrentActor actor, String query);

    /**
     * 批量校验报告来源，保留生成片段和得分并重新生成可返回的引用。
     * @param actor 已认证操作者
     * @param snapshots 非空引用组成的历史快照集合，允许空集合
     * @return 按原顺序返回可见引用；非法快照和数据库异常立即传播
     */
    List<KnowledgeReference> resolveReportReferences(CurrentActor actor, List<KnowledgeReference> snapshots);

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
