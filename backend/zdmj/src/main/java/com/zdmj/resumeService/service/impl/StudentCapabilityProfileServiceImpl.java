package com.zdmj.resumeService.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zdmj.common.context.UserHolder;
import com.zdmj.common.exception.BusinessException;
import com.zdmj.common.util.PdfParserUtil;
import com.zdmj.common.util.PromptUtil;
import com.zdmj.resumeService.dto.CapabilityProfileGenerateReqDTO;
import com.zdmj.resumeService.dto.StudentCapabilityProfileDTO;
import com.zdmj.resumeService.entity.StudentCapabilityProfile;
import com.zdmj.resumeService.mapper.StudentCapabilityProfileMapper;
import com.zdmj.resumeService.mapper.StudentCapabilityProfileStructMapper;
import com.zdmj.resumeService.service.StudentCapabilityProfileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class StudentCapabilityProfileServiceImpl
        extends ServiceImpl<StudentCapabilityProfileMapper, StudentCapabilityProfile>
        implements StudentCapabilityProfileService {

    private final ChatClient chatClient;
    private final PromptUtil promptUtil;
    private final StudentCapabilityProfileStructMapper structMapper;

    @Override
    public StudentCapabilityProfileDTO getCurrentUserProfile() {
        Long userId = UserHolder.requireUserId();
        StudentCapabilityProfile profile = getOne(
                new LambdaQueryWrapper<StudentCapabilityProfile>()
                        .eq(StudentCapabilityProfile::getUserId, userId));
        if (profile == null) {
            throw new BusinessException(404, "当前用户尚未生成能力画像");
        }
        return structMapper.toDTO(profile);
    }

    @Override
    public StudentCapabilityProfileDTO generateProfile(CapabilityProfileGenerateReqDTO reqDTO) {
        Long userId = UserHolder.requireUserId();
        String sourceText = "";

        // 1. 获取输入源文本
        if (StringUtils.hasText(reqDTO.getPdfUrl())) {
            log.info("从 PDF 解析内容: {}", reqDTO.getPdfUrl());
            try {
                // 如果传入的是 COS 的 Key，则调用 extractTextFromUrl 解析
                sourceText = PdfParserUtil.extractTextFromUrl(reqDTO.getPdfUrl());
            } catch (Exception e) {
                log.error("PDF 解析失败", e);
                throw new BusinessException(400, "PDF 解析失败，请检查文件是否合法");
            }
        } else if (StringUtils.hasText(reqDTO.getRawText())) {
            log.info("从纯文本解析内容");
            sourceText = reqDTO.getRawText();
        } else {
            throw new BusinessException(400, "必须提供 pdfUrl 或 rawText");
        }

        if (!StringUtils.hasText(sourceText)) {
            throw new BusinessException(400, "提取到的文本为空，无法生成画像");
        }

        // 2. 调用大模型进行解析
        log.info("开始调用大模型生成能力画像...");
        StudentCapabilityProfileDTO aiResult;
        try {
            String systemPrompt = promptUtil.load(PromptUtil.PromptNames.GENERATE_CAPABILITY_PROFILE);
            final String text = sourceText;
            String response = chatClient.prompt()
                    .system(systemPrompt)
                    .user(u -> u.text("这是学生的原始信息：\n{text}\n\n请严格按照 JSON 格式返回，包含上述要求的所有字段。")
                            .param("text", text))
                    .call()
                    .content();

            // 清理可能带有的 ```json 标签
            if (response != null && response.startsWith("```json")) {
                response = response.substring(7);
                if (response.endsWith("```")) {
                    response = response.substring(0, response.length() - 3);
                }
            }
            if (response != null && response.startsWith("```")) {
                response = response.substring(3);
                if (response.endsWith("```")) {
                    response = response.substring(0, response.length() - 3);
                }
            }

            ObjectMapper mapper = new ObjectMapper();
            aiResult = mapper.readValue(response, StudentCapabilityProfileDTO.class);
            if (aiResult == null) {
                throw new BusinessException(500, "大模型返回数据格式异常");
            }
        } catch (Exception e) {
            log.error("大模型生成能力画像失败", e);
            throw new BusinessException(500, "大模型生成能力画像失败：" + e.getMessage());
        }

        // 3. 落库保存或更新
        StudentCapabilityProfile existingProfile = getOne(
                new LambdaQueryWrapper<StudentCapabilityProfile>()
                        .eq(StudentCapabilityProfile::getUserId, userId));

        StudentCapabilityProfile newProfile = structMapper.toEntity(aiResult);
        newProfile.setUserId(userId);

        if (existingProfile != null) {
            newProfile.setId(existingProfile.getId());
            updateById(newProfile);
        } else {
            save(newProfile);
        }

        return structMapper.toDTO(newProfile);
    }
}
