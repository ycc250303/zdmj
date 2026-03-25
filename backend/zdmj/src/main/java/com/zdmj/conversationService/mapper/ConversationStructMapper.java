package com.zdmj.conversationService.mapper;

import com.zdmj.conversationService.dto.ConversationDTO;
import com.zdmj.conversationService.entity.Conversation;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

/**
 * 会话 DTO/Entity 映射器（MapStruct）
 */
@Mapper(componentModel = "spring")
public interface ConversationStructMapper {

    ConversationDTO toDto(Conversation entity);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "userId", ignore = true)
    @Mapping(target = "messageCount", ignore = true)
    @Mapping(target = "lastMessageAt", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Conversation fromDto(ConversationDTO dto);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "userId", ignore = true)
    @Mapping(target = "messageCount", ignore = true)
    @Mapping(target = "lastMessageAt", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateEntityFromDto(ConversationDTO dto, @MappingTarget Conversation entity);
}
