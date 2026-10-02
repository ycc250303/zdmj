package com.zdmj.contract.support;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpMethod;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import com.zdmj.common.async.AsyncTaskDTO;
import com.zdmj.common.async.AsyncTaskStatus;
import com.zdmj.common.async.AsyncTaskType;
import com.zdmj.common.exception.BusinessException;
import com.zdmj.common.exception.ErrorCode;
import com.zdmj.common.model.PageDTO;
import com.zdmj.common.model.Result;
import com.zdmj.resumeService.dto.EducationResponse;
import com.zdmj.resumeService.enums.EducationDegreeEnum;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 契约探针。只在 {@code mvc-contract} profile 下注册，避免进入集成测试的组件扫描。
 */
@Profile("mvc-contract")
@RestController
@RequestMapping("/contract")
public class ContractProbeController {

    public static final String SECRET_FAILURE = "secret-uncaught-detail";

    @GetMapping("/business")
    void business() {
        throw new BusinessException(ErrorCode.RESUME_NOT_FOUND);
    }

    @GetMapping("/limited")
    void limited() {
        throw new BusinessException(ErrorCode.RATE_LIMIT_EXCEEDED);
    }

    @GetMapping("/only-get")
    void onlyGet() {
    }

    @GetMapping("/no-resource")
    void noResource() throws NoResourceFoundException {
        throw new NoResourceFoundException(HttpMethod.GET, "/missing");
    }

    @GetMapping("/uncaught")
    void uncaught() {
        throw new IllegalStateException(SECRET_FAILURE);
    }

    @PostMapping("/validate")
    void validate(@Valid @RequestBody ProbeBody body) {
    }

    @GetMapping("/serialization")
    Result<SerializationSample> serialization() {
        AsyncTaskDTO task = new AsyncTaskDTO();
        task.setTaskId(7L);
        task.setTaskType(AsyncTaskType.RESUME_PARSE.getCode());
        task.setStatus(AsyncTaskStatus.SUCCESS.getCode());
        task.setStartedAt(LocalDateTime.of(2024, 9, 1, 8, 30, 0));
        task.setErrorMessage(null);
        task.setResult(null);
        task.setCompletedAt(null);

        SerializationSample sample = new SerializationSample();
        sample.setTasks(PageDTO.of(List.of(task), 1, 2, 20));
        sample.setEmpty(null);
        return Result.success(sample);
    }

    @GetMapping("/time")
    Result<TimeSample> time() {
        TimeSample sample = new TimeSample();
        sample.setAt(LocalDateTime.of(2024, 9, 1, 8, 30, 0, 123_456_000));
        sample.setWholeSecond(LocalDateTime.of(2024, 9, 1, 8, 30, 0));
        sample.setDay(LocalDate.of(2024, 9, 1));
        return Result.success(sample);
    }

    @GetMapping("/enums")
    Result<EnumSample> enums() {
        EducationResponse legal = new EducationResponse();
        legal.setDegreeEnum(EducationDegreeEnum.BACHELOR);
        EducationResponse empty = new EducationResponse();
        EducationResponse unknown = new EducationResponse();
        unknown.setDegree(99);
        EnumSample sample = new EnumSample();
        sample.setLegal(legal);
        sample.setEmpty(empty);
        sample.setUnknown(unknown);
        return Result.success(sample);
    }

    @Data
    public static class ProbeBody {
        @NotBlank
        private String name;
    }

    @Data
    public static class SerializationSample {
        private PageDTO<AsyncTaskDTO> tasks;
        private String empty;
    }

    @Data
    public static class TimeSample {
        private LocalDateTime at;
        private LocalDateTime wholeSecond;
        private LocalDate day;
    }

    @Data
    public static class EnumSample {
        private EducationResponse legal;
        private EducationResponse empty;
        private EducationResponse unknown;
    }
}
