package com.propertyapp.service.rating;

import com.propertyapp.dto.common.PageResponse;
import com.propertyapp.dto.rating.CreateRatingRequest;
import com.propertyapp.dto.rating.RatingDTO;
import com.propertyapp.entity.inquiry.InquiryStatus;
import com.propertyapp.entity.property.Property;
import com.propertyapp.entity.realtor.RealtorRating;
import com.propertyapp.entity.user.User;
import com.propertyapp.exception.BadRequestException;
import com.propertyapp.exception.BusinessException;
import com.propertyapp.exception.ResourceNotFoundException;
import com.propertyapp.repository.inquiry.InquiryRepository;
import com.propertyapp.repository.property.PropertyRepository;
import com.propertyapp.repository.realtor.RealtorRatingRepository;
import com.propertyapp.repository.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class RealtorRatingServiceImpl implements RealtorRatingService {

    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    @Value("${app.rating.eligibility-seconds:86400}")
    private int eligibilitySeconds;

    private final RealtorRatingRepository ratingRepository;
    private final InquiryRepository       inquiryRepository;
    private final UserRepository          userRepository;
    private final PropertyRepository      propertyRepository;

    @Override
    @Transactional
    public RatingDTO submitRating(Long realtorId, Long raterId, CreateRatingRequest request) {
        Long propertyId = request.getPropertyId();

        if (!isEligibleToRate(realtorId, raterId, propertyId)) {
            throw new BadRequestException(
                    "You can only rate a realtor after your enquiry has been acknowledged " +
                    "(or after the eligibility period from your first contact)");
        }

        User realtor = requireActiveRealtor(realtorId);
        User rater   = userRepository.findByIdAndDeletedAtIsNull(raterId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", raterId));
        Property property = propertyRepository.findByIdAndDeletedAtIsNull(propertyId)
                .orElseThrow(() -> new ResourceNotFoundException("Property", "id", propertyId));

        // Upsert: one rating per buyer–realtor pair (property is stored as context of last submission)
        RealtorRating rating = ratingRepository
                .findByRealtor_IdAndRater_Id(realtorId, raterId)
                .orElseGet(() -> RealtorRating.builder()
                        .realtor(realtor)
                        .rater(rater)
                        .build());
        rating.setProperty(property); // always record which property triggered this rating/update

        rating.setRating(request.getRating());
        rating.setComment(request.getComment() != null ? request.getComment().strip() : null);
        return toDTO(ratingRepository.save(rating));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<RatingDTO> getMyRating(Long realtorId, Long raterId, Long propertyId) {
        requireActiveRealtor(realtorId);
        if (!isEligibleToRate(realtorId, raterId, propertyId)) {
            throw new BusinessException("You are not yet eligible to rate this realtor. " +
                    "Contact the realtor via enquiry first, then rate once your enquiry is acknowledged.");
        }
        return ratingRepository
                .findByRealtor_IdAndRater_Id(realtorId, raterId)
                .map(this::toDTO);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<RatingDTO> getRatings(Long realtorId, int page, int size) {
        requireActiveRealtor(realtorId);
        int safeSize = Math.min(size, 50);
        return PageResponse.of(
                ratingRepository.findByRealtorIdWithRater(realtorId, PageRequest.of(page, safeSize))
                        .map(this::toDTO));
    }

    // ── private helpers ───────────────────────────────────────────────────────

    private boolean isEligibleToRate(Long realtorId, Long raterId, Long propertyId) {
        LocalDateTime cutoff = LocalDateTime.now().minusSeconds(eligibilitySeconds);
        return inquiryRepository.existsRatingEligibleInquiry(
                realtorId, raterId, propertyId, cutoff, InquiryStatus.CONTACTED, InquiryStatus.CLOSED);
    }

    private User requireActiveRealtor(Long realtorId) {
        User realtor = userRepository.findByIdAndDeletedAtIsNull(realtorId)
                .orElseThrow(() -> new ResourceNotFoundException("Realtor", "id", realtorId));
        boolean isRealtor = realtor.getRoles().stream()
                .anyMatch(r -> "REALTOR".equals(r.getName()));
        if (!realtor.isActive() || !isRealtor) {
            throw new ResourceNotFoundException("Realtor", "id", realtorId);
        }
        return realtor;
    }

    private RatingDTO toDTO(RealtorRating r) {
        User rater = r.getRater();
        return RatingDTO.builder()
                .id(r.getId())
                .realtorId(r.getRealtor().getId())
                .propertyId(r.getProperty().getId())
                .raterId(rater.getId())
                .raterName((rater.getFirstName() + " " + rater.getLastName()).trim())
                .raterPhotoUrl(rater.getProfileImageUrl())
                .rating(r.getRating())
                .comment(r.getComment())
                .createdAt(r.getCreatedAt() != null ? r.getCreatedAt().format(ISO) : null)
                .updatedAt(r.getUpdatedAt() != null ? r.getUpdatedAt().format(ISO) : null)
                .build();
    }
}
