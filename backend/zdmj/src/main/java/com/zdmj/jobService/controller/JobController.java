package com.zdmj.jobService.controller;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.zdmj.common.annotation.RateLimit;
import com.zdmj.common.model.CreateGroup;

import java.util.concurrent.TimeUnit;
import com.zdmj.common.model.PageDTO;
import com.zdmj.common.model.Result;
import com.zdmj.common.model.UpdateGroup;
import com.zdmj.jobService.dto.JobListItemResponse;
import com.zdmj.jobService.dto.JobCapabilityProfileResponse;
import com.zdmj.jobService.dto.JobCareerGraphResponse;
import com.zdmj.jobService.dto.JobRequest;
import com.zdmj.jobService.dto.JobResponse;
import com.zdmj.jobService.dto.JobPageQueryDTO;
import com.zdmj.jobService.enums.JobEmploymentEnum;
import com.zdmj.jobService.service.JobCapabilityProfileService;
import com.zdmj.jobService.service.JobCareerGraphService;
import com.zdmj.jobService.service.JobService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * 岗位信息控制器
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/jobs")
@Tag(name = "岗位管理", description = "岗位信息、能力画像与职业图谱")
public class JobController {

    private final JobService jobService;
    private final JobCapabilityProfileService jobCapabilityProfileService;
    private final JobCareerGraphService jobCareerGraphService;

    /**
     * 查询岗位详情
     * 
     * @param id 岗位ID
     * @return 岗位详情
     */
    @GetMapping("/{id}")
    public Result<JobListItemResponse> getById(@PathVariable Long id) {
        return Result.success("查询岗位成功", jobService.getDetail(id));
    }

    /**
     * 查询岗位列表（查询参数绑定 {@link JobPageQueryDTO}）
     * <p>请求示例：</p>
     * <pre>{@code
     * GET /jobs?page=1&limit=20&companySizes=[1,2,3]&fundingTypes=[1,2]&industries=["互联网","企业服务"]
     *     &companyName=某科技公司&employment=INTERN&salaryType=2&filterSalaryMin=200&filterSalaryMax=500&jobName=后端
     * }</pre>
     */
    @GetMapping
    public Result<PageDTO<JobListItemResponse>> getPage(
            @RequestParam(required = false) String page,
            @RequestParam(required = false) String limit,
            @RequestParam(required = false) String companySizes,
            @RequestParam(required = false) String fundingTypes,
            @RequestParam(required = false) String industries,
            @RequestParam(required = false) String companyName,
            @RequestParam(required = false) String employment,
            @RequestParam(required = false) String salaryType,
            @RequestParam(required = false) String filterSalaryMin,
            @RequestParam(required = false) String filterSalaryMax,
            @RequestParam(required = false) String jobName) {
        JobPageQueryDTO query = new JobPageQueryDTO();
        query.setPage(page);
        query.setLimit(limit);
        query.setCompanySizes(companySizes);
        query.setFundingTypes(fundingTypes);
        query.setIndustries(industries);
        query.setCompanyName(companyName);
        query.setEmployment(JobEmploymentEnum.parse(employment));
        query.setSalaryType(salaryType);
        query.setFilterSalaryMin(filterSalaryMin);
        query.setFilterSalaryMax(filterSalaryMax);
        query.setJobName(jobName);
        return Result.success("查询岗位列表成功", jobService.getPage(query));
    }

    /**
     * 创建岗位
     * 
     * @param dto 岗位DTO
     * @return 创建的岗位
     */
    @PostMapping
    public Result<JobResponse> create(@Validated(CreateGroup.class) @RequestBody JobRequest request) {
        return Result.success("创建岗位成功", jobService.create(request));
    }

    /**
     * 更新岗位
     *
     * @param request 岗位请求
     * @return 更新的岗位
     */
    @PutMapping
    public Result<JobResponse> update(@Validated(UpdateGroup.class) @RequestBody JobRequest request) {
        return Result.success("更新岗位成功", jobService.update(request));
    }

    /**
     * 删除岗位
     *
     * @param id 岗位ID
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        jobService.delete(id);
        return Result.success("删除岗位成功", null);
    }


    /**
     * 查询当前用户对该岗的能力画像（仅查询，不触发生成；未登录或不存在返回 null）
     *
     * @param id 岗位ID
     * @return 岗位能力画像或 null
     */
    @GetMapping("/capability-profile")
    public Result<JobCapabilityProfileResponse> queryJobCapabilityProfileByParam(@RequestParam Long id) {
        return Result.success("查询岗位能力画像成功", jobCapabilityProfileService.getJobCapabilityProfileOrNull(id));
    }

    /**
     * 为当前用户生成岗位能力画像（已有则覆盖本人旧行）
     * 
     * @param id 岗位ID
     * @return 岗位能力画像
     */
    @RateLimit(dimension = RateLimit.Dimension.USER, count = 10, interval = 1, timeUnit = TimeUnit.MINUTES)
    @PostMapping("/{id}/capability-profile")
    public Result<JobCapabilityProfileResponse> getJobCapabilityProfile(@PathVariable Long id) {
        return Result.success("获取岗位能力画像成功", jobCapabilityProfileService.getJobCapabilityProfile(id));
    }

        /**
     * 查询岗位关联图谱（仅查询，不触发 LLM 生成；不存在返回 null）。
     *
     * <p>图谱包含两部分：</p>
     * <ul>
     *     <li>{@code verticalPath} — 垂直岗位图谱（岗位未来发展路径，至少 3 个层级节点）。</li>
     *     <li>{@code transitionPaths} — 换岗路径图谱（至少 5 条，每条 ≥2 个节点）。</li>
     * </ul>
     *
     * @param id 岗位ID
     * @return 岗位关联图谱或 null
     */
        @GetMapping("/{id}/career-graph")
        public Result<JobCareerGraphResponse> queryJobCareerGraph(@PathVariable Long id) {
            return Result.success("查询岗位关联图谱成功", jobCareerGraphService.getOrNull(id));
        }
    
        /**
         * 生成岗位关联图谱（若已有则覆盖重写）。
         *
         * @param id 岗位ID
         * @return 岗位关联图谱
         */
        @RateLimit(dimension = RateLimit.Dimension.USER, count = 5, interval = 1, timeUnit = TimeUnit.MINUTES)
        @PostMapping("/{id}/career-graph")
        public Result<JobCareerGraphResponse> generateJobCareerGraph(@PathVariable Long id) {
            return Result.success("生成岗位关联图谱成功", jobCareerGraphService.generate(id));
        }
}
