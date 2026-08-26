package com.zdmj.resumeService.controller;

import com.zdmj.common.model.CreateGroup;
import com.zdmj.common.model.Result;
import com.zdmj.common.model.UpdateGroup;
import com.zdmj.resumeService.dto.AwardRequest;
import com.zdmj.resumeService.dto.AwardResponse;
import com.zdmj.resumeService.service.AwardService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/awards")
@Tag(name = "获奖信息", description = "获奖信息的增删改查")
public class AwardController {

    private final AwardService awardService;

    @PostMapping
    public Result<AwardResponse> addAward(@Validated(CreateGroup.class) @RequestBody AwardRequest awardRequest) {
        return Result.success("添加获奖信息成功", awardService.create(awardRequest));
    }

    @PutMapping
    public Result<AwardResponse> updateAward(@Validated(UpdateGroup.class) @RequestBody AwardRequest awardRequest) {
        return Result.success("更新获奖信息成功", awardService.update(awardRequest));
    }

    @DeleteMapping("/{id}")
    public Result<Void> deleteAward(@PathVariable Long id) {
        awardService.delete(id);
        return Result.success("删除获奖信息成功", null);
    }

    @GetMapping("/{id}")
    public Result<AwardResponse> getAwardById(@PathVariable Long id) {
        return Result.success("查询获奖信息成功", awardService.getById(id));
    }

    @GetMapping
    public Result<List<AwardResponse>> getAwards() {
        return Result.success("查询获奖信息成功", awardService.getByUserId());
    }
}
