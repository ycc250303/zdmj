package com.zdmj.jobService.dto;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import com.zdmj.common.exception.BusinessException;
import com.zdmj.common.exception.ErrorCode;
import com.zdmj.jobService.enums.JobEmploymentEnum;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.AccessLevel;
import lombok.Data;
import lombok.Setter;

/**
 * 岗位分页查询条件（对应 GET /jobs 的查询参数）。
 */
@Data
public class JobPageQueryDTO {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();


    /**
     * 页码，默认 1；当 page <= 0 时按 1 处理
     */
    @Setter(AccessLevel.NONE)
    private Integer page;

    /**
     * 每页条数，默认 20；最大 100，超过上限按 100 处理
     */
    @Setter(AccessLevel.NONE)
    private Integer limit;

    /**
     * 公司规模（多选）
     */
    @Setter(AccessLevel.NONE)
    private List<Integer> companySizes;

    /**
     * 公司融资阶段（多选）
     */
    @Setter(AccessLevel.NONE)
    private List<Integer> fundingTypes;

    /**
     * 行业（多选）
     */
    @Setter(AccessLevel.NONE)
    private List<String> industries;

    /**
     * 公司名称
     */
    private String companyName;

    /**
     * 实习 / 全职；不传表示不限制
     */
    private JobEmploymentEnum employment;

    /**
     * 薪资类型（1=日薪/2=月薪/3=年薪）；与 employment 二选一，用于薪资区间筛选时指定单位
     */
    @Setter(AccessLevel.NONE)
    private Integer salaryType;

    /**
     * 解析后的薪资类型（Service 写入，供 Mapper 统一按 salary_type 筛选）
     */
    private Integer resolvedSalaryType;

    /**
     * 全职用工（Service 写入；Mapper 映射为 salary_type IN (2, 3)）
     */
    private Boolean fullTimeEmployment;

    /**
     * 期望薪资下限（元，须与 resolvedSalaryType 同一单位）
     */
    @Setter(AccessLevel.NONE)
    private Integer filterSalaryMin;

    /**
     * 期望薪资上限（元）
     */
    @Setter(AccessLevel.NONE)
    private Integer filterSalaryMax;

    /**
     * 岗位名称关键词（对 job_name 做包含匹配）
     */
    private String jobName;

    public void setPage(Object page) {
        this.page = parseInteger(page, "page");
    }

    public void setLimit(Object limit) {
        this.limit = parseInteger(limit, "limit");
    }

    public void setFilterSalaryMin(Object filterSalaryMin) {
        this.filterSalaryMin = parseInteger(filterSalaryMin, "filterSalaryMin");
    }

    public void setFilterSalaryMax(Object filterSalaryMax) {
        this.filterSalaryMax = parseInteger(filterSalaryMax, "filterSalaryMax");
    }

    public void setSalaryType(Object salaryType) {
        this.salaryType = parseInteger(salaryType, "salaryType");
    }

    public void setCompanySizes(Object companySizes) {
        this.companySizes = parseIntegerList(companySizes, "companySizes");
    }

    public void setFundingTypes(Object fundingTypes) {
        this.fundingTypes = parseIntegerList(fundingTypes, "fundingTypes");
    }

    public void setIndustries(Object industries) {
        this.industries = parseStringList(industries, "industries");
    }

    private static Integer parseInteger(Object value, String fieldName) {
        if (value == null) {
            return null;
        }
        if (value instanceof Integer intValue) {
            return intValue;
        }
        if (value instanceof Number number) {
            double doubleValue = number.doubleValue();
            if (doubleValue % 1 != 0) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, fieldName + " 必须是整数");
            }
            return number.intValue();
        }
        if (value instanceof String str) {
            String trimmed = str.trim();
            if (trimmed.isEmpty()) {
                return null;
            }
            try {
                return Integer.valueOf(trimmed);
            } catch (NumberFormatException ex) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, fieldName + " 必须是整数");
            }
        }
        throw new BusinessException(ErrorCode.VALIDATION_ERROR, fieldName + " 必须是整数");
    }

    private static List<Integer> parseIntegerList(Object value, String fieldName) {
        List<Object> rawList = parseRawList(value, fieldName);
        if (rawList == null) {
            return null;
        }
        return rawList.stream()
                .map(item -> parseInteger(item, fieldName))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private static List<String> parseStringList(Object value, String fieldName) {
        List<Object> rawList = parseRawList(value, fieldName);
        if (rawList == null) {
            return null;
        }
        return rawList.stream()
                .map(item -> item == null ? null : String.valueOf(item))
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    @SuppressWarnings("unchecked")
    private static List<Object> parseRawList(Object value, String fieldName) {
        if (value == null) {
            return null;
        }
        if (value instanceof List<?> list) {
            return (List<Object>) list;
        }
        if (value instanceof String str) {
            String trimmed = str.trim();
            if (trimmed.isEmpty()) {
                return null;
            }
            if (!trimmed.startsWith("[") || !trimmed.endsWith("]")) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, fieldName + " 必须使用 [] 包裹");
            }
            try {
                return OBJECT_MAPPER.readValue(trimmed, new TypeReference<List<Object>>() {
                });
            } catch (Exception ex) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, fieldName + " 数组格式不合法");
            }
        }
        throw new BusinessException(ErrorCode.VALIDATION_ERROR, fieldName + " 必须是数组");
    }
}
