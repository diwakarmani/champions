package com.propertyapp.service.agent;

import com.propertyapp.entity.user.User;
import com.propertyapp.enums.NotificationType;
import com.propertyapp.service.communication.EmailService;
import com.propertyapp.service.notification.NotificationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.IContext;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AgentNotifierTest {

    private final NotificationService notifications = mock(NotificationService.class);
    private final EmailService email = mock(EmailService.class);
    private final TemplateEngine templates = mock(TemplateEngine.class);
    private final AgentNotifier notifier = new AgentNotifier(notifications, email, templates);
    private final User person = User.builder().email("a@example.com").firstName("A").build();

    @BeforeEach
    void beginTransaction() {
        TransactionSynchronizationManager.initSynchronization();
        person.setId(5L);
        when(templates.process(anyString(), any(IContext.class))).thenReturn("<html/>");
    }

    @AfterEach
    void endTransaction() {
        TransactionSynchronizationManager.clear();
    }

    @Test
    void nothingIsSentBeforeCommit() {
        notifier.tell(person, NotificationType.AGENT_SUSPENDED, "t", "m", "USER", 5L);
        verifyNoInteractions(notifications, email, templates);

        commit();

        verify(notifications).send(5L, NotificationType.AGENT_SUSPENDED, "t", "m", "USER", 5L);
        verify(email).sendEmail(eq("a@example.com"), eq("t"), eq("<html/>"));
    }

    @Test
    void nothingIsSentIfTheChangeRollsBack() {
        notifier.tell(person, NotificationType.AGENT_SUSPENDED, "t", "m", "USER", 5L);
        notifier.notifyInApp(List.of(1L), NotificationType.AGENT_APPLICATION_SUBMITTED, "t", "m", "X", 1L);

        TransactionSynchronizationManager.getSynchronizations()
                .forEach(sync -> sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

        verifyNoInteractions(notifications, email, templates);
    }

    @Test
    void aFailedNoticeDoesNotReportTheCommittedChangeAsFailed() {
        doThrow(new IllegalStateException("template broke")).when(templates).process(anyString(), any(IContext.class));
        notifier.tell(person, NotificationType.AGENT_SUSPENDED, "t", "m", "USER", 5L);

        assertThatCode(this::commit).doesNotThrowAnyException();
        verify(email, never()).sendEmail(anyString(), anyString(), anyString());
    }

    @Test
    void adminNoticesAreInAppOnly() {
        notifier.notifyInApp(List.of(1L, 2L), NotificationType.AGENT_APPLICATION_SUBMITTED, "t", "m", "X", 9L);
        commit();

        verify(notifications).send(1L, NotificationType.AGENT_APPLICATION_SUBMITTED, "t", "m", "X", 9L);
        verify(notifications).send(2L, NotificationType.AGENT_APPLICATION_SUBMITTED, "t", "m", "X", 9L);
        verifyNoInteractions(email);
    }

    private void commit() {
        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
    }
}
