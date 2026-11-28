package com.zdmj.resumeService.service;

/** 验证跨模块内部依赖被架构检查拒绝的字节码样例。 */
public class ForeignInternalDependencyFixture {
    com.zdmj.jobService.dto.JobRequest request;
    com.zdmj.jobService.service.JobService service;
    com.zdmj.jobService.mapper.JobMapper mapper;
    com.zdmj.jobService.entity.Job entity;
    com.zdmj.jobService.service.impl.JobServiceImpl implementation;
    com.zdmj.jobService.enums.JobEmploymentEnum enumeration;
}
