package com.zdmj.jobService.mapper;

import com.zdmj.jobService.dto.JobCapabilityProfileDTO;
import com.zdmj.jobService.entity.JobCapabilityProfile;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

/**
 * 岗位能力画像 DTO/Entity 映射器（MapStruct）
 */
@Mapper(componentModel = "spring")
public interface JobCapabilityProfileStructMapper {
    JobCapabilityProfileStructMapper INSTANCE = Mappers.getMapper(JobCapabilityProfileStructMapper.class);

    JobCapabilityProfileDTO toDTO(JobCapabilityProfile entity);

    JobCapabilityProfile toEntity(JobCapabilityProfileDTO dto);
}
