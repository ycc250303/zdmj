package com.zdmj.contract.support;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import com.zdmj.common.exception.BusinessException;
import com.zdmj.common.exception.ErrorCode;
import com.zdmj.common.model.CreateGroup;
import com.zdmj.common.model.PageDTO;
import com.zdmj.common.model.Result;
import com.zdmj.common.model.UpdateGroup;
import com.zdmj.conversationService.dto.ConversationResponse;
import com.zdmj.knowledgeService.dto.KnowledgeDocumentRequest;
import com.zdmj.resumeService.dto.EducationResponse;
import com.zdmj.resumeService.dto.ResumeContentSaveRequest;
import com.zdmj.resumeService.dto.SkillRequest;
import com.zdmj.resumeService.enums.EducationDegreeEnum;
import com.zdmj.userAuthService.dto.UserLoginRequest;

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

    @PostMapping("/record/login")
    Result<String> recordLogin(@Valid @RequestBody UserLoginRequest request) {
        return Result.success("操作成功", request.usernameOrEmail());
    }

    @PostMapping("/record/skill/create")
    Result<SkillRequest> recordCreateSkill(
            @Validated(CreateGroup.class)
            @RequestBody SkillRequest request) {
        return Result.success(request);
    }

    @PostMapping("/record/skill/update")
    Result<SkillRequest> recordUpdateSkill(
            @Validated(UpdateGroup.class)
            @RequestBody SkillRequest request) {
        return Result.success(request);
    }

    @PostMapping(value = "/record/document", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    Result<KnowledgeDocumentRequest> recordDocument(
            @Validated(CreateGroup.class)
            @ModelAttribute
            KnowledgeDocumentRequest request) {
        return Result.success(request);
    }

    @PostMapping("/record/resume")
    Result<ResumeContentSaveRequest> recordResume(
            @Valid @RequestBody ResumeContentSaveRequest request) {
        return Result.success(request);
    }

    @GetMapping("/serialization")
    Result<PageDTO<ConversationResponse>> serialization() {
        var conversation = new ConversationResponse(7L, 1L, "求职准备", null, 2,
                LocalDateTime.of(2024, 9, 1, 8, 30, 0));
        return Result.success(PageDTO.of(List.of(conversation), 1, 2, 20));
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
        EducationResponse legal = new EducationResponse(null, null, null, null,
                EducationDegreeEnum.BACHELOR.getCode(), null, null, null);

        EducationResponse empty = new EducationResponse(null, null, null, null, null, null, null, null);
        EducationResponse unknown = new EducationResponse(null, null, null, null, 99, null, null, null);

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
