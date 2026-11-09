package com.zdmj.aiService.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zdmj.aiService.entity.UserLlmConfig;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserLlmConfigMapper extends BaseMapper<UserLlmConfig> {
    /**
     * 原子插入或覆盖用户配置，冲突时保留创建时间。
     *
     * @param config 包含用户编号、模型编号、密文与应用时间的完整配置
     * @return 影响行数，成功时必须为 1
     */
    int upsert(UserLlmConfig config);
}
