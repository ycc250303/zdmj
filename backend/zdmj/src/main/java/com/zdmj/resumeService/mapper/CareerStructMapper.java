package com.zdmj.resumeService.mapper;

import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

import com.zdmj.resumeService.dto.CareerDTO;
import com.zdmj.resumeService.entity.Career;

@Mapper(componentModel = "spring")
public interface CareerStructMapper {

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "userId", ignore = true)
    void updateEntityFromDto(CareerDTO dto, @MappingTarget Career entity);
}