package com.zdmj.resumeService.controller;

import com.zdmj.common.model.CreateGroup;
import com.zdmj.common.model.Result;
import com.zdmj.common.model.UpdateGroup;
import com.zdmj.resumeService.dto.CareerRequest;
import com.zdmj.resumeService.dto.CareerResponse;
import com.zdmj.resumeService.service.CareerService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/career")
@Tag(name = "工作经历", description = "工作/实习经历的增删改查")
public class CareerController {
    private final CareerService careerService;

    @PostMapping
    public Result<CareerResponse> addCareer(@Validated(CreateGroup.class) @RequestBody CareerRequest careerRequest) {
        return Result.success("添加工作经历成功", careerService.create(careerRequest));
    }

    @PutMapping
    public Result<CareerResponse> updateCareer(
            @Validated(UpdateGroup.class) @RequestBody CareerRequest careerRequest) {
        return Result.success("更新工作经历成功", careerService.update(careerRequest));
    }

    @DeleteMapping("/{id}")
    public Result<Void> deleteCareer(@PathVariable Long id) {
        careerService.delete(id);
        return Result.success("删除工作经历成功", null);
    }

    @GetMapping("/{id}")
    public Result<CareerResponse> getCareerById(@PathVariable Long id) {
        return Result.success("查询工作经历成功", careerService.getById(id));
    }

    @GetMapping
    public Result<List<CareerResponse>> getCareers() {
        return Result.success("查询工作经历成功", careerService.getByUserId());
    }
}
