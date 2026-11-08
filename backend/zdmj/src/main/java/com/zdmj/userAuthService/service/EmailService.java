package com.zdmj.userAuthService.service;

/**
 * 邮件服务接口
 */
public interface EmailService {

    /**
     * 发送邮件
     * 
     * @param to      收件人邮箱
     * @param subject 邮件主题
     * @param content 邮件内容
     * @throws com.zdmj.common.exception.BusinessException 邮件故障时返回错误码 2016 并保留原始原因
     */
    void sendEmail(String to, String subject, String content);
}
