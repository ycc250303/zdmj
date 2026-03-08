package com.zdmj.common.typehandler;

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

/**
 * PostgreSQL JSONB 数组与 Java String 的类型处理器
 * 用于处理字段类型为 String 但数据库列为 JSONB 数组的情况
 * 
 * 功能：
 * 1. 插入时：验证 JSON 字符串是否为有效的 JSON 数组格式
 * 2. 读取时：直接返回 JSON 字符串
 * 
 * 使用方式：
 * 
 * @TableField(typeHandler = JsonbListStringTypeHandler.class)
 *                         private String context;
 * 
 *                         注意：
 *                         - 此 TypeHandler 用于处理 String 类型的字段，但数据库列为 JSONB 数组
 *                         - 如果传入的是 null 或空字符串，会转换为 PostgreSQL 的空数组 "[]"
 *                         - 如果传入的不是有效的 JSON 数组，会抛出异常
 */
@Slf4j
@MappedTypes({ String.class })
@MappedJdbcTypes(JdbcType.OTHER)
public class JsonbListStringTypeHandler extends BaseTypeHandler<String> {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, String parameter, JdbcType jdbcType)
            throws SQLException {
        try {
            // 如果为空字符串，使用空数组
            if (parameter == null || parameter.trim().isEmpty()) {
                ps.setString(i, "[]");
                return;
            }

            // 验证是否为有效的 JSON 数组格式
            try {
                var jsonNode = objectMapper.readTree(parameter);
                if (!jsonNode.isArray()) {
                    throw new SQLException("JSON 字符串不是有效的数组格式: " + parameter);
                }
            } catch (Exception e) {
                if (e instanceof SQLException) {
                    throw e;
                }
                throw new SQLException("JSON 格式错误: " + e.getMessage(), e);
            }

            // PostgreSQL JDBC 驱动会自动将字符串转换为 JSONB
            ps.setString(i, parameter);
        } catch (SQLException e) {
            throw e;
        } catch (Exception e) {
            log.error("设置 JSONB 数组参数失败: {}", e.getMessage(), e);
            throw new SQLException("设置 JSONB 数组参数失败: " + e.getMessage(), e);
        }
    }

    @Override
    public String getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return rs.getString(columnName);
    }

    @Override
    public String getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return rs.getString(columnIndex);
    }

    @Override
    public String getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return cs.getString(columnIndex);
    }
}
