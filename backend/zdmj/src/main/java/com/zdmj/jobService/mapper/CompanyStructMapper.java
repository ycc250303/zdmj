package com.zdmj.jobService.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.zdmj.jobService.dto.JobRequest;
import com.zdmj.jobService.entity.Company;
import com.zdmj.jobService.enums.CompanySizeEnum;

@Mapper(componentModel = "spring", imports = CompanySizeEnum.class)
public interface CompanyStructMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "name", source = "companyName")
    @Mapping(target = "size", expression = "java(dto.companySize() == null ? CompanySizeEnum.BELOW_20.getCode() : dto.companySize())")
    @Mapping(target = "type", source = "companyFundingType")
    @Mapping(target = "industries", expression = "java(dto.companyIndustries() == null ? new java.util.ArrayList<>() : dto.companyIndustries())")
    @Mapping(target = "introduction", source = "companyIntroduction")
    Company fromJobDto(JobRequest dto);
}
