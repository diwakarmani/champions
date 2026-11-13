package com.propertyapp.service.contact;

import com.propertyapp.dto.publicapi.GuestContactRequest;
import com.propertyapp.dto.publicapi.GuestInquiryReceipt;
import com.propertyapp.dto.publicapi.GuestInquiryRequest;
import com.propertyapp.dto.publicapi.PublicContactDTO;
import com.propertyapp.entity.inquiry.Inquiry;
import com.propertyapp.entity.inquiry.InquiryStatus;
import com.propertyapp.entity.property.Property;
import com.propertyapp.entity.property.PropertyContactEvent;
import com.propertyapp.entity.user.User;
import com.propertyapp.enums.NotificationType;
import com.propertyapp.exception.BadRequestException;
import com.propertyapp.exception.RateLimitExceededException;
import com.propertyapp.exception.ResourceNotFoundException;
import com.propertyapp.repository.inquiry.InquiryRepository;
import com.propertyapp.repository.property.PropertyContactEventRepository;
import com.propertyapp.repository.property.PropertyRepository;
import com.propertyapp.security.ratelimit.RateLimitStore;
import com.propertyapp.service.communication.EmailService;
import com.propertyapp.service.notification.NotificationService;
import com.propertyapp.service.property.PropertyVisibility;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Locale;

/**
 * Contact from visitors without an account (ADR-0007, Amendment 1). Checks run in a fixed order:
 * guest flag, per-IP limit, honeypot, Turnstile, listing, per-email limit, dedupe.
 */
@Service
@Slf4j
public class GuestContactService {

    private final PropertyRepository propertyRepository;
    private final InquiryRepository inquiryRepository;
    private final PropertyContactEventRepository contactEventRepository;
    private final RateLimitStore rateLimits;
    private final TurnstileVerifier turnstile;
    private final NotificationService notificationService;
    private final EmailService emailService;
    private final TemplateEngine templateEngine;
    private final boolean guestEnabled;
    private final int inquiriesPerIpPerHour;
    private final int contactsPerIpPerHour;
    private final int inquiriesPerEmailPerDay;

    public GuestContactService(
            PropertyRepository propertyRepository,
            InquiryRepository inquiryRepository,
            PropertyContactEventRepository contactEventRepository,
            RateLimitStore rateLimits,
            TurnstileVerifier turnstile,
            NotificationService notificationService,
            EmailService emailService,
            TemplateEngine templateEngine,
            @Value("${app.contact.guest-enabled:true}") boolean guestEnabled,
            @Value("${app.rate-limit.guest-inquiries-per-ip-per-hour:5}") int inquiriesPerIpPerHour,
            @Value("${app.rate-limit.guest-contacts-per-ip-per-hour:30}") int contactsPerIpPerHour,
            @Value("${app.rate-limit.guest-inquiries-per-email-per-day:10}") int inquiriesPerEmailPerDay) {
        this.propertyRepository = propertyRepository;
        this.inquiryRepository = inquiryRepository;
        this.contactEventRepository = contactEventRepository;
        this.rateLimits = rateLimits;
        this.turnstile = turnstile;
        this.notificationService = notificationService;
        this.emailService = emailService;
        this.templateEngine = templateEngine;
        this.guestEnabled = guestEnabled;
        this.inquiriesPerIpPerHour = inquiriesPerIpPerHour;
        this.contactsPerIpPerHour = contactsPerIpPerHour;
        this.inquiriesPerEmailPerDay = inquiriesPerEmailPerDay;
    }

