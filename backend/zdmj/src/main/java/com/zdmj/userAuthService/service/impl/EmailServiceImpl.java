package com.zdmj.userAuthService.service.impl;

import com.zdmj.userAuthService.service.EmailService;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

/**
 * 邮件服务实现类
 */
@Slf4j
@Service
public class EmailServiceImpl implements EmailService {

    @Autowired
    private JavaMailSender mailSender;

    /**
     * 发件人邮箱（从配置文件读取，必须与SMTP认证用户名一致）
     */
    @Value("${spring.mail.username}")
    private String fromEmail;

    @Override
    public void sendEmail(String to, String subject, String content) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            // 设置发件人（与SMTP认证用户名一致）
            helper.setFrom(fromEmail);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(content, true); // false表示纯文本，true表示HTML

            mailSender.send(message);
            log.info("邮件发送成功: To={}, From={}", to, fromEmail);
        } catch (Exception e) {
            log.error("邮件发送失败: To={}, From={}, Error={}", to, fromEmail, e.getMessage(), e);
            throw new RuntimeException("邮件发送失败", e);
        }
    }
}
