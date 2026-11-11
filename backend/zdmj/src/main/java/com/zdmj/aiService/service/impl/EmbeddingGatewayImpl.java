package com.zdmj.aiService.service.impl;

import java.util.List;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.zdmj.aiService.api.EmbeddingGateway;
import com.zdmj.common.context.CurrentActor;
import com.zdmj.common.exception.BusinessException;
import com.zdmj.common.exception.ErrorCode;

/**
 * {@link EmbeddingGateway} 的实现：校验身份和文本后，委托平台 {@link EmbeddingModel}。
 */
@Service
public class EmbeddingGatewayImpl implements EmbeddingGateway {

    private final EmbeddingModel embeddingModel;

    /**
     * 装配平台嵌入模型。
     *
     * @param embeddingModel Spring AI 自动装配的嵌入模型
     */
    public EmbeddingGatewayImpl(EmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    @Override
    public List<float[]> embed(CurrentActor actor, List<String> texts) {
        requireUserId(actor);
        if (texts == null || texts.isEmpty() || texts.stream().anyMatch(text -> !StringUtils.hasText(text))) {
            throw new IllegalArgumentException("嵌入文本不能为空");
        }
        List<float[]> vectors = embeddingModel.embed(texts);
        if (vectors == null || vectors.size() != texts.size()) {
            throw new IllegalStateException("嵌入结果数量与文本数量不一致");
        }
        return vectors;
    }

    private static Long requireUserId(CurrentActor actor) {
        if (actor == null || actor.userId() == null) {
            throw new BusinessException(ErrorCode.USER_NOT_LOGIN);
        }
        return actor.userId();
    }
}
