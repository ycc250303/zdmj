package com.zdmj.jobService.controller;

import org.springframework.beans.PropertyAccessException;
import org.springframework.core.ResolvableType;
import org.springframework.validation.BindingResult;
import org.springframework.validation.DefaultBindingErrorProcessor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.zdmj.common.exception.BusinessException;
import com.zdmj.common.model.CreateGroup;

import java.util.List;
import com.zdmj.common.model.PageDTO;
import com.zdmj.common.model.Result;
import com.zdmj.common.model.UpdateGroup;
import com.zdmj.jobService.dto.JobListItemResponse;
import com.zdmj.jobService.dto.JobRequest;
import com.zdmj.jobService.dto.JobResponse;
import com.zdmj.jobService.dto.JobPageQueryDTO;
import com.zdmj.jobService.enums.JobEmploymentEnum;
import com.zdmj.jobService.service.JobService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * 岗位信息控制器
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/jobs")
@Tag(name = "岗位管理", description = "岗位信息")
public class JobController {

    private final JobService jobService;

    /**
     * 查询参数绑定岗位列表条件。{@code resolvedSalaryType} 与 {@code fullTimeEmployment} 由服务计算，不接受请求传入。
     * 查询字段不合法时抛出参数校验错误。
     *
     * @param binder 当前请求的数据绑定器
     */
    @InitBinder
    public void initJobPageQueryBinder(WebDataBinder binder) {
        if (!isJobPageQuery(binder)) {
            return;
        }
        binder.setDisallowedFields("resolvedSalaryType", "fullTimeEmployment");
        binder.registerCustomEditor(Integer.class, "page", JobPageQueryDTO.integerEditor("page"));
        binder.registerCustomEditor(Integer.class, "limit", JobPageQueryDTO.integerEditor("limit"));
        binder.registerCustomEditor(Integer.class, "salaryType", JobPageQueryDTO.integerEditor("salaryType"));
        binder.registerCustomEditor(Integer.class, "filterSalaryMin", JobPageQueryDTO.integerEditor("filterSalaryMin"));
        binder.registerCustomEditor(Integer.class, "filterSalaryMax", JobPageQueryDTO.integerEditor("filterSalaryMax"));
        binder.registerCustomEditor(List.class, "companySizes", JobPageQueryDTO.listEditor("companySizes", true));
        binder.registerCustomEditor(List.class, "fundingTypes", JobPageQueryDTO.listEditor("fundingTypes", true));
        binder.registerCustomEditor(List.class, "industries", JobPageQueryDTO.listEditor("industries", false));
        binder.registerCustomEditor(JobEmploymentEnum.class, "employment", JobPageQueryDTO.employmentEditor());
        binder.setBindingErrorProcessor(new DefaultBindingErrorProcessor() {
            @Override
            public void processPropertyAccessException(PropertyAccessException ex, BindingResult bindingResult) {
                if (ex.getCause() instanceof BusinessException businessException) {
                    throw businessException;
                }
                super.processPropertyAccessException(ex, bindingResult);
            }
        });
    }

    /**
     * 按已创建对象或声明类型判断当前绑定是否为岗位列表查询。
     *
     * @param binder 当前请求的数据绑定器
     * @return 当前绑定目标是岗位列表查询条件
     */
    private static boolean isJobPageQuery(WebDataBinder binder) {
        if (binder.getTarget() instanceof JobPageQueryDTO) {
            return true;
        }
        ResolvableType targetType = binder.getTargetType();
        return targetType != null && targetType.resolve() == JobPageQueryDTO.class;
    }

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
    public Result<PageDTO<JobListItemResponse>> getPage(JobPageQueryDTO query) {
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
}
