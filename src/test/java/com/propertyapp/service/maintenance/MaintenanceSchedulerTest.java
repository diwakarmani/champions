package com.propertyapp.service.maintenance;

import com.propertyapp.entity.inquiry.Inquiry;
import com.propertyapp.entity.inquiry.InquiryStatus;
import com.propertyapp.entity.property.Property;
import com.propertyapp.entity.user.User;
import com.propertyapp.enums.NotificationType;
import com.propertyapp.repository.inquiry.InquiryRepository;
import com.propertyapp.service.notification.NotificationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MaintenanceSchedulerTest {

    @Mock InquiryRepository   inquiryRepository;
    @Mock NotificationService notificationService;

    @InjectMocks MaintenanceScheduler scheduler;

    @Test
    void transitionsStaleInquiriesAndNotifiesBuyers() {
        User buyer   = buildUser(1L, "Alice", "Buyer");
        User realtor = buildUser(2L, "Bob",   "Realtor");
        Property prop = buildProperty("Test Property", realtor);

        Inquiry stale = buildInquiry(10L, buyer, prop);
        when(inquiryRepository.findStaleNewInquiries(eq(InquiryStatus.NEW), any()))
                .thenReturn(List.of(stale));
        when(inquiryRepository.bulkTransitionStatus(any(), any(), any())).thenReturn(1);

        scheduler.runAutoContactTransition();

        verify(inquiryRepository).bulkTransitionStatus(
                eq(InquiryStatus.NEW), eq(InquiryStatus.CONTACTED), any());
        verify(notificationService).send(
                eq(1L), eq(NotificationType.INQUIRY_RATING_PROMPT),
                anyString(), contains("Bob Realtor"),
                eq("INQUIRY"), eq(10L));
    }

    @Test
    void skipsTransition_whenNoStaleInquiries() {
        when(inquiryRepository.findStaleNewInquiries(any(), any())).thenReturn(List.of());

        scheduler.runAutoContactTransition();

        verify(inquiryRepository, never()).bulkTransitionStatus(any(), any(), any());
        verify(notificationService, never()).send(any(), any(), any(), any(), any(), any());
    }

    @Test
    void continuesWhenNotificationFails_forOneInquiry() {
        User buyer1   = buildUser(1L, "Alice", "A");
        User buyer2   = buildUser(2L, "Carol", "B");
        User realtor  = buildUser(3L, "Dan",   "Realtor");
        Property prop = buildProperty("A Flat", realtor);

        Inquiry i1 = buildInquiry(11L, buyer1, prop);
        Inquiry i2 = buildInquiry(12L, buyer2, prop);

        when(inquiryRepository.findStaleNewInquiries(any(), any())).thenReturn(List.of(i1, i2));
        when(inquiryRepository.bulkTransitionStatus(any(), any(), any())).thenReturn(2);

        // First notification throws; second must still fire
        doThrow(new RuntimeException("push fail"))
                .when(notificationService).send(eq(1L), any(), any(), any(), any(), any());

        scheduler.runAutoContactTransition();   // must not throw

        verify(notificationService, times(2)).send(any(), any(), any(), any(), any(), any());
    }

    @Test
    void runAll_doesNotPropagateTaskException() {
        // Repository throws — runAll must swallow and not propagate
        when(inquiryRepository.findStaleNewInquiries(any(), any()))
                .thenThrow(new RuntimeException("DB down"));

        org.assertj.core.api.Assertions.assertThatNoException()
                .isThrownBy(() -> scheduler.runAll());
    }

    // ── helpers ──────────────────────────────────────────────────────────────────

    private User buildUser(Long id, String first, String last) {
        User u = User.builder().firstName(first).lastName(last).email(first + "@test.com").build();
        u.setId(id);
        return u;
    }

    private Property buildProperty(String title, User owner) {
        Property p = Property.builder().title(title).build();
        p.setId(99L);
        // reflectively set owner via setter — Lombok generates it
        try {
            var f = Property.class.getDeclaredField("owner");
            f.setAccessible(true);
            f.set(p, owner);
        } catch (Exception e) { throw new RuntimeException(e); }
        return p;
    }

    private Inquiry buildInquiry(Long id, User inquirer, Property property) {
        Inquiry i = Inquiry.builder()
                .inquirer(inquirer)
                .property(property)
                .name("test").email("test@test.com")
                .message("msg").status(InquiryStatus.NEW)
                .build();
        i.setId(id);
        return i;
    }
}
