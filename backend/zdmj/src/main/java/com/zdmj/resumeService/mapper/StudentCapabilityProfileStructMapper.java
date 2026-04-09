package com.zdmj.resumeService.mapper;

import com.zdmj.resumeService.dto.StudentCapabilityProfileDTO;
import com.zdmj.resumeService.entity.StudentCapabilityProfile;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

/**
 * 画像实体类与 DTO 转换
 */
@Mapper(componentModel = "spring")
public interface StudentCapabilityProfileStructMapper {
    StudentCapabilityProfileStructMapper INSTANCE = Mappers.getMapper(StudentCapabilityProfileStructMapper.class);

    StudentCapabilityProfileDTO toDTO(StudentCapabilityProfile entity);

    StudentCapabilityProfile toEntity(StudentCapabilityProfileDTO dto);
}
