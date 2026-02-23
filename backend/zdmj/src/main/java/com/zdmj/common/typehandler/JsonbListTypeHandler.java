package com.zdmj.common.typehandler;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * PostgreSQL JSONB 数组与 Java List 的类型处理器
 * 支持 List<Long> 和 List<String> 类型
 * 
 * 使用方式：
 * @TableField(typeHandler = JsonbListTypeHandler.class)
 * private List<Long> projects;
 */
@Slf4j
@MappedTypes({List.class})
@MappedJdbcTypes(JdbcType.OTHER)
public class JsonbListTypeHandler extends BaseTypeHandler<List<?>> {

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final TypeReference<List<Long>> LONG_LIST_TYPE = new TypeReference<List<Long>>() {};
    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<List<String>>() {};

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, List<?> parameter, JdbcType jdbcType) throws SQLException {
        try {
            // 将 List 转换为 JSON 字符串
            String json = objectMapper.writeValueAsString(parameter);
            
            // 使用 CAST 确保 PostgreSQL 识别为 JSONB 类型
            // 注意：这里直接设置字符串，PostgreSQL JDBC 驱动会自动处理类型转换
            // 如果数据库列类型是 JSONB，PostgreSQL 会自动将字符串转换为 JSONB
            ps.setString(i, json);
        } catch (Exception e) {
            log.error("设置 JSONB 参数失败: {}", e.getMessage(), e);
            throw new SQLException("设置 JSONB 参数失败: " + e.getMessage(), e);
        }
    }

    @Override
    public List<?> getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return parseJsonb(rs.getString(columnName));
    }

    @Override
    public List<?> getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return parseJsonb(rs.getString(columnIndex));
    }

    @Override
    public List<?> getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return parseJsonb(cs.getString(columnIndex));
    }

    /**
     * 解析 JSONB 字符串为 List
     * 自动识别元素类型（Long 或 String）
     */
    private List<?> parseJsonb(String json) {
        if (json == null || json.trim().isEmpty() || "null".equals(json)) {
            return new ArrayList<>();
        }

        try {
            // 先尝试解析为 List<Long>
            try {
                List<Long> longList = objectMapper.readValue(json, LONG_LIST_TYPE);
                // 验证是否真的是 Long 类型（检查第一个元素）
                if (!longList.isEmpty() && longList.get(0) instanceof Long) {
                    return longList;
                }
            } catch (Exception e) {
                // 不是 Long 类型，继续尝试 String
            }

            // 尝试解析为 List<String>
            return objectMapper.readValue(json, STRING_LIST_TYPE);
        } catch (Exception e) {
            log.warn("解析 JSONB 为 List 失败: {}, 原始值: {}", e.getMessage(), json);
            return new ArrayList<>();
        }
    }
}
