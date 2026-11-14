package com.zdmj.knowledgeService.service.impl;

import com.zdmj.common.context.CurrentActor;
import com.zdmj.common.exception.ErrorCode;
import com.zdmj.common.exception.BusinessException;
import com.zdmj.knowledgeService.dto.KnowledgeBasesResponse;
import com.zdmj.knowledgeService.entity.KnowledgeBases;
import com.zdmj.knowledgeService.mapper.KnowledgeBasesMapper;
import com.zdmj.knowledgeService.service.KnowledgeBasesService;

import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
@Service
public class KnowledgeBasesServiceImpl implements KnowledgeBasesService {

    private final KnowledgeBasesMapper knowledgeBasesMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public KnowledgeBasesResponse create(CurrentActor actor) {
        // 1.创建知识库
        Long userId = actor.userId();
        log.info("用户 {} 创建知识库: {}", userId);

        KnowledgeBases knowledgeBases = new KnowledgeBases();
        knowledgeBases.setUserId(userId);
        knowledgeBases.setScope(1);

        // 2.保存知识库
        if (knowledgeBasesMapper.insert(knowledgeBases) != 1 || knowledgeBases.getId() == null) {
            throw new IllegalStateException("知识库插入结果无效");
        }

        return convertToResponse(knowledgeBases);
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
        knowledgeBasesMapper.insertUserBaseIfAbsent(userId);
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
        KnowledgeBasesResponse response = new KnowledgeBasesResponse();
        BeanUtils.copyProperties(knowledgeBases, response);
        return response;
    }
}
