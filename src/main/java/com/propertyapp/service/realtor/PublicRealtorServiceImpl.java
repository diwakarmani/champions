package com.propertyapp.service.realtor;

import com.propertyapp.dto.property.ConnectRealtorRequest;
import com.propertyapp.dto.property.ConnectRealtorResponse;
import com.propertyapp.dto.property.RealtorProfileDTO;
import com.propertyapp.entity.property.Property;
import com.propertyapp.entity.user.User;
import com.propertyapp.exception.BadRequestException;
import com.propertyapp.exception.ResourceNotFoundException;
import com.propertyapp.repository.property.PropertyRepository;
import com.propertyapp.repository.realtor.RealtorRatingRepository;
import com.propertyapp.repository.realtor.RealtorUserInteractionRepository;
import com.propertyapp.repository.user.UserRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PublicRealtorServiceImpl implements PublicRealtorService {

    private static final String CONNECT_CLICKED = "CONNECT_CLICKED";

    private final UserRepository userRepository;
    private final PropertyRepository propertyRepository;
    private final RealtorUserInteractionRepository interactionRepository;
    private final RealtorRatingRepository ratingRepository;
    private final JdbcTemplate jdbcTemplate;

    @Override
    @Transactional(readOnly = true)
    public RealtorProfileDTO getProfile(Long realtorId) {
        User realtor = requireActiveRealtor(realtorId);
        long activeListings = propertyRepository.countByOwnerAndStatus(realtorId, "ACTIVE");
        long connectedUsers;
        try {
            connectedUsers = interactionRepository.countDistinctUsersByRealtorId(realtorId);
        } catch (Exception e) {
            connectedUsers = 0;
        }

        long ratingCount   = ratingRepository.countByRealtor_Id(realtorId);
        Double rawAverage  = ratingRepository.findAverageRatingByRealtorId(realtorId).orElse(null);
        // Round to 1 decimal place (e.g. 4.2666… → 4.3)
        Double ratingAvg   = rawAverage == null ? null
                : BigDecimal.valueOf(rawAverage).setScale(1, RoundingMode.HALF_UP).doubleValue();

        return RealtorProfileDTO.builder()
                .id(realtor.getId())
                .name((realtor.getFirstName() + " " + realtor.getLastName()).trim())
                .profilePhotoUrl(realtor.getProfileImageUrl())
                .phone(realtor.getPhone())
                .email(realtor.getEmail())
                .bio(realtor.getBio())
                .areasServed(propertyRepository.findDistinctActiveCitiesByOwnerId(realtorId))
                .languages(List.of())
                .ratingAverage(ratingAvg)
                .ratingCount(ratingCount)
                .totalUserInteractions(connectedUsers)
                .activeListingsCount(activeListings)
                .verificationStatus(realtor.isEmailVerified() || realtor.isMobileVerified() ? "VERIFIED" : "UNVERIFIED")
                .createdAt(realtor.getCreatedAt())
                .updatedAt(realtor.getUpdatedAt())
                .build();
    }

    @Override
    @Transactional
    public ConnectRealtorResponse connect(Long realtorId, Long userId, ConnectRealtorRequest request) {
        requireActiveRealtor(realtorId);
        userRepository.findByIdAndDeletedAtIsNull(userId)
                .filter(User::isActive)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        if (realtorId.equals(userId)) {
            throw new BadRequestException("You cannot connect with your own realtor profile");
        }

        Long propertyId = validateProperty(realtorId, request.getPropertyId());
        Long interactionId = jdbcTemplate.queryForObject("""
                INSERT INTO realtor_user_interactions
                    (version, created_at, updated_at, created_by, updated_by,
                     interaction_type, message, property_id, realtor_id, user_id)
                VALUES
                    (0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT ON CONSTRAINT uk_realtor_user_interaction_type
                DO UPDATE SET
                    updated_at = CURRENT_TIMESTAMP,
                    updated_by = EXCLUDED.updated_by,
                    property_id = COALESCE(EXCLUDED.property_id, realtor_user_interactions.property_id),
                    message = COALESCE(NULLIF(EXCLUDED.message, ''), realtor_user_interactions.message)
                RETURNING id
                """, Long.class, userId, userId, CONNECT_CLICKED, request.getMessage(), propertyId, realtorId, userId);

        long totalUsers = interactionRepository.countDistinctUsersByRealtorId(realtorId);
        return ConnectRealtorResponse.builder()
                .success(true)
                .interactionId(interactionId)
                .realtorId(realtorId)
                .propertyId(propertyId)
                .totalUserInteractions(totalUsers)
                .build();
    }

    private User requireActiveRealtor(Long realtorId) {
        User realtor = userRepository.findByIdAndDeletedAtIsNull(realtorId)
                .orElseThrow(() -> new ResourceNotFoundException("Realtor", "id", realtorId));
        boolean hasRealtorRole = realtor.getRoles().stream().anyMatch(
                role -> "REALTOR".equals(role.getName()) || "REALTOR_GROUP_ADMIN".equals(role.getName()));
        if (!realtor.isActive() || !hasRealtorRole) {
            throw new ResourceNotFoundException("Realtor", "id", realtorId);
        }
        return realtor;
    }

    private Long validateProperty(Long realtorId, Long propertyId) {
        if (propertyId == null) return null;
        Property property = propertyRepository.findByIdAndDeletedAtIsNull(propertyId)
                .orElseThrow(() -> new ResourceNotFoundException("Property", "id", propertyId));
        if (!property.getOwner().getId().equals(realtorId)) {
            throw new BadRequestException("Property is not listed by this realtor");
        }
        if (!"ACTIVE".equals(property.getStatus())) {
            throw new BadRequestException("Connections can only reference active properties");
        }
        return propertyId;
    }
}
