package com.zdmj.knowledgeService.mapper;

import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

import com.zdmj.knowledgeService.dto.KnowledgeBasesDTO;
import com.zdmj.knowledgeService.entity.KnowledgeBases;

@Mapper(componentModel = "spring")
public interface KnowledgeBasesStructMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "userId", ignore = true)
    @Mapping(target = "vectorIds", ignore = true)
    @Mapping(target = "vectorTaskId", ignore = true)
    @Mapping(target = "vectorTaskStatus", ignore = true)
    @Mapping(target = "typeEnum", ignore = true)
    KnowledgeBases fromDto(KnowledgeBasesDTO dto);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "userId", ignore = true)
    @Mapping(target = "projectId", ignore = true)
    @Mapping(target = "vectorIds", ignore = true)
    @Mapping(target = "vectorTaskId", ignore = true)
    @Mapping(target = "vectorTaskStatus", ignore = true)
    @Mapping(target = "typeEnum", ignore = true)
    void updateEntityFromDto(KnowledgeBasesDTO dto, @MappingTarget KnowledgeBases entity);
}

