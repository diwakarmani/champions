package com.propertyapp.service.contact;

import com.propertyapp.dto.publicapi.GuestContactRequest;
import com.propertyapp.dto.publicapi.GuestInquiryRequest;
import com.propertyapp.exception.BadRequestException;
import com.propertyapp.repository.inquiry.InquiryRepository;
import com.propertyapp.repository.property.PropertyContactEventRepository;
import com.propertyapp.repository.property.PropertyRepository;
import com.propertyapp.security.ratelimit.RateLimitStore;
import com.propertyapp.service.communication.EmailService;
import com.propertyapp.service.notification.NotificationService;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.thymeleaf.TemplateEngine;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** The two switches: guest contact off, and Turnstile on (ADR-0007 Amendment 1). */
class GuestContactServiceTest {

    PropertyRepository properties = mock(PropertyRepository.class);
    InquiryRepository inquiries = mock(InquiryRepository.class);
    RateLimitStore limits = mock(RateLimitStore.class);
    TurnstileVerifier turnstile = mock(TurnstileVerifier.class);

    GuestContactService service(boolean guestEnabled) {
        return new GuestContactService(properties, inquiries, mock(PropertyContactEventRepository.class), limits,
                turnstile, mock(NotificationService.class), mock(EmailService.class), mock(TemplateEngine.class),
                guestEnabled, 5, 30, 10);
    }

    GuestInquiryRequest inquiry() {
        return GuestInquiryRequest.builder().name("A").email("a@example.com").message("Hi").build();
    }

    @Test
    void whenGuestContactIsOffBothRoutesAreRefusedBeforeAnythingElse() {
        GuestContactService off = service(false);
        assertThatThrownBy(() -> off.inquire(1L, inquiry(), "1.2.3.4")).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> off.revealContact(1L, new GuestContactRequest(), "1.2.3.4"))
                .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(limits, properties, inquiries);
    }

    @Test
    void aFailedHumanCheckStopsBeforeTheListingIsTouched() {
        when(limits.tryConsume(anyString(), anyInt(), any())).thenReturn(0L);
        when(turnstile.verify(any(), anyString())).thenReturn(false);
        GuestContactService on = service(true);
        assertThatThrownBy(() -> on.inquire(1L, inquiry(), "1.2.3.4")).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> on.revealContact(1L, null, "1.2.3.4")).isInstanceOf(BadRequestException.class);
        verifyNoInteractions(properties, inquiries);
    }
}
