package com.propertyapp.controller;

import com.propertyapp.dto.common.ApiResponse;
import com.propertyapp.dto.common.PageResponse;
import com.propertyapp.dto.inquiry.CreateInquiryRequest;
import com.propertyapp.dto.inquiry.InquiryDTO;
import com.propertyapp.entity.inquiry.Inquiry;
import com.propertyapp.entity.inquiry.InquiryStatus;
import com.propertyapp.entity.property.Property;
import com.propertyapp.entity.user.User;
import com.propertyapp.enums.NotificationType;
import com.propertyapp.exception.BadRequestException;
import com.propertyapp.exception.ResourceNotFoundException;
import com.propertyapp.exception.UnauthorizedException;
import com.propertyapp.repository.inquiry.InquiryRepository;
import com.propertyapp.repository.property.PropertyRepository;
import com.propertyapp.repository.user.UserRepository;
import com.propertyapp.service.notification.NotificationService;
import com.propertyapp.util.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.format.DateTimeFormatter;

@RestController
@RequestMapping("/api/inquiries")
@RequiredArgsConstructor
@Tag(name = "Inquiries", description = "Send and manage property inquiries")
@SecurityRequirement(name = "Bearer Authentication")
public class InquiryController {

    private final InquiryRepository inquiryRepository;
    private final PropertyRepository propertyRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    // ── POST /api/inquiries ──────────────────────────────────────────────────

    @PostMapping
    @Transactional
    @Operation(summary = "Send an inquiry about a property")
    public ResponseEntity<ApiResponse<InquiryDTO>> createInquiry(
            @Valid @RequestBody CreateInquiryRequest request) {

        Long userId = SecurityUtils.getCurrentUserId()
                .orElseThrow(() -> new UnauthorizedException("Authentication required"));

        Property property = propertyRepository.findById(request.getPropertyId())
                .orElseThrow(() -> new ResourceNotFoundException("Property not found"));

        User inquirer = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        property.incrementInquiryCount();

        Inquiry inquiry = Inquiry.builder()
                .property(property)
                .inquirer(inquirer)
                .name(request.getName())
                .email(request.getEmail())
                .phone(request.getPhone())
                .message(request.getMessage())
                .status(InquiryStatus.NEW)
                .build();

        Inquiry saved = inquiryRepository.save(inquiry);

        notificationService.send(
                property.getOwner().getId(),
                NotificationType.INQUIRY_RECEIVED,
                "New enquiry on your listing!",
                request.getName() + " sent you an enquiry about \"" + property.getTitle() + "\".",
                "PROPERTY",
                property.getId()
        );

        return ResponseEntity.ok(ApiResponse.success("Inquiry sent successfully", toDTO(saved)));
    }

    // ── GET /api/inquiries/received ──────────────────────────────────────────

    @GetMapping("/received")
    @Transactional(readOnly = true)
    @Operation(summary = "Get inquiries received on my properties (Seller / Realtor)")
    public ResponseEntity<ApiResponse<PageResponse<InquiryDTO>>> getReceived(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Long userId = SecurityUtils.getCurrentUserId()
                .orElseThrow(() -> new UnauthorizedException("Authentication required"));

        Page<Inquiry> inquiries = inquiryRepository.findReceivedByOwnerId(
                userId, PageRequest.of(page, size));

        return ResponseEntity.ok(ApiResponse.success(
                "Inquiries retrieved", PageResponse.of(inquiries.map(this::toDTO))));
    }

    // ── GET /api/inquiries/sent ──────────────────────────────────────────────

    @GetMapping("/sent")
    @Transactional(readOnly = true)
    @Operation(summary = "Get inquiries I sent (Buyer)")
    public ResponseEntity<ApiResponse<PageResponse<InquiryDTO>>> getSent(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Long userId = SecurityUtils.getCurrentUserId()
                .orElseThrow(() -> new UnauthorizedException("Authentication required"));

        Page<Inquiry> inquiries = inquiryRepository.findSentByInquirerId(
                userId, PageRequest.of(page, size));

        return ResponseEntity.ok(ApiResponse.success(
                "Sent inquiries retrieved", PageResponse.of(inquiries.map(this::toDTO))));
    }

    // ── PATCH /api/inquiries/{id}/status ────────────────────────────────────

    @PatchMapping("/{id}/status")
    @Transactional
    @Operation(summary = "Update inquiry status (Seller / Realtor)")
    public ResponseEntity<ApiResponse<InquiryDTO>> updateStatus(
            @PathVariable Long id,
            @RequestParam String status) {

        Long userId = SecurityUtils.getCurrentUserId()
                .orElseThrow(() -> new UnauthorizedException("Authentication required"));

        Inquiry inquiry = inquiryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Inquiry not found"));

        if (!inquiry.getProperty().getOwner().getId().equals(userId)) {
            throw new UnauthorizedException("You can only update inquiries on your own properties");
        }

        InquiryStatus newStatus;
        try {
            newStatus = InquiryStatus.valueOf(status.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid status. Must be NEW, CONTACTED, or CLOSED");
        }

        inquiry.setStatus(newStatus);
        return ResponseEntity.ok(ApiResponse.success("Status updated", toDTO(inquiry)));
    }

    // ── Helper ───────────────────────────────────────────────────────────────

    private InquiryDTO toDTO(Inquiry i) {
        var owner = i.getProperty().getOwner();
        boolean ownerIsRealtor = owner != null && owner.getRoles().stream()
                .anyMatch(r -> "REALTOR".equals(r.getName()));

        return InquiryDTO.builder()
                .id(i.getId())
                .propertyId(i.getProperty().getId())
                .propertyTitle(i.getProperty().getTitle())
                .inquirerId(i.getInquirer() != null ? i.getInquirer().getId() : null)
                .realtorId(ownerIsRealtor ? owner.getId() : null)
                .realtorName(ownerIsRealtor
                        ? (owner.getFirstName() + " " + owner.getLastName()).trim()
                        : null)
                .name(i.getName())
                .email(i.getEmail())
                .phone(i.getPhone())
                .message(i.getMessage())
                .status(i.getStatus().name())
                .createdAt(i.getCreatedAt() != null ? i.getCreatedAt().format(ISO) : null)
                .build();
    }
}
