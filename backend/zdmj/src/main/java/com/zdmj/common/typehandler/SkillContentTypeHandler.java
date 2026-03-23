package com.zdmj.common.typehandler;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zdmj.resumeService.dto.SkillItemDTO;
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
 * skills.content(JSONB) <-> List<SkillItemDTO> 类型处理器
 */
@Slf4j
@MappedTypes({ List.class })
@MappedJdbcTypes(JdbcType.OTHER)
public class SkillContentTypeHandler extends BaseTypeHandler<List<SkillItemDTO>> {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<List<SkillItemDTO>> SKILL_ITEM_LIST_TYPE = new TypeReference<List<SkillItemDTO>>() {
    };

    /**
     * 设置 skills.content JSONB
     * 
     * @param ps        PreparedStatement
     * @param i         参数索引
     * @param parameter 参数值
     * @param jdbcType  JDBC类型
     * @throws SQLException 异常
     */
    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, List<SkillItemDTO> parameter, JdbcType jdbcType)
            throws SQLException {
        try {
            ps.setString(i, OBJECT_MAPPER.writeValueAsString(parameter));
        } catch (Exception e) {
            throw new SQLException("设置 skills.content JSONB 失败: " + e.getMessage(), e);
        }
    }

    @Override
    public List<SkillItemDTO> getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return parseJsonb(rs.getString(columnName));
    }

    @Override
    public List<SkillItemDTO> getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return parseJsonb(rs.getString(columnIndex));
    }

    @Override
    public List<SkillItemDTO> getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return parseJsonb(cs.getString(columnIndex));
    }

    private List<SkillItemDTO> parseJsonb(String json) {
        if (json == null || json.trim().isEmpty() || "null".equals(json)) {
            return new ArrayList<>();
        }
        try {
            return OBJECT_MAPPER.readValue(json, SKILL_ITEM_LIST_TYPE);
        } catch (Exception e) {
            log.warn("解析 skills.content JSONB 失败: {}", e.getMessage());
            return new ArrayList<>();
        }
    }
}
