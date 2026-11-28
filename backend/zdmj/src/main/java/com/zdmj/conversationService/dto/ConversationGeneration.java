package com.zdmj.conversationService.dto;

import com.zdmj.common.context.CurrentActor;
import java.util.concurrent.CompletableFuture;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

/**
 * 单轮生成的内部可变状态；消息服务在本对象上同步准备、取消和结束保存。
 * 保留原请求编号，使删除通知和外部取消线程触发的清理继续关联原生成。
 */
@Getter
@RequiredArgsConstructor
public final class ConversationGeneration {

    /** 发起本轮生成的已认证操作者。 */
    private final CurrentActor actor;
    /** 全局唯一会话编号，所属关系由消息服务校验。 */
    private final Long conversationId;
    /** 原请求日志编号；非 HTTP 内部调用可为空。 */
    private final String traceId;
    /** 订阅开始的单调时间，仅用于计算耗时。 */
    private final long startedNanos = System.nanoTime();
    /** 已接收的增量正文，由消息服务同步访问，不输出日志。 */
    private final StringBuilder full = new StringBuilder(256);
    /** 删除提交后的通知，同时表达删除状态，取消订阅不会改变它。 */
    private final CompletableFuture<Void> deletion = new CompletableFuture<>();
    /** 下游是否取消；准备与取消操作共同使用本对象的同步锁。 */
    @Setter private volatile boolean cancelled;
    /** 结束保存的原始异常，存在时保留活动登记及已接收内容。 */
    @Setter private volatile RuntimeException persistenceFailure;
    /** 准备事务已提交的问答与配置；尚未完成准备时为空。 */
    @Setter private PreparedConversationMessages prepared;
}
