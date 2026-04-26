package com.zdmj.userAuthService.service.impl;

import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmailServiceImplTest {

    @Mock
    private JavaMailSender mailSender;

    @Mock
    private MimeMessage mimeMessage;

    private EmailServiceImpl emailService;

    @BeforeEach
    void setUp() {
        emailService = new EmailServiceImpl(mailSender, "noreply@test.com");
    }

    @Test
    void sendEmail_whenMailSenderFails_shouldThrowRuntimeException() {
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        doThrow(new MailSendException("send failed")).when(mailSender).send(mimeMessage);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> emailService.sendEmail("to@test.com", "subject", "content"));

        assertEquals("邮件发送失败", ex.getMessage());
        verify(mailSender).send(mimeMessage);
    }

    @Test
    void sendEmail_whenSuccess_shouldSendMimeMessage() {
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        assertDoesNotThrow(() -> emailService.sendEmail("to@test.com", "subject", "<b>content</b>"));

        verify(mailSender).createMimeMessage();
        verify(mailSender).send(mimeMessage);
    }
}
