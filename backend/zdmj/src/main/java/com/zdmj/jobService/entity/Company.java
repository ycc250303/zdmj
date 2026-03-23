package com.zdmj.jobService.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.zdmj.common.model.BaseEntity;
import com.zdmj.common.typehandler.JsonbListTypeHandler;
import com.zdmj.jobService.enums.CompanyFundingTypeEnum;
import com.zdmj.jobService.enums.CompanySizeEnum;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * 公司表 companies
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("companies")
public class Company extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    @TableField(typeHandler = JsonbListTypeHandler.class)
    private List<String> industries;

    /**
     * 人员规模：1=20人以下 … 7=10000人以上（见 SQL 注释）
     */
    private Integer size;

    /**
     * 融资阶段：1=A轮 … 8=未融资（见 SQL 注释），可为 null
     */
    private Integer type;

    /**
     * 公司介绍
     */
    private String introduction;

    /**
     * 获取人员规模枚举）
     */
    public CompanySizeEnum getSizeEnum() {
        return CompanySizeEnum.fromCode(this.size);
    }

    /**
     * 设置人员规模枚举
     */
    public void setSizeEnum(CompanySizeEnum sizeEnum) {
        this.size = sizeEnum != null ? sizeEnum.getCode() : null;
    }

    /**
     * 获取融资阶段枚举
     */
    public CompanyFundingTypeEnum getTypeEnum() {
        return CompanyFundingTypeEnum.fromCode(this.type);
    }

    /**
     * 设置融资阶段枚举
     */
    public void setTypeEnum(CompanyFundingTypeEnum typeEnum) {
        this.type = typeEnum != null ? typeEnum.getCode() : null;
    }
}
