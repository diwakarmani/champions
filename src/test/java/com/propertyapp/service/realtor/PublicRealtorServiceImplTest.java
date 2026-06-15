package com.propertyapp.service.realtor;

import com.propertyapp.entity.user.Role;
import com.propertyapp.entity.user.User;
import com.propertyapp.exception.ResourceNotFoundException;
import com.propertyapp.repository.property.PropertyRepository;
import com.propertyapp.repository.realtor.RealtorUserInteractionRepository;
import com.propertyapp.repository.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PublicRealtorServiceImplTest {

    @Mock UserRepository userRepository;
    @Mock PropertyRepository propertyRepository;
    @Mock RealtorUserInteractionRepository interactionRepository;
    @Mock JdbcTemplate jdbcTemplate;
    @InjectMocks PublicRealtorServiceImpl service;

    @Test
    void mapsPersistedRealtorTrustDetails() {
        Role role = Role.builder().name("REALTOR").build();
        User realtor = User.builder()
                .email("agent@example.com")
                .firstName("Rhea")
                .lastName("Agent")
                .phone("+15125550100")
                .bio("Austin specialist")
                .isActive(true)
                .emailVerified(true)
                .roles(Set.of(role))
                .build();
        realtor.setId(22L);
        when(userRepository.findByIdAndDeletedAtIsNull(22L)).thenReturn(Optional.of(realtor));
        when(propertyRepository.countByOwnerAndStatus(22L, "ACTIVE")).thenReturn(3L);
        when(propertyRepository.findDistinctActiveCitiesByOwnerId(22L)).thenReturn(List.of("Austin", "Round Rock"));
        when(interactionRepository.countDistinctUsersByRealtorId(22L)).thenReturn(4L);

        var profile = service.getProfile(22L);

        assertThat(profile.getName()).isEqualTo("Rhea Agent");
        assertThat(profile.getVerificationStatus()).isEqualTo("VERIFIED");
        assertThat(profile.getActiveListingsCount()).isEqualTo(3);
        assertThat(profile.getTotalUserInteractions()).isEqualTo(4);
        assertThat(profile.getAreasServed()).containsExactly("Austin", "Round Rock");
    }

    @Test
    void rejectsUsersWithoutTheRealtorRole() {
        User buyer = User.builder()
                .email("buyer@example.com")
                .firstName("Demo")
                .lastName("Buyer")
                .isActive(true)
                .roles(Set.of(Role.builder().name("BUYER").build()))
                .build();
        buyer.setId(9L);
        when(userRepository.findByIdAndDeletedAtIsNull(9L)).thenReturn(Optional.of(buyer));

        assertThatThrownBy(() -> service.getProfile(9L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Realtor not found");
    }
}
