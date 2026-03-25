package com.zdmj.resumeService.controller;

import lombok.extern.slf4j.Slf4j;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.*;

import com.zdmj.common.Result;
import com.zdmj.common.validation.CreateGroup;
import com.zdmj.common.validation.UpdateGroup;
import com.zdmj.resumeService.dto.CareerDTO;
import com.zdmj.resumeService.entity.Career;
import com.zdmj.resumeService.service.CareerService;

import java.util.List;
import lombok.RequiredArgsConstructor;

/**
 * 工作(实习)经历控制器
 */
@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/career")
public class CareerController {
    private final CareerService careerService;

    /**
     * 添加工作经历
     * 
     * @param careerDTO 工作经历DTO
     * @return 工作经历
     */
    @PostMapping
    public Result<Career> addCareer(@Validated(CreateGroup.class) @RequestBody CareerDTO careerDTO) {
        return Result.success("添加工作经历成功", careerService.create(careerDTO));
    }

    /**
     * 更新工作经历
     * 
     * @param careerDTO 工作经历DTO
     * @return 工作经历
     */
    @PutMapping
    public Result<Career> updateCareer(@Validated(UpdateGroup.class) @RequestBody CareerDTO careerDTO) {
        return Result.success("更新工作经历成功", careerService.update(careerDTO));
    }

    /**
     * 删除工作经历
     * 
     * @param id 工作经历ID
     * @return 删除结果
     */
    @DeleteMapping("/{id}")
    public Result<String> deleteCareer(@PathVariable Long id) {
        careerService.delete(id);
        return Result.success("删除工作经历成功", null);
    }

    /**
     * 根据ID查询工作经历
     * 
     * @param id 工作经历ID
     * @return 工作经历
     */
    @GetMapping("/{id}")
    public Result<Career> getCareerById(@PathVariable Long id) {
        return Result.success("查询工作经历成功", careerService.getById(id));
    }

    /**
     * 查询所有工作经历
     * 
     * @return 工作经历列表
     */
    @GetMapping
    public Result<List<Career>> getCareers() {
        return Result.success("查询工作经历成功", careerService.getByUserId());
    }
}
