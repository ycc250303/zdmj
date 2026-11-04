package com.zdmj.aiService.api;

import java.util.List;

import com.zdmj.common.context.CurrentActor;
import com.zdmj.common.exception.BusinessException;
import com.zdmj.common.exception.ErrorCode;

/**
 * 平台嵌入入口。向量使用 {@code spring.ai.openai} 的默认嵌入配置，不使用用户对话密钥。
 */
public interface EmbeddingGateway {

    /**
     * 按文本顺序计算向量。
     *
     * @param actor 当前操作者，用户主键为空时抛出 {@link ErrorCode#USER_NOT_LOGIN}
     * @param texts 待嵌入文本，空列表或空白项抛出 {@link IllegalArgumentException}
     * @return 与 {@code texts} 顺序一致的向量
     * @throws BusinessException        用户未登录
     * @throws IllegalArgumentException 文本列表为空或含空白项
     * @throws IllegalStateException    嵌入结果数量与文本数量不一致
     */
    List<float[]> embed(CurrentActor actor, List<String> texts);
}
