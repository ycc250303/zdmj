package com.zdmj.common.async;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.zdmj.common.model.Result;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * 异步 LLM 任务查询。生成类 POST 仍在各域 Controller。
 */
@RequiredArgsConstructor
@RestController
@RequestMapping("/async-tasks")
@Validated
@Tag(name = "异步任务", description = "查询 Redis Stream 异步任务状态")
public class AsyncTaskController {

    private final AsyncTaskService asyncTaskService;

    /**
     * 查询当前用户的任务；他人或已删视为不存在（13001）。
     *
     * @param id {@code async_llm_tasks.id}
     */
    @GetMapping("/{id}")
    public Result<AsyncTaskDTO> getById(@PathVariable Long id) {
        return Result.success("查询异步任务成功", asyncTaskService.getById(id));
    }
}
