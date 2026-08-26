package com.zdmj.jobService.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.zdmj.jobService.dto.JobCareerGraphResponse;
import com.zdmj.jobService.entity.JobCareerGraph;

/**
 * 岗位关联图谱服务。
 *
 * <p>负责「垂直岗位图谱（晋升路径）」与「换岗路径图谱」的生成与查询，
 * 对应开发要求：</p>
 * <ul>
 *     <li>垂直岗位图谱：涵盖岗位描述、岗位晋升路径关联信息，至少 3 个层级节点。</li>
 *     <li>换岗路径图谱：将相关岗位进行血缘关系关联，每个岗位提供 ≥5 条路径，每条 ≥2 节点。</li>
 * </ul>
 *
 * <p><b>存储层</b>：落地到关系型数据库表 {@code job_career_graphs}（一个岗位最多一条记录），
 * 与岗位能力画像 {@link JobCapabilityProfileService} 保持一致的风格；JSONB 列由 Service 层
 * Jackson 序列化/反序列化。</p>
 */
public interface JobCareerGraphService extends IService<JobCareerGraph> {

    /**
     * 仅查询岗位关联图谱；若 DB 中不存在返回 {@code null}，不会触发 LLM 生成。
     *
     * @param jobId 岗位ID
     * @return 图谱 DTO 或 {@code null}
     */
    JobCareerGraphResponse getOrNull(Long jobId);

    /**
     * 生成或重新生成岗位关联图谱（已有则覆盖写入 DB）。
     *
     * @param jobId 岗位ID
     * @return 图谱 DTO
     */
    JobCareerGraphResponse generate(Long jobId);
}
