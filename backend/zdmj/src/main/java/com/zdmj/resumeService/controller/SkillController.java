package com.zdmj.resumeService.controller;

import com.zdmj.common.model.CreateGroup;
import com.zdmj.common.model.Result;
import com.zdmj.common.model.UpdateGroup;
import com.zdmj.resumeService.dto.SkillRequest;
import com.zdmj.resumeService.dto.SkillResponse;
import com.zdmj.resumeService.service.SkillService;
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
@RequestMapping("/skills")
@Tag(name = "技能管理", description = "简历技能的增删改查")
public class SkillController {
    private final SkillService skillService;

    @PostMapping
    public Result<SkillResponse> addSkill(@Validated(CreateGroup.class) @RequestBody SkillRequest skillRequest) {
        return Result.success("添加技能成功", skillService.create(skillRequest));
    }

    @PutMapping
    public Result<SkillResponse> updateSkill(@Validated(UpdateGroup.class) @RequestBody SkillRequest skillRequest) {
        return Result.success("更新技能成功", skillService.update(skillRequest));
    }

    @DeleteMapping("/{id}")
    public Result<Void> deleteSkill(@PathVariable Long id) {
        skillService.delete(id);
        return Result.success("删除技能成功", null);
    }

    @GetMapping("/{id}")
    public Result<SkillResponse> getSkillById(@PathVariable Long id) {
        return Result.success("查询技能成功", skillService.getById(id));
    }

    @GetMapping
    public Result<List<SkillResponse>> getSkills() {
        return Result.success("查询技能成功", skillService.getByUserId());
    }
}
