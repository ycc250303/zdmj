package com.zdmj.knowledgeService.service.impl;

import com.zdmj.common.context.CurrentActor;
import com.zdmj.common.exception.ErrorCode;
import com.zdmj.common.exception.BusinessException;
import com.zdmj.knowledgeService.dto.KnowledgeBasesResponse;
import com.zdmj.knowledgeService.entity.KnowledgeBases;
import com.zdmj.knowledgeService.mapper.KnowledgeBasesMapper;
import com.zdmj.knowledgeService.service.KnowledgeBasesService;

import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Service
public class KnowledgeBasesServiceImpl implements KnowledgeBasesService {

    private final KnowledgeBasesMapper knowledgeBasesMapper;
    private final Clock clock;

    /** 短事务内确保当前用户存在唯一私有库，首次和重复创建均返回该知识库。 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public KnowledgeBasesResponse create(CurrentActor actor) {
        getOrCreateKnowledgeBaseId(actor);
        return convertToResponse(requireUserKnowledgeBase(actor));
    }

    @Override
    public KnowledgeBasesResponse getByUserId(CurrentActor actor) {
        return convertToResponse(requireUserKnowledgeBase(actor));
    }

    @Override
    public Long findKnowledgeBaseIdByScope(int scope) {
        return knowledgeBasesMapper.selectKnowledgeIdByScope(scope);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long getOrCreateKnowledgeBaseId(CurrentActor actor) {
        Long userId = actor.userId();
        Long id = knowledgeBasesMapper.selectKnowledgeIdByUserId(userId);
        if (id != null) {
            return id;
        }
        int inserted = knowledgeBasesMapper.insertUserBaseIfAbsent(userId, LocalDateTime.now(clock));
        if (inserted < 0 || inserted > 1) {
            throw new IllegalStateException("知识库插入数量必须为0或1，rows=" + inserted);
        }
        Long existing = knowledgeBasesMapper.selectKnowledgeIdByUserId(userId);
        if (existing == null) {
            throw new IllegalStateException("用户知识库未成功建立");
        }
        return existing;
    }

    private KnowledgeBases requireUserKnowledgeBase(CurrentActor actor) {
        KnowledgeBases knowledgeBases = knowledgeBasesMapper.selectByUserId(actor.userId());
        if (knowledgeBases == null) {
            throw new BusinessException(ErrorCode.KNOWLEDGE_BASE_NOT_FOUND);
        }
        return knowledgeBases;
    }

    private KnowledgeBasesResponse convertToResponse(KnowledgeBases knowledgeBases) {
        return new KnowledgeBasesResponse(knowledgeBases.getId(), knowledgeBases.getUserId(),
                knowledgeBases.getScope());
    }
}
