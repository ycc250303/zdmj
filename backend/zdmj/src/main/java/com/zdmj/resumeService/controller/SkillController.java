package com.zdmj.resumeService.controller;

import lombok.extern.slf4j.Slf4j;

import java.util.List;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.zdmj.common.Result;
import com.zdmj.common.validation.CreateGroup;
import com.zdmj.common.validation.UpdateGroup;
import com.zdmj.resumeService.dto.SkillDTO;
import com.zdmj.resumeService.entity.Skill;
import com.zdmj.resumeService.service.SkillService;

/**
 * 技能控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/skills")
public class SkillController {
    private final SkillService skillService;

    public SkillController(SkillService skillService) {
        this.skillService = skillService;
    }

    /**
     * 添加技能
     * 
     * @param skillDTO 技能DTO
     * @return 技能
     */
    @PostMapping
    public Result<Skill> addSkill(@Validated(CreateGroup.class) @RequestBody SkillDTO skillDTO) {
        return Result.success("添加技能成功", skillService.create(skillDTO));
    }

    /**
     * 更新技能
     * 
     * @param skillDTO 技能DTO
     * @return 技能
     */
    @PutMapping
    public Result<Skill> updateSkill(@Validated(UpdateGroup.class) @RequestBody SkillDTO skillDTO) {
        return Result.success("更新技能成功", skillService.update(skillDTO));
    }

    /**
     * 删除技能
     * 
     * @param id 技能ID
     * @return 删除结果
     */
    @DeleteMapping("/{id}")
    public Result<String> deleteSkill(@PathVariable Long id) {
        skillService.delete(id);
        return Result.success("删除技能成功", null);
    }

    /**
     * 根据ID查询技能
     * 
     * @param id 技能ID
     * @return 技能
     */
    @GetMapping("/{id}")
    public Result<Skill> getSkillById(@PathVariable Long id) {
        return Result.success("查询技能成功", skillService.getById(id));
    }

    /**
     * 查询所有技能
     * 
     * @return 技能列表
     */
    @GetMapping
    public Result<List<Skill>> getSkills() {
        return Result.success("查询技能成功", skillService.getByUserId());
    }
}
