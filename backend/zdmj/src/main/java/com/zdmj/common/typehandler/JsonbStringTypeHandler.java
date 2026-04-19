package com.zdmj.common.typehandler;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * PostgreSQL JSONB 与 Java String（原始 JSON 文本）互转。
 * 读结果集时兼容 {@link PGobject}，避免列有数据但实体 String 字段仍为 null。
 */
@MappedTypes({ String.class })
@MappedJdbcTypes(JdbcType.OTHER)
public class JsonbStringTypeHandler extends BaseTypeHandler<String> {

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, String parameter, JdbcType jdbcType)
            throws SQLException {
        ps.setString(i, parameter);
    }

    @Override
    public String getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return toJsonString(rs.getObject(columnName));
    }

    @Override
    public String getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return toJsonString(rs.getObject(columnIndex));
    }

    @Override
    public String getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return toJsonString(cs.getObject(columnIndex));
    }

    private static String toJsonString(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof String s) {
            return s;
        }
        // JDBC 对 JSONB 常返回 PGobject，避免直接依赖驱动类名以兼容不同类加载环境
        try {
            if ("org.postgresql.util.PGobject".equals(raw.getClass().getName())) {
                Object v = raw.getClass().getMethod("getValue").invoke(raw);
                return v != null ? v.toString() : null;
            }
        } catch (ReflectiveOperationException ignored) {
            // fall through
        }
        return raw.toString();
    }
}
