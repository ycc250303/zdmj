package com.zdmj.userAuthService.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.zdmj.common.model.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("user_llm_config")
public class UserLlmConfig extends BaseEntity {

    @TableId(value = "user_id", type = IdType.INPUT)
    private Long userId;

    private String modelCode;

    private String apiKeyCiphertext;
}