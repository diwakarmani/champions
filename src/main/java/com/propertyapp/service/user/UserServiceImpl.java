package com.propertyapp.service.user;

import com.propertyapp.dto.common.PageResponse;
import com.propertyapp.dto.user.*;
import com.propertyapp.entity.user.Role;
import com.propertyapp.entity.user.User;
import com.propertyapp.entity.user.UserAddress;
import com.propertyapp.exception.BadRequestException;
import com.propertyapp.exception.DuplicateResourceException;
import com.propertyapp.exception.ResourceNotFoundException;
import com.propertyapp.exception.UnauthorizedException;
import com.propertyapp.mapper.UserMapper;
import com.propertyapp.repository.user.RoleRepository;
import com.propertyapp.repository.user.UserAddressRepository;
import com.propertyapp.repository.user.UserRepository;
import com.propertyapp.service.auth.OtpService;
import com.propertyapp.util.PasswordUtils;
import com.propertyapp.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserServiceImpl implements UserService {

    // E.164: starts with +, then 1-3 digit country code, then 6-14 digits
    private static final Pattern E164_PATTERN = Pattern.compile("^\\+[1-9]\\d{6,14}$");
    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[A-Za-z0-9+_.-]+@(.+)$");

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserAddressRepository addressRepository;
    private final UserMapper userMapper;
    private final OtpService otpService;
    
    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public PageResponse<UserDTO> getAllUsers(Pageable pageable) {
        log.info("Fetching all users, page: {}", pageable.getPageNumber());
        
        Page<User> users = userRepository.findAllByDeletedAtIsNull(pageable);
        Page<UserDTO> userDTOs = users.map(userMapper::toDTO);
        
        return PageResponse.of(userDTOs);
    }
    
    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public PageResponse<UserDTO> getUsersByRole(String role, Pageable pageable) {
        log.info("Fetching users by role: {}, page: {}", role, pageable.getPageNumber());

        Page<User> users = userRepository.findByRole(role, pageable);
        Page<UserDTO> userDTOs = users.map(userMapper::toDTO);

        return PageResponse.of(userDTOs);
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public PageResponse<UserDTO> searchUsers(String search, Pageable pageable) {
        log.info("Searching users with query: {}", search);
        
        Page<User> users = userRepository.searchUsers(search, pageable);
        Page<UserDTO> userDTOs = users.map(userMapper::toDTO);
        
        return PageResponse.of(userDTOs);
    }
    
    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "users", key = "#id")
    public UserDTO getUserById(Long id) {
        log.info("Fetching user by id: {}", id);
        
        User user = userRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));
        
        return userMapper.toDTO(user);
    }
    
    @Override
    @Transactional(readOnly = true)
    public UserDTO getCurrentUser() {
        Long userId = SecurityUtils.getCurrentUserId()
                .orElseThrow(() -> new UnauthorizedException("User not authenticated"));
        
        return getUserById(userId);
    }
    
    @Override
    @Transactional
    @CacheEvict(value = "users", key = "#id")
    @PreAuthorize("hasRole('SUPER_ADMIN') or @securityUtils.getCurrentUserId().orElse(null) == #id")
    public UserDTO updateUser(Long id, UserUpdateRequest request) {
        log.info("Updating user: {}", id);
        
        User user = userRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));
        
        if (request.getFirstName() != null) {
            user.setFirstName(request.getFirstName());
        }
        if (request.getLastName() != null) {
            user.setLastName(request.getLastName());
        }
        if (request.getDateOfBirth() != null) {
            user.setDateOfBirth(request.getDateOfBirth().atStartOfDay());
        }
        if (request.getGender() != null) {
            user.setGender(request.getGender());
        }
        if (request.getBio() != null) {
            user.setBio(request.getBio());
        }
        if (request.getOccupation() != null) {
            user.setOccupation(request.getOccupation());
        }
        if (request.getWebsite() != null) {
            user.setWebsite(request.getWebsite());
        }

        user = userRepository.save(user);
        
        return userMapper.toDTO(user);
    }
    
    @Override
    @Transactional
    public UserDTO updateCurrentUser(UserUpdateRequest request) {
        Long userId = SecurityUtils.getCurrentUserId()
                .orElseThrow(() -> new UnauthorizedException("User not authenticated"));
        
        return updateUser(userId, request);
    }
    
    @Override
    @Transactional
    @CacheEvict(value = "users", key = "#id")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public void deleteUser(Long id) {
        log.info("Deleting user: {}", id);
        
        User user = userRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));
        
        Long currentUserId = SecurityUtils.getCurrentUserId().orElse(null);
        user.markAsDeleted(currentUserId);
        userRepository.save(user);
        
        log.info("User deleted successfully: {}", id);
    }
    
    @Override
    @Transactional
    public void changePassword(ChangePasswordRequest request) {
        Long userId = SecurityUtils.getCurrentUserId()
                .orElseThrow(() -> new UnauthorizedException("User not authenticated"));
        
        User user = userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));
        
        // Verify current password
        if (!PasswordUtils.matches(request.getCurrentPassword(), user.getPasswordHash())) {
            throw new BadRequestException("Current password is incorrect");
        }
        
        // Update password
        user.setPasswordHash(PasswordUtils.hash(request.getNewPassword()));
        userRepository.save(user);
        
        log.info("Password changed successfully for user: {}", userId);
    }
    
    @Override
    @Transactional
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public void assignRoles(Long userId, Set<String> roleNames) {
        log.info("Assigning roles to user {}: {}", userId, roleNames);

        User user = userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        boolean targetIsCurrentAdmin = user.getRoles().stream()
                .anyMatch(r -> "SUPER_ADMIN".equals(r.getName()));
        boolean removingAdminRole = !roleNames.contains("SUPER_ADMIN");

        if (targetIsCurrentAdmin && removingAdminRole) {
            // Prevent stripping SUPER_ADMIN when this is the last admin in the system
            long adminCount = userRepository.countByRole("SUPER_ADMIN");
            if (adminCount <= 1) {
                throw new BadRequestException(
                        "Cannot remove SUPER_ADMIN role: this is the only admin account in the system. " +
                        "Assign another admin first.");
            }

            // Prevent an admin from removing their own SUPER_ADMIN role
            Long callerId = SecurityUtils.getCurrentUserId().orElse(null);
            if (userId.equals(callerId)) {
                throw new BadRequestException(
                        "You cannot remove the SUPER_ADMIN role from your own account.");
            }
        }

        Set<Role> roles = roleRepository.findByNameIn(roleNames);
        user.setRoles(roles);
        userRepository.save(user);

        log.info("Roles assigned successfully to user: {}", userId);
    }
    
    @Override
    @Transactional
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @CacheEvict(value = "users", key = "#id")
    public UserDTO activateUser(Long id) {
        log.info("Activating user: {}", id);
        User user = userRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));
        user.setActive(true);
        user.setEmailVerified(true);
        return userMapper.toDTO(userRepository.save(user));
    }

    @Override
    @Transactional
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @CacheEvict(value = "users", key = "#id")
    public UserDTO deactivateUser(Long id) {
        log.info("Deactivating user: {}", id);
        User user = userRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));
        user.setActive(false);
        return userMapper.toDTO(userRepository.save(user));
    }

    @Override
    @Transactional
    public UserAddressDTO addAddress(UserAddressDTO addressDTO) {
        Long userId = SecurityUtils.getCurrentUserId()
                .orElseThrow(() -> new UnauthorizedException("User not authenticated"));
        
        User user = userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));
        
        UserAddress address = userMapper.toAddressEntity(addressDTO);
        address.setUser(user);
        
        address = addressRepository.save(address);
        
        return userMapper.toAddressDTO(address);
    }
    
    @Override
    @Transactional
    public void removeAddress(Long addressId) {
        Long userId = SecurityUtils.getCurrentUserId()
                .orElseThrow(() -> new UnauthorizedException("User not authenticated"));

        UserAddress address = addressRepository.findById(addressId)
                .orElseThrow(() -> new ResourceNotFoundException("Address", "id", addressId));

        if (!address.getUser().getId().equals(userId)) {
            throw new UnauthorizedException("You don't have permission to delete this address");
        }

        addressRepository.delete(address);
    }

    // ── Secure contact-change (OTP-gated) ────────────────────────────────────

    @Override
    @Transactional
    public void initiatePhoneChange(ContactChangeRequest request, String ipAddress) {
        Long userId = SecurityUtils.getCurrentUserId()
                .orElseThrow(() -> new UnauthorizedException("User not authenticated"));
        User user = userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        String newPhone = request.getNewContact().trim();

        if (!E164_PATTERN.matcher(newPhone).matches()) {
            throw new BadRequestException(
                    "Invalid phone format. Must include country code, e.g. +12025551234");
        }
        if (newPhone.equals(user.getPhone())) {
            throw new BadRequestException(
                    "New phone number is the same as your current phone number");
        }
        if (userRepository.existsByPhoneAndDeletedAtIsNull(newPhone)) {
            throw new DuplicateResourceException("User", "phone", newPhone);
        }

        otpService.sendContactChangeOtp(newPhone, ipAddress);
        log.info("Phone change OTP initiated for user {} → {}", userId, newPhone);
    }

    @Override
    @Transactional
    public UserDTO verifyPhoneChange(VerifyContactChangeRequest request) {
        Long userId = SecurityUtils.getCurrentUserId()
                .orElseThrow(() -> new UnauthorizedException("User not authenticated"));
        User user = userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        String newPhone = request.getNewContact().trim();

        // Re-check uniqueness in case another user claimed the number between initiate and verify
        if (userRepository.existsByPhoneAndDeletedAtIsNull(newPhone)
                && !newPhone.equals(user.getPhone())) {
            throw new DuplicateResourceException("User", "phone", newPhone);
        }

        // Throws BadRequestException if OTP is wrong/expired/max-attempts
        otpService.verifyContactChangeOtp(newPhone, request.getOtpCode());

        user.setPhone(newPhone);
        user.setMobileVerified(true);
        user = userRepository.save(user);

        log.info("Phone updated successfully for user {}", userId);
        return userMapper.toDTO(user);
    }

    @Override
    @Transactional
    public void initiateEmailChange(ContactChangeRequest request, String ipAddress) {
        Long userId = SecurityUtils.getCurrentUserId()
                .orElseThrow(() -> new UnauthorizedException("User not authenticated"));
        User user = userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        String newEmail = request.getNewContact().trim().toLowerCase();

        if (!EMAIL_PATTERN.matcher(newEmail).matches()) {
            throw new BadRequestException("Invalid email address format");
        }
        if (newEmail.equalsIgnoreCase(user.getEmail())) {
            throw new BadRequestException(
                    "New email address is the same as your current email");
        }
        if (userRepository.existsByEmailAndDeletedAtIsNull(newEmail)) {
            throw new DuplicateResourceException("User", "email", newEmail);
        }

        otpService.sendContactChangeOtp(newEmail, ipAddress);
        log.info("Email change OTP initiated for user {} → {}", userId, newEmail);
    }

    @Override
    @Transactional
    public UserDTO verifyEmailChange(VerifyContactChangeRequest request) {
        Long userId = SecurityUtils.getCurrentUserId()
                .orElseThrow(() -> new UnauthorizedException("User not authenticated"));
        User user = userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        String newEmail = request.getNewContact().trim().toLowerCase();

        if (userRepository.existsByEmailAndDeletedAtIsNull(newEmail)
                && !newEmail.equalsIgnoreCase(user.getEmail())) {
            throw new DuplicateResourceException("User", "email", newEmail);
        }

        otpService.verifyContactChangeOtp(newEmail, request.getOtpCode());

        user.setEmail(newEmail);
        user.setEmailVerified(true);
        user = userRepository.save(user);

        log.info("Email updated successfully for user {}", userId);
        return userMapper.toDTO(user);
    }
}