package com.zdmj.testsupport;

import java.io.InputStream;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;

/**
 * 邮件替身。记录待发送消息，不连接 SMTP。
 */
public class TestMailSender implements JavaMailSender {

    private final List<MimeMessage> sent = new CopyOnWriteArrayList<>();
    private final Session session = Session.getInstance(new java.util.Properties());

    public List<MimeMessage> sentMessages() {
        return List.copyOf(sent);
    }

    @Override
    public MimeMessage createMimeMessage() {
        return new MimeMessage(session);
    }

    @Override
    public MimeMessage createMimeMessage(InputStream contentStream) throws MailException {
        try {
            return new MimeMessage(session, contentStream);
        } catch (Exception e) {
            throw new MailException("读取邮件内容失败", e) {
            };
        }
    }

    @Override
    public void send(MimeMessage mimeMessage) throws MailException {
        sent.add(mimeMessage);
    }

    @Override
    public void send(MimeMessage... mimeMessages) throws MailException {
        for (MimeMessage mimeMessage : mimeMessages) {
            send(mimeMessage);
        }
    }

    @Override
    public void send(org.springframework.mail.javamail.MimeMessagePreparator mimeMessagePreparator)
            throws MailException {
        try {
            MimeMessage message = createMimeMessage();
            mimeMessagePreparator.prepare(message);
            send(message);
        } catch (Exception e) {
            throw new MailException("准备邮件失败", e) {
            };
        }
    }

    @Override
    public void send(org.springframework.mail.javamail.MimeMessagePreparator... mimeMessagePreparators)
            throws MailException {
        for (org.springframework.mail.javamail.MimeMessagePreparator preparator : mimeMessagePreparators) {
            send(preparator);
        }
    }

    @Override
    public void send(SimpleMailMessage simpleMessage) throws MailException {
        calls(simpleMessage);
    }

    @Override
    public void send(SimpleMailMessage... simpleMessages) throws MailException {
        for (SimpleMailMessage simpleMessage : simpleMessages) {
            send(simpleMessage);
        }
    }

    private void calls(SimpleMailMessage simpleMessage) {
        try {
            MimeMessage message = createMimeMessage();
            message.setSubject(simpleMessage.getSubject());
            sent.add(message);
        } catch (Exception e) {
            throw new MailException("记录邮件失败", e) {
            };
        }
    }
}
