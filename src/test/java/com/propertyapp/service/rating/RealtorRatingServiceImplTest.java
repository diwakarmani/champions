package com.propertyapp.service.rating;

import com.propertyapp.dto.rating.CreateRatingRequest;
import com.propertyapp.dto.rating.RatingDTO;
import com.propertyapp.entity.inquiry.InquiryStatus;
import com.propertyapp.entity.property.Property;
import com.propertyapp.entity.realtor.RealtorRating;
import com.propertyapp.entity.user.Role;
import com.propertyapp.entity.user.User;
import com.propertyapp.exception.BadRequestException;
import com.propertyapp.exception.BusinessException;
import com.propertyapp.exception.ResourceNotFoundException;
import com.propertyapp.repository.inquiry.InquiryRepository;
import com.propertyapp.repository.property.PropertyRepository;
import com.propertyapp.repository.realtor.RealtorRatingRepository;
import com.propertyapp.repository.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RealtorRatingServiceImplTest {

    @Mock RealtorRatingRepository  ratingRepository;
    @Mock InquiryRepository        inquiryRepository;
    @Mock UserRepository           userRepository;
    @Mock PropertyRepository       propertyRepository;

    @InjectMocks RealtorRatingServiceImpl service;

    private static final long REALTOR_ID  = 10L;
    private static final long BUYER_ID    = 20L;
    private static final long PROPERTY_ID = 30L;

    private User     realtor;
    private User     buyer;
    private Property property;

    @BeforeEach
    void setup() {
        realtor = User.builder()
                .firstName("Jane").lastName("Realtor")
                .email("jane@realty.com")
                .isActive(true)
                .roles(Set.of(Role.builder().name("REALTOR").build()))
                .build();
        realtor.setId(REALTOR_ID);

        buyer = User.builder()
                .firstName("Bob").lastName("Buyer")
                .email("bob@buyer.com")
                .isActive(true)
                .roles(Set.of(Role.builder().name("BUYER").build()))
                .build();
        buyer.setId(BUYER_ID);

        property = Property.builder()
                .title("Test Property")
                .owner(realtor)
                .build();
        property.setId(PROPERTY_ID);
    }

    // ── submitRating ─────────────────────────────────────────────────────────────

    @Nested
    class SubmitRating {

        @Test
        void createsNewRating_whenEligible() {
            givenRealtorExists();
            givenBuyerExists();
            givenPropertyExists();
            givenBuyerIsEligible(true);
            when(ratingRepository.findByRealtor_IdAndRater_Id(REALTOR_ID, BUYER_ID))
                    .thenReturn(Optional.empty());

            RealtorRating saved = buildRating(5, "Great!");
            when(ratingRepository.save(any())).thenReturn(saved);

            RatingDTO dto = service.submitRating(REALTOR_ID, BUYER_ID, req(5, "Great!"));

            assertThat(dto.getRating()).isEqualTo(5);
            assertThat(dto.getComment()).isEqualTo("Great!");
            verify(ratingRepository).save(any(RealtorRating.class));
        }

        @Test
        void updatesExistingRating_onSecondSubmit() {
            givenRealtorExists();
            givenBuyerExists();
            givenPropertyExists();
            givenBuyerIsEligible(true);

            RealtorRating existing = buildRating(3, "OK");
            when(ratingRepository.findByRealtor_IdAndRater_Id(REALTOR_ID, BUYER_ID))
                    .thenReturn(Optional.of(existing));

            RealtorRating updated = buildRating(5, "Changed mind");
            when(ratingRepository.save(existing)).thenReturn(updated);

            RatingDTO dto = service.submitRating(REALTOR_ID, BUYER_ID, req(5, "Changed mind"));

            assertThat(dto.getRating()).isEqualTo(5);
            verify(ratingRepository, times(1)).save(existing);
        }

        @Test
        void stripsWhitespaceFromComment() {
            givenRealtorExists();
            givenBuyerExists();
            givenPropertyExists();
            givenBuyerIsEligible(true);
            when(ratingRepository.findByRealtor_IdAndRater_Id(REALTOR_ID, BUYER_ID))
                    .thenReturn(Optional.empty());

            ArgumentCaptor<RealtorRating> captor = ArgumentCaptor.forClass(RealtorRating.class);
            when(ratingRepository.save(captor.capture())).thenReturn(buildRating(4, "Good"));

            service.submitRating(REALTOR_ID, BUYER_ID, req(4, "  Good  "));

            assertThat(captor.getValue().getComment()).isEqualTo("Good");
        }

        @Test
        void rejectsSubmit_whenNotEligible() {
            givenBuyerIsEligible(false);

            assertThatThrownBy(() -> service.submitRating(REALTOR_ID, BUYER_ID, req(4, null)))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("eligibility period");
        }

        @Test
        void rejectsSubmit_whenRealtorDoesNotExist() {
            when(userRepository.findByIdAndDeletedAtIsNull(REALTOR_ID)).thenReturn(Optional.empty());
            givenBuyerIsEligible(true);

            assertThatThrownBy(() -> service.submitRating(REALTOR_ID, BUYER_ID, req(3, null)))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        void rejectsSubmit_whenUserIsNotRealtor() {
            User nonRealtor = User.builder()
                    .firstName("John").lastName("Seller")
                    .isActive(true)
                    .roles(Set.of(Role.builder().name("SELLER").build()))
                    .build();
            nonRealtor.setId(REALTOR_ID);
            when(userRepository.findByIdAndDeletedAtIsNull(REALTOR_ID)).thenReturn(Optional.of(nonRealtor));
            givenBuyerIsEligible(true);

            assertThatThrownBy(() -> service.submitRating(REALTOR_ID, BUYER_ID, req(5, null)))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // ── getMyRating ──────────────────────────────────────────────────────────────

    @Nested
    class GetMyRating {

        @Test
        void throwsBusinessException_whenNotEligible() {
            givenRealtorExists();
            givenBuyerIsEligible(false);

            assertThatThrownBy(() -> service.getMyRating(REALTOR_ID, BUYER_ID, PROPERTY_ID))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        void returnsEmpty_whenEligibleButNeverRated() {
            givenRealtorExists();
            givenBuyerIsEligible(true);
            when(ratingRepository.findByRealtor_IdAndRater_Id(REALTOR_ID, BUYER_ID))
                    .thenReturn(Optional.empty());

            assertThat(service.getMyRating(REALTOR_ID, BUYER_ID, PROPERTY_ID)).isEmpty();
        }

        @Test
        void returnsRating_whenEligibleAndAlreadyRated() {
            givenRealtorExists();
            givenBuyerIsEligible(true);
            when(ratingRepository.findByRealtor_IdAndRater_Id(REALTOR_ID, BUYER_ID))
                    .thenReturn(Optional.of(buildRating(4, "Nice")));

            assertThat(service.getMyRating(REALTOR_ID, BUYER_ID, PROPERTY_ID)).isPresent()
                    .hasValueSatisfying(dto -> {
                        assertThat(dto.getRating()).isEqualTo(4);
                        assertThat(dto.getComment()).isEqualTo("Nice");
                    });
        }
    }

    // ── Eligibility edge cases ────────────────────────────────────────────────────

    @Nested
    class EligibilityEdgeCases {

        @Test
        void eligibleImmediately_whenInquiryStatusIsContacted() {
            givenRealtorExists();
            givenBuyerExists();
            givenPropertyExists();
            givenBuyerIsEligible(true);
            when(ratingRepository.findByRealtor_IdAndRater_Id(REALTOR_ID, BUYER_ID))
                    .thenReturn(Optional.empty());
            when(ratingRepository.save(any())).thenReturn(buildRating(5, null));

            assertThatNoException().isThrownBy(() -> service.submitRating(REALTOR_ID, BUYER_ID, req(5, null)));
        }

        @Test
        void ineligible_whenInquiryIsNewAndLessThan30s() {
            givenBuyerIsEligible(false);

            assertThatThrownBy(() -> service.submitRating(REALTOR_ID, BUYER_ID, req(5, null)))
                    .isInstanceOf(BadRequestException.class);
        }
    }

    // ── helpers ──────────────────────────────────────────────────────────────────

    private void givenRealtorExists() {
        when(userRepository.findByIdAndDeletedAtIsNull(REALTOR_ID)).thenReturn(Optional.of(realtor));
    }

    private void givenBuyerExists() {
        when(userRepository.findByIdAndDeletedAtIsNull(BUYER_ID)).thenReturn(Optional.of(buyer));
    }

    private void givenPropertyExists() {
        when(propertyRepository.findByIdAndDeletedAtIsNull(PROPERTY_ID)).thenReturn(Optional.of(property));
    }

    private void givenBuyerIsEligible(boolean eligible) {
        when(inquiryRepository.existsRatingEligibleInquiry(
                eq(REALTOR_ID), eq(BUYER_ID), eq(PROPERTY_ID), any(LocalDateTime.class),
                eq(InquiryStatus.CONTACTED), eq(InquiryStatus.CLOSED)))
                .thenReturn(eligible);
    }

    private RealtorRating buildRating(int stars, String comment) {
        RealtorRating r = RealtorRating.builder()
                .realtor(realtor).rater(buyer).property(property)
                .rating(stars).comment(comment)
                .build();
        r.setId(99L);
        return r;
    }

    private CreateRatingRequest req(int stars, String comment) {
        CreateRatingRequest r = new CreateRatingRequest();
        r.setPropertyId(PROPERTY_ID);
        r.setRating(stars);
        r.setComment(comment);
        return r;
    }
}
