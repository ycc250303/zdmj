package com.zdmj.aiService.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.zdmj.common.model.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 用户模型配置，对应 user_llm_config 表，每名用户保存一条记录。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("user_llm_config")
public class UserLlmConfig extends BaseEntity {

    /** 配置所属用户编号，对应 user_id 主键，逻辑关联 users.id。 */
    @TableId(value = "user_id", type = IdType.INPUT)
    private Long userId;

    /** 用户选择的模型目录编号，对应非空列 model_code。 */
    private String modelCode;

    /** 供应商密钥的加密文本，对应非空列 api_key_ciphertext。 */
    @lombok.ToString.Exclude
    private String apiKeyCiphertext;
}
