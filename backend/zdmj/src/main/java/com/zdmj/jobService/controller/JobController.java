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

import com.zdmj.common.Result;
import com.zdmj.common.model.PageResult;
import com.zdmj.common.validation.CreateGroup;
import com.zdmj.common.validation.UpdateGroup;
import com.zdmj.jobService.dto.JobDetailDTO;
import com.zdmj.jobService.dto.JobListItemDTO;
import com.zdmj.jobService.dto.JobDTO;
import com.zdmj.jobService.entity.Job;
import com.zdmj.jobService.service.JobService;

import java.util.List;

/**
 * 岗位信息控制器
 */
@RestController
@RequestMapping("/jobs")
public class JobController {

    private final JobService jobService;

    public JobController(JobService jobService) {
        this.jobService = jobService;
    }

    /**
     * 查询岗位详情
     * 
     * @param id 岗位ID
     * @return 岗位详情
     */
    @GetMapping("/{id}")
    public Result<JobDetailDTO> getById(@PathVariable Long id) {
        return Result.success("查询岗位成功", jobService.getDetail(id));
    }

    /**
     * 查询岗位列表
     * 
     * @param page         页码
     * @param limit        每页条数
     * @param companySizes 公司规模
     * @param fundingTypes 融资类型
     * @param industries   行业
     * @return 岗位列表
     */
    @GetMapping
    public Result<PageResult<JobListItemDTO>> getPage(
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer limit,
            @RequestParam(required = false) List<Integer> companySizes,
            @RequestParam(required = false) List<Integer> fundingTypes,
            @RequestParam(required = false) List<String> industries) {
        return Result.success("查询岗位列表成功",
                jobService.getPage(page, limit, companySizes, fundingTypes, industries));
    }

    /**
     * 创建岗位
     * 
     * @param dto 岗位DTO
     * @return 创建的岗位
     */
    @PostMapping
    public Result<Job> create(@Validated(CreateGroup.class) @RequestBody JobDTO dto) {
        return Result.success("创建岗位成功", jobService.create(dto));
    }

    /**
     * 更新岗位
     * 
     * @param dto 岗位DTO
     * @return 更新的岗位
     */
    @PutMapping
    public Result<Job> update(@Validated(UpdateGroup.class) @RequestBody JobDTO dto) {
        return Result.success("更新岗位成功", jobService.update(dto));
    }

    /**
     * 删除岗位
     * 
     * @param id 岗位ID
     * @return 删除的岗位
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        jobService.delete(id);
        return Result.success("删除岗位成功", null);
    }
}
