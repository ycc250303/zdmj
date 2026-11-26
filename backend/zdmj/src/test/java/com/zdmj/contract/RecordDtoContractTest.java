package com.zdmj.contract;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.zdmj.common.config.WebMvcConfig;
import com.zdmj.common.exception.GlobalExceptionHandler;
import com.zdmj.contract.support.ContractProbeController;

@WebMvcTest(controllers = ContractProbeController.class, useDefaultFilters = false)
@Import({ContractProbeController.class, GlobalExceptionHandler.class, WebMvcConfig.class})
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("mvc-contract")
class RecordDtoContractTest {

    private static final String BASE = "/api/zdmj/contract/record";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void 登录请求_空白与缺少字段_保留必填校验及错误契约() throws Exception {
        mockMvc.perform(post(BASE + "/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usernameOrEmail\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value(1001))
                .andExpect(jsonPath("$.detail").value(containsString("用户名或邮箱不能为空")))
                .andExpect(jsonPath("$.detail").value(containsString("密码不能为空")));
    }

    @Test
    void 技能创建_名称超限与合法边界_保留长度校验及创建分组() throws Exception {
        mockMvc.perform(post(BASE + "/skill/create").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + "a".repeat(256) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1001))
                .andExpect(jsonPath("$.detail").value(containsString("技能名称长度不能超过255个字符")));
        mockMvc.perform(post(BASE + "/skill/create").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + "a".repeat(255) + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("a".repeat(255)));
    }

    @Test
    void 技能更新_缺少编号与完整输入_执行更新分组() throws Exception {
        mockMvc.perform(post(BASE + "/skill/update").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Java\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("技能ID不能为空")));
        mockMvc.perform(post(BASE + "/skill/update").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":7,\"name\":\"Java\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(7))
                .andExpect(jsonPath("$.data.name").value("Java"));
    }

    @Test
    void 知识文档_表单构造绑定_保留字段与创建校验() throws Exception {
        mockMvc.perform(multipart(BASE + "/document").param("title", "学习笔记").param("type", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("学习笔记"))
                .andExpect(jsonPath("$.data.type").value(1));
        mockMvc.perform(multipart(BASE + "/document").param("title", " ").param("type", "1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1001))
                .andExpect(jsonPath("$.detail").value(containsString("文档标题不能为空")));
    }

    @Test
    void 简历保存_嵌套记录字段超限_继续级联校验() throws Exception {
        mockMvc.perform(post(BASE + "/resume").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"personalInfo\":{\"name\":\"" + "a".repeat(101) + "\"}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1001))
                .andExpect(jsonPath("$.detail").value(containsString("姓名长度不能超过100个字符")));
    }

    @Test
    void 简历保存_省略列表与显式空值_保留默认值和拒绝空值的区别() throws Exception {
        mockMvc.perform(post(BASE + "/resume").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.skills").isEmpty())
                .andExpect(jsonPath("$.data.educations").isEmpty());
        mockMvc.perform(post(BASE + "/resume").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"skills\":null}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("技能列表不能为 null")));
    }
}
