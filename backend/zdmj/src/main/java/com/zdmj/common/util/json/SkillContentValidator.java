package com.zdmj.common.util.json;

import com.zdmj.common.exception.BusinessException;
import com.zdmj.common.exception.ErrorCode;
import com.zdmj.resumeService.dto.SkillItemDTO;

import java.util.List;

/**
 * 技能内容验证器
 * 
 * 验证规则：
 * - 根节点必须是数组
 * - 数组元素必须是对象
 * - 对象必须包含 type（字符串）和 content（数组）字段
 * - 严格模式：不允许其他字段
 */
public class SkillContentValidator {

    /**
     * 验证技能内容结构（强类型）
     */
    public static List<SkillItemDTO> validate(List<SkillItemDTO> contentList) {
        if (contentList == null || contentList.isEmpty()) {
            throw new BusinessException(ErrorCode.ILLEGAL_ARGUMENT.getCode(), "技能内容不能为空");
        }

        for (int i = 0; i < contentList.size(); i++) {
            SkillItemDTO item = contentList.get(i);
            String path = "skills[" + i + "]";
            if (item == null) {
                throw new BusinessException(ErrorCode.ILLEGAL_ARGUMENT.getCode(), path + " 不能为空");
            }
            if (item.getType() == null || item.getType().trim().isEmpty()) {
                throw new BusinessException(ErrorCode.ILLEGAL_ARGUMENT.getCode(), path + ".type 不能为空");
            }
            if (item.getContent() == null || item.getContent().isEmpty()) {
                throw new BusinessException(ErrorCode.ILLEGAL_ARGUMENT.getCode(), path + ".content 不能为空");
            }
            for (int j = 0; j < item.getContent().size(); j++) {
                String value = item.getContent().get(j);
                if (value == null || value.trim().isEmpty()) {
                    throw new BusinessException(ErrorCode.ILLEGAL_ARGUMENT.getCode(),
                            path + ".content[" + j + "] 不能为空");
                }
            }
        }
        return contentList;
    }
}
