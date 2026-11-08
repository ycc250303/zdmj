package com.zdmj.userAuthService.service.impl;

import jakarta.mail.internet.MimeMessage;
import com.zdmj.common.exception.BusinessException;
import com.zdmj.common.exception.ErrorCode;
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
    void sendEmail_whenMailSenderFails_shouldPreserveCauseAndReportMailUnavailable() {
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        MailSendException cause = new MailSendException("send failed");
        doThrow(cause).when(mailSender).send(mimeMessage);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> emailService.sendEmail("to@test.com", "subject", "content"));

        assertEquals(ErrorCode.CAPTCHA_MAIL_UNAVAILABLE, ex.getErrorCode());
        org.junit.jupiter.api.Assertions.assertSame(cause, ex.getCause());
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
