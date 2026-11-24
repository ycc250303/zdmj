package com.zdmj.conversationService.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.zdmj.conversationService.entity.Message;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 消息 Mapper 骨架
 */
@Mapper
public interface MessageMapper extends BaseMapper<Message> {

    /** 最近若干轮用户问题与关联助手回复，按序排列；读取范围同时限制用户和会话。 */
    java.util.List<Message> selectContextHistory(@Param("userId") Long userId,
            @Param("conversationId") Long conversationId, @Param("maxTurns") int maxTurns);

    /** 会话行锁内检查是否已有生成中的回复。 */
    boolean hasProcessing(@Param("userId") Long userId, @Param("conversationId") Long conversationId);

    /** 仅将生成中的助手回复转换为终态，同时保存内容；返回实际更新行数。 */
    int finishAssistant(@Param("id") Long id, @Param("userId") Long userId,
            @Param("conversationId") Long conversationId, @Param("content") String content,
            @Param("generationStatus") int generationStatus);

    /** 单实例启动时将进程中断留下的生成状态转为失败，保留现有内容。 */
    int recoverInterrupted();

    /**
     * 按会话分页查询消息（分页由 MyBatis-Plus 插件处理）。
     */
    IPage<Message> selectPageByConversationId(IPage<Message> page, @Param("conversationId") Long conversationId);
}
