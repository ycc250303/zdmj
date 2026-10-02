package com.zdmj.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.zdmj.common.async.AsyncTaskStatus;
import com.zdmj.common.config.WebMvcConfig;
import com.zdmj.common.exception.GlobalExceptionHandler;
import com.zdmj.contract.support.ContractProbeController;
import com.zdmj.conversationService.enums.MessageRoleEnum;
import com.zdmj.resumeService.enums.AwardTypeEnum;
import com.zdmj.resumeService.enums.EducationDegreeEnum;

@WebMvcTest(controllers = ContractProbeController.class, useDefaultFilters = false)
@Import({ContractProbeController.class, GlobalExceptionHandler.class, WebMvcConfig.class})
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("mvc-contract")
class EnumSerializationContractTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void 枚举码_合法非法与空值_解析结果与响应字段一致() throws Exception {
        assertThat(EducationDegreeEnum.fromCode(3)).isEqualTo(EducationDegreeEnum.BACHELOR);
        assertThat(EducationDegreeEnum.fromCode(null)).isNull();
        assertThat(EducationDegreeEnum.fromCode(99)).isNull();
        assertThat(AwardTypeEnum.fromCode(2)).isEqualTo(AwardTypeEnum.COMPETITION);
        assertThat(AwardTypeEnum.fromCode(null)).isNull();
        assertThat(AwardTypeEnum.fromCode(9)).isNull();
        assertThat(MessageRoleEnum.fromCode(1)).isEqualTo(MessageRoleEnum.USER);
        assertThat(MessageRoleEnum.fromCode(null)).isNull();
        assertThat(AsyncTaskStatus.fromCode(AsyncTaskStatus.SUCCESS.getCode())).isEqualTo(AsyncTaskStatus.SUCCESS);
        assertThat(AsyncTaskStatus.fromCode(null)).isNull();
        assertThat(AsyncTaskStatus.fromCode(0)).isNull();

        mockMvc.perform(get("/api/zdmj/contract/enums"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.legal.degree").value(EducationDegreeEnum.BACHELOR.getCode()))
                .andExpect(jsonPath("$.data.legal.degreeEnum").value(EducationDegreeEnum.BACHELOR.name()))
                .andExpect(jsonPath("$.data.empty.degree").value(nullValue()))
                .andExpect(jsonPath("$.data.empty.degreeEnum").value(nullValue()))
                .andExpect(jsonPath("$.data.unknown.degree").value(99))
                .andExpect(jsonPath("$.data.unknown.degreeEnum").value(nullValue()));
    }
}
