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
 * PostgreSQL JSONB 对象与 Java String 的类型处理器
 * 用于处理 JSONB 对象字段（如 info、lightspot、lookup_result 等）
 * 
 * 功能：
 * 1. 插入时：自动验证 JSON 格式，确保是有效的 JSON 字符串
 * 2. 读取时：直接返回 JSON 字符串（由业务层决定如何解析）
 * 
 * 使用方式：
 * 
 * @TableField(typeHandler = JsonbObjectTypeHandler.class)
 *                         private String info;
 * 
 *                         注意：
 *                         - 此 TypeHandler 只负责格式验证和基本转换，不进行对象序列化/反序列化
 *                         - 业务层可以使用 ObjectMapper 将 String 转换为具体的 Java 对象
 *                         - 如果传入的是 null 或空字符串，会转换为 PostgreSQL 的 NULL
 */
@Slf4j
@MappedTypes({ String.class })
@MappedJdbcTypes(JdbcType.OTHER)
public class JsonbObjectTypeHandler extends BaseTypeHandler<String> {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, String parameter, JdbcType jdbcType)
            throws SQLException {
        try {
            // 验证 JSON 格式
            if (parameter != null && !parameter.trim().isEmpty()) {
                // 尝试解析 JSON，验证格式是否正确
                objectMapper.readTree(parameter);
            }

            // PostgreSQL JDBC 驱动会自动将字符串转换为 JSONB
            // 如果数据库列类型是 JSONB，PostgreSQL 会自动处理类型转换
            ps.setString(i, parameter);
        } catch (Exception e) {
            log.error("设置 JSONB 对象参数失败: {}", e.getMessage(), e);
            throw new SQLException("JSONB 格式错误: " + e.getMessage(), e);
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
