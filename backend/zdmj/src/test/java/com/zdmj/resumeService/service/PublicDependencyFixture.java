package com.zdmj.resumeService.service;

/** 验证公开接口和公共身份允许被业务模块引用的字节码样例。 */
public class PublicDependencyFixture {
    com.zdmj.jobService.api.JobReader reader;
    com.zdmj.common.context.CurrentActor actor;
}
