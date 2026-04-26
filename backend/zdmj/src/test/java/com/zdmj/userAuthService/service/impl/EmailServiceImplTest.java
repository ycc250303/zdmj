package com.zdmj.userAuthService.service.impl;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmailServiceImplTest {

    @Mock
    private JavaMailSender mailSender;

    private EmailServiceImpl emailService;

    @BeforeEach
    void setUp() {
        emailService = new EmailServiceImpl(mailSender, "noreply@zdmj.com");
    }

    @Test
    void sendEmail_success_shouldInvokeMailSenderSend() {
        MimeMessage message = new MimeMessage(Session.getDefaultInstance(new Properties()));
        when(mailSender.createMimeMessage()).thenReturn(message);

        emailService.sendEmail("to@zdmj.com", "subject", "<b>content</b>");

        verify(mailSender).send(message);
    }

    @Test
    void sendEmail_whenMailSenderThrows_shouldWrapToRuntimeException() {
        MimeMessage message = new MimeMessage(Session.getDefaultInstance(new Properties()));
        when(mailSender.createMimeMessage()).thenReturn(message);
        doThrow(new RuntimeException("smtp error")).when(mailSender).send(any(MimeMessage.class));

        assertThrows(RuntimeException.class,
                () -> emailService.sendEmail("to@zdmj.com", "subject", "<b>content</b>"));
    }
}
