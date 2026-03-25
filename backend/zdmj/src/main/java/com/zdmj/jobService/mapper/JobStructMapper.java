package com.zdmj.jobService.mapper;

import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

import com.zdmj.jobService.dto.JobDTO;
import com.zdmj.jobService.entity.Job;

@Mapper(componentModel = "spring")
public interface JobStructMapper {

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "companyId", ignore = true)
    @Mapping(target = "companyName", ignore = true)
    @Mapping(target = "recall", ignore = true)
    void patchFromDto(JobDTO dto, @MappingTarget Job job);
}

