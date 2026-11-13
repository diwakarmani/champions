package com.propertyapp.service.user;

import com.propertyapp.dto.user.DeleteAccountRequest;
import com.propertyapp.entity.user.User;
import com.propertyapp.exception.BadRequestException;
import com.propertyapp.exception.ConflictException;
import com.propertyapp.exception.ResourceNotFoundException;
import com.propertyapp.exception.UnauthorizedException;
import com.propertyapp.repository.agent.AgentApplicationRepository;
import com.propertyapp.repository.inquiry.InquiryRepository;
import com.propertyapp.repository.property.PropertyRepository;
import com.propertyapp.repository.user.UserFavoriteRepository;
import com.propertyapp.repository.user.UserRepository;
import com.propertyapp.service.auth.OtpService;
import com.propertyapp.util.Constants;
import com.propertyapp.util.PasswordUtils;
import com.propertyapp.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Self-service account deletion, required by the app stores (ADR-0008 Amendment 1). The row stays,
 * soft-deleted and anonymised, so listings, ratings and audit references keep working.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AccountDeletionService {

    static final String DELETED_NAME_FIRST = "Deleted";
    static final String DELETED_NAME_LAST = "user";

    private final UserRepository userRepository;
    private final PropertyRepository propertyRepository;
    private final UserFavoriteRepository favoriteRepository;
    private final InquiryRepository inquiryRepository;
    private final AgentApplicationRepository applicationRepository;
    private final OtpService otpService;

    /** A failed OTP attempt is still counted, so the code cannot be guessed by retrying. */
    @Transactional(noRollbackFor = BadRequestException.class)
    @CacheEvict(value = "users", allEntries = true)
    public void deleteMyAccount(DeleteAccountRequest request) {
        Long userId = SecurityUtils.getCurrentUserId()
                .orElseThrow(() -> new UnauthorizedException("User not authenticated"));
        User user = userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        confirm(user, request);

        if (hasRole(user, Constants.ROLE_SUPER_ADMIN)) {
            throw new ConflictException("An admin account must be removed by another admin");
        }
        long open = propertyRepository.countByOwnerAndStatus(userId, "ACTIVE")
                + propertyRepository.countByOwnerAndStatus(userId, "PENDING_APPROVAL");
        if (open > 0) {
            throw new ConflictException("You have " + open + " active or pending listing(s). "
                    + "Ask an admin to reassign them, or set them inactive, before deleting your account.");
        }

        String placeholderEmail = "deleted-" + userId + "@deleted.invalid";
        inquiryRepository.anonymiseSentBy(userId, placeholderEmail);
        favoriteRepository.deleteAllByUserId(userId);
        applicationRepository.closePendingForDeletedUser(userId, LocalDateTime.now());
        applicationRepository.anonymiseForDeletedUser(userId);

        user.setFirstName(DELETED_NAME_FIRST);
        user.setLastName(DELETED_NAME_LAST);
        user.setEmail(placeholderEmail);
        user.setPhone(null);
        user.setProfileImageUrl(null);
        user.setBio(null);
        user.setWebsite(null);
        user.setBrokerageName(null);
        user.setLicenseNumber(null);
        user.setLicenseState(null);
        user.setYearsExperience(null);
        user.setServiceAreas(null);
        user.setLicensePublic(false);
        user.setPasswordHash(PasswordUtils.hash(UUID.randomUUID().toString()));
        user.setEmailVerified(false);
        user.setMobileVerified(false);
        user.setActive(false);
        user.getAddresses().clear();
        user.markAsDeleted(userId);
        userRepository.save(user);

        log.info("User {} deleted their account; personal data anonymised", userId);
    }

    private void confirm(User user, DeleteAccountRequest request) {
        if (StringUtils.hasText(request.getPassword())) {
            if (!PasswordUtils.matches(request.getPassword(), user.getPasswordHash())) {
                throw new BadRequestException("Password is incorrect");
            }
            return;
        }
        if (StringUtils.hasText(request.getOtpIdentifier()) && StringUtils.hasText(request.getOtpCode())) {
            String identifier = request.getOtpIdentifier().trim();
            boolean own = identifier.equalsIgnoreCase(user.getEmail()) || identifier.equals(user.getPhone());
            if (!own) {
                throw new BadRequestException("The code must be sent to your own email or phone");
            }
            otpService.verifyContactChangeOtp(identifier, request.getOtpCode().trim());
            return;
        }
        throw new BadRequestException("Confirm with your password or a one-time code");
    }

    private boolean hasRole(User user, String role) {
        return user.getRoles().stream().anyMatch(r -> role.equals(r.getName()));
    }
}
