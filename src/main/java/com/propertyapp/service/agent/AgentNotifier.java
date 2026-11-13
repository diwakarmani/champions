package com.propertyapp.service.agent;

import com.propertyapp.entity.user.User;
import com.propertyapp.enums.NotificationType;
import com.propertyapp.service.communication.EmailService;
import com.propertyapp.service.notification.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;


/** Tells people only after the change has committed, so a rolled-back action tells nobody (ADR-0004). */
@Component
@RequiredArgsConstructor
@Slf4j
class AgentNotifier {

    private final NotificationService notificationService;
    private final EmailService emailService;
    private final TemplateEngine templateEngine;

    /** In-app notice and email to the person affected. */
    void tell(User recipient, NotificationType type, String title, String message, String entityType, Long entityId) {
        Long recipientId = recipient.getId();
        String email = recipient.getEmail();
        String firstName = recipient.getFirstName();
        afterCommit(() -> {
            notificationService.send(recipientId, type, title, message, entityType, entityId);
            emailService.sendEmail(email, title, render(firstName, title, message));
        });
    }

    /** In-app notice only: admins do not need an email per application. */
    void notifyInApp(Iterable<Long> recipientIds, NotificationType type, String title, String message,
                     String entityType, Long entityId) {
        afterCommit(() -> recipientIds.forEach(
                id -> notificationService.send(id, type, title, message, entityType, entityId)));
    }

    private String render(String firstName, String title, String message) {
        Context context = new Context();
        context.setVariable("firstName", firstName);
        context.setVariable("title", title);
        context.setVariable("message", message);
        return templateEngine.process("email/notice-template", context);
    }

    private void afterCommit(Runnable action) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                // The change is already saved: a failed notice must not report it as failed.
                try {
                    action.run();
                } catch (RuntimeException e) {
                    log.error("Could not send agent notice: {}", e.getMessage(), e);
                }
            }
        });
    }
}
