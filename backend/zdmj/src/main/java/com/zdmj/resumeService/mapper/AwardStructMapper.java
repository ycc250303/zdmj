package com.zdmj.resumeService.mapper;

import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

import com.zdmj.resumeService.dto.AwardRequest;
import com.zdmj.resumeService.entity.Award;

@Mapper(componentModel = "spring")
public interface AwardStructMapper {

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "userId", ignore = true)
    void updateEntityFromDto(AwardRequest dto, @MappingTarget Award entity);
}