    @Transactional
    public GuestInquiryReceipt inquire(Long propertyId, GuestInquiryRequest request, String clientIp) {
        requireGuestContactEnabled();
        limit("guest-inquiry-ip:" + clientIp, inquiriesPerIpPerHour, Duration.ofHours(1));

        if (StringUtils.hasText(request.getWebsite())) {
            log.info("Guest inquiry honeypot filled for property {}; dropped", propertyId);
            return GuestInquiryReceipt.accepted();
        }
        requireHuman(request.getTurnstileToken(), clientIp);

        Property property = contactableProperty(propertyId);
        String email = request.getEmail().trim().toLowerCase(Locale.ROOT);
        limit("guest-inquiry-email:" + email, inquiriesPerEmailPerDay, Duration.ofDays(1));

        if (inquiryRepository.existsRecentByPropertyAndEmail(propertyId, email, LocalDateTime.now().minusHours(24))) {
            log.info("Duplicate guest inquiry for property {} within 24 hours; not stored again", propertyId);
            return GuestInquiryReceipt.accepted();
        }

        String name = request.getName().trim();
        String phone = StringUtils.hasText(request.getPhone()) ? request.getPhone().trim() : null;
        inquiryRepository.save(Inquiry.builder()
                .property(property)
                .inquirer(null)
                .name(name)
                .email(email)
                .phone(phone)
                .message(request.getMessage().trim())
                .status(InquiryStatus.NEW)
                .source("MOBILE".equalsIgnoreCase(request.getSource()) ? "GUEST_MOBILE" : "GUEST_WEB")
                .build());
        property.incrementInquiryCount();
        propertyRepository.save(property);

        User agent = property.getOwner();
        Long agentId = agent.getId();
        String agentEmail = agent.getEmail();
        String agentFirstName = agent.getFirstName();
        String title = property.getTitle();
        String message = request.getMessage().trim();
        afterCommit(() -> {
            notificationService.send(agentId, NotificationType.INQUIRY_RECEIVED,
                    "New enquiry on your listing!",
                    name + " sent you an enquiry about \"" + title + "\".",
                    "PROPERTY", propertyId);
            emailService.sendEmail(agentEmail, "New enquiry: " + title, render(agentFirstName,
                    "New enquiry on your listing",
                    "From: " + name + " (" + email + (phone != null ? ", " + phone : "") + ")\n"
                            + "Listing: " + title + "\n\n" + message));
            emailService.sendEmail(email, "We sent your enquiry", render(name,
                    "We sent your enquiry",
                    "The agent for \"" + title + "\" has your message and will reply to this email address."));
        });
        log.info("Guest inquiry stored for property {}", propertyId);
        return GuestInquiryReceipt.accepted();
    }

    @Transactional
    public PublicContactDTO revealContact(Long propertyId, GuestContactRequest request, String clientIp) {
        requireGuestContactEnabled();
        limit("guest-contact-ip:" + clientIp, contactsPerIpPerHour, Duration.ofHours(1));
        requireHuman(request == null ? null : request.getTurnstileToken(), clientIp);

        Property property = contactableProperty(propertyId);
        contactEventRepository.save(PropertyContactEvent.builder()
                .property(property)
                .contactedBy(null)
                .build());
        property.incrementContactCount();
        propertyRepository.save(property);

        User agent = property.getOwner();
        return PublicContactDTO.builder()
                .agentId(agent.getId())
                .agentName((agent.getFirstName() + " " + agent.getLastName()).trim())
                .phone(agent.getPhone())
                .email(agent.getEmail())
                .build();
    }

    private void requireGuestContactEnabled() {
        if (!guestEnabled) {
            throw new AccessDeniedException("Please sign in to contact the agent");
        }
    }

    private void limit(String key, int capacity, Duration window) {
        long retryAfter = rateLimits.tryConsume(key, capacity, window);
        if (retryAfter > 0) {
            throw new RateLimitExceededException(retryAfter);
        }
    }

    private void requireHuman(String token, String clientIp) {
        if (!turnstile.verify(token, clientIp)) {
            throw new BadRequestException("Please complete the verification and try again");
        }
    }

    private Property contactableProperty(Long propertyId) {
        Property property = propertyRepository.findByIdAndDeletedAtIsNull(propertyId)
                .orElseThrow(() -> new ResourceNotFoundException("Property", "id", propertyId));
        PropertyVisibility.requireContactable(property);
        return property;
    }

    private String render(String firstName, String title, String message) {
        Context context = new Context();
        context.setVariable("firstName", firstName);
        context.setVariable("title", title);
        context.setVariable("message", message);
        return templateEngine.process("email/notice-template", context);
    }

    /** Tell people only once the inquiry is saved; a failed notice must not fail the request. */
    private void afterCommit(Runnable action) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    action.run();
                } catch (RuntimeException e) {
                    log.error("Could not send guest inquiry notice: {}", e.getMessage(), e);
                }
            }
        });
    }
}
