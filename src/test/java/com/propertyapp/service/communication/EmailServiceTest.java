package com.propertyapp.service.communication;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;
import org.thymeleaf.TemplateEngine;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Bug 6 — {@code EmailService} previously caught every send failure with one generic
 * {@code catch (Exception e)}, so an SMTP authentication failure (unset/invalid
 * MAIL_USERNAME/MAIL_PASSWORD) was indistinguishable in the logs from any other mail error.
 * This verifies the new dedicated {@code AuthenticationFailedException} branch fires with a
 * distinct, actionable log message, and — just as importantly — that the failure is still
 * swallowed rather than propagated (an async @Async method must not throw, and the
 * forgot-password caller must not learn whether the target email exists).
 */
class EmailServiceTest {

    private JavaMailSender mailSender;
    private TemplateEngine templateEngine;
    private EmailService emailService;
    private ListAppender<ILoggingEvent> logAppender;

    @BeforeEach
    void setUp() throws Exception {
        mailSender = mock(JavaMailSender.class);
        templateEngine = mock(TemplateEngine.class);
        emailService = new EmailService(mailSender, templateEngine);
        ReflectionTestUtils.setField(emailService, "fromEmail", "noreply@propertyapp.com");
        ReflectionTestUtils.setField(emailService, "fromName", "PropertyApp");

        MimeMessage realMessage = new MimeMessage(Session.getDefaultInstance(new Properties()));
        when(mailSender.createMimeMessage()).thenReturn(realMessage);

        logAppender = new ListAppender<>();
        logAppender.start();
        ((Logger) LoggerFactory.getLogger(EmailService.class)).addAppender(logAppender);
    }

    @AfterEach
    void tearDown() {
        ((Logger) LoggerFactory.getLogger(EmailService.class)).detachAppender(logAppender);
    }

    @Test
    void sendEmailLogsADistinctMessageOnAuthenticationFailureAndDoesNotThrow() {
        doThrow(new MailAuthenticationException("535 Authentication failed"))
                .when(mailSender).send(org.mockito.ArgumentMatchers.any(MimeMessage.class));

        assertThatCode(() -> emailService.sendEmail("buyer@propertyapp.com", "Reset Your Password", "<p>reset</p>"))
                .doesNotThrowAnyException();

        assertThat(logAppender.list)
                .anyMatch(event -> event.getFormattedMessage().contains("SMTP authentication failed")
                        && event.getFormattedMessage().contains("buyer@propertyapp.com"));
    }

    @Test
    void sendEmailLogsGenericFailureForNonAuthErrors() {
        doThrow(new RuntimeException("connection refused"))
                .when(mailSender).send(org.mockito.ArgumentMatchers.any(MimeMessage.class));

        assertThatCode(() -> emailService.sendEmail("buyer@propertyapp.com", "Reset Your Password", "<p>reset</p>"))
                .doesNotThrowAnyException();

        assertThat(logAppender.list)
                .anyMatch(event -> event.getFormattedMessage().contains("Failed to send email")
                        && !event.getFormattedMessage().contains("SMTP authentication failed"));
    }
}
