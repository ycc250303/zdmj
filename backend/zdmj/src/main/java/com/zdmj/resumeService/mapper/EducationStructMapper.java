package com.zdmj.resumeService.mapper;

import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

import com.zdmj.resumeService.dto.EducationDTO;
import com.zdmj.resumeService.entity.Education;

@Mapper(componentModel = "spring")
public interface EducationStructMapper {

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "userId", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "degreeEnum", ignore = true)
    void updateEntityFromDto(EducationDTO dto, @MappingTarget Education entity);
}

