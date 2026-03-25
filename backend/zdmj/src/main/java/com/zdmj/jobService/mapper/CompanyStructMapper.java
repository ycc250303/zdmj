package com.zdmj.jobService.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.zdmj.jobService.dto.JobDTO;
import com.zdmj.jobService.entity.Company;
import com.zdmj.jobService.enums.CompanySizeEnum;

@Mapper(componentModel = "spring", imports = CompanySizeEnum.class)
public interface CompanyStructMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "name", source = "companyName")
    @Mapping(target = "size", expression = "java(dto.getCompanySize() == null ? CompanySizeEnum.BELOW_20.getCode() : dto.getCompanySize())")
    @Mapping(target = "type", source = "companyFundingType")
    @Mapping(target = "industries", expression = "java(dto.getCompanyIndustries() == null ? new java.util.ArrayList<>() : dto.getCompanyIndustries())")
    @Mapping(target = "introduction", source = "companyIntroduction")
    @Mapping(target = "sizeEnum", ignore = true)
    @Mapping(target = "typeEnum", ignore = true)
    Company fromJobDto(JobDTO dto);
}

