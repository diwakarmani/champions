package com.propertyapp.service.maintenance;

import com.propertyapp.entity.inquiry.Inquiry;
import com.propertyapp.entity.inquiry.InquiryStatus;
import com.propertyapp.enums.NotificationType;
import com.propertyapp.repository.inquiry.InquiryRepository;
import com.propertyapp.service.notification.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Single application-wide maintenance scheduler.
 *
 * Design principles:
 *  - ONE scheduler class; each logical task lives in its own private method.
 *  - Every task is wrapped in try/catch — one failure never blocks the others.
 *  - New tasks: add a private runXxx() method and call it from runAll().
 *  - Cadence and thresholds are configured via app.maintenance.* in application.yaml.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MaintenanceScheduler {

    @Value("${app.maintenance.auto-contact-seconds:86400}")
    private int autoContactSeconds;

    private final InquiryRepository inquiryRepository;
    private final NotificationService notificationService;

    // ── Main entry ───────────────────────────────────────────────────────────

    @Scheduled(cron = "${app.maintenance.cron:0 */30 * * * *}")
    @Transactional
    public void runAll() {
        runAutoContactTransition();
        // ADD NEW MAINTENANCE TASKS HERE — each must have its own try/catch
    }

    // ── Task: auto-transition stale NEW inquiries → CONTACTED ────────────────

    void runAutoContactTransition() {
        try {
            LocalDateTime cutoff = LocalDateTime.now().minusSeconds(autoContactSeconds);

            // Pass 1: load entities with all data needed for notifications
            List<Inquiry> stale = inquiryRepository.findStaleNewInquiries(InquiryStatus.NEW, cutoff);
            if (stale.isEmpty()) {
                log.debug("[Maintenance] autoContactTransition: no stale inquiries found");
                return;
            }

            // Pass 2: single-query bulk status update
            int updated = inquiryRepository.bulkTransitionStatus(
                    InquiryStatus.NEW, InquiryStatus.CONTACTED, cutoff);
            log.info("[Maintenance] autoContactTransition: transitioned {} inquiries to CONTACTED", updated);

            // Pass 3: notify each affected buyer
            for (Inquiry inquiry : stale) {
                try {
                    Long buyerId = inquiry.getInquirer().getId();
                    String realtorName = inquiry.getProperty().getOwner().getFirstName()
                            + " " + inquiry.getProperty().getOwner().getLastName();
                    String propertyTitle = inquiry.getProperty().getTitle();

                    notificationService.send(
                            buyerId,
                            NotificationType.INQUIRY_RATING_PROMPT,
                            "How was your experience?",
                            "You connected with " + realtorName.trim() + " about \""
                                    + propertyTitle + "\". Leave a review!",
                            "INQUIRY",
                            inquiry.getId()
                    );
                } catch (Exception notifEx) {
                    // Notification failure must never roll back the status transition
                    log.warn("[Maintenance] autoContactTransition: notification failed for inquiry {}: {}",
                            inquiry.getId(), notifEx.getMessage());
                }
            }

        } catch (Exception e) {
            log.error("[Maintenance] autoContactTransition FAILED: {}", e.getMessage(), e);
            // Do NOT rethrow — other maintenance tasks must still run
        }
    }

    // ── Placeholder for future maintenance tasks ──────────────────────────────
    // private void runExpiredFavoritesCleanup() { ... }
    // private void runStaleSessionPurge()        { ... }
}
