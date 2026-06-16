package com.zdmj.knowledgeService.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.zdmj.common.context.UserHolder;
import com.zdmj.common.exception.ErrorCode;
import com.zdmj.common.exception.BusinessException;
import com.zdmj.knowledgeService.entity.KnowledgeBases;
import com.zdmj.knowledgeService.mapper.KnowledgeBasesMapper;
import com.zdmj.knowledgeService.mapper.KnowledgeDocumentMapper;
import com.zdmj.knowledgeService.mapper.KnowledgeVectorMapper;
import com.zdmj.knowledgeService.mapper.KnowledgeVectorTaskMapper;
import com.zdmj.knowledgeService.service.KnowledgeBasesService;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
@Service
public class KnowledgeBasesServiceImpl extends ServiceImpl<KnowledgeBasesMapper, KnowledgeBases>
        implements KnowledgeBasesService {

    private final KnowledgeBasesMapper knowledgeBasesMapper;
    private final KnowledgeDocumentMapper knowledgeDocumentMapper;
    private final KnowledgeVectorTaskMapper knowledgeVectorTaskMapper;
    private final KnowledgeVectorMapper knowledgeVectorMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public KnowledgeBases create() {
        // 1.创建知识库
        Long userId = UserHolder.requireUserId();
        log.info("用户 {} 创建知识库: {}", userId);

        KnowledgeBases knowledgeBases = new KnowledgeBases();
        knowledgeBases.setUserId(userId);
        knowledgeBases.setScope(1);

        // 2.保存知识库
        boolean saved = save(knowledgeBases);
        if (!saved) {
            throw new BusinessException(ErrorCode.KNOWLEDGE_BASE_SAVE_FAILED);
        }

        return knowledgeBases;
    }

    @Override
    public KnowledgeBases getByUserId() {
        KnowledgeBases knowledgeBases = knowledgeBasesMapper.selectByUserId(UserHolder.requireUserId());
        if (knowledgeBases == null) {
            throw new BusinessException(ErrorCode.KNOWLEDGE_BASE_NOT_FOUND);
        }
        return knowledgeBases;
    }

    @Override
    public Long findKnowledgeBaseIdByScope(int scope) {
        return knowledgeBasesMapper.selectKnowledgeIdByScope(scope);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long getOrCreateKnowledgeBaseId() {
        Long userId = UserHolder.requireUserId();
        Long id = knowledgeBasesMapper.selectKnowledgeIdByUserId(userId);
        if (id != null) {
            return id;
        }
        KnowledgeBases knowledgeBases = new KnowledgeBases();
        knowledgeBases.setUserId(userId);
        knowledgeBases.setScope(1);
        try {
            boolean saved = save(knowledgeBases);
            if (!saved) {
                throw new BusinessException(ErrorCode.KNOWLEDGE_BASE_SAVE_FAILED);
            }
            return knowledgeBases.getId();
        } catch (DataIntegrityViolationException e) {
            Long existing = knowledgeBasesMapper.selectKnowledgeIdByUserId(userId);
            if (existing != null) {
                return existing;
            }
            throw new BusinessException(ErrorCode.KNOWLEDGE_BASE_SAVE_FAILED);
        }
    }

    // @Override
    // @Transactional(rollbackFor = Exception.class)
    // public void update() {

    // }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void clear() {
        KnowledgeBases knowledgeBases = getByUserId();
        Long knowledgeId = knowledgeBases.getId();
        knowledgeVectorMapper.deleteByKnowledgeId(knowledgeId);
        knowledgeVectorTaskMapper.deleteByKnowledgeId(knowledgeId);
        knowledgeDocumentMapper.deleteByKnowledgeId(knowledgeId);
        log.info("清空知识库成功，knowledgeId={}", knowledgeId);
    }
}
