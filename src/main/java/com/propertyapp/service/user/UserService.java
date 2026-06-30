package com.propertyapp.service.user;

import com.propertyapp.dto.common.PageResponse;
import com.propertyapp.dto.user.*;
import org.springframework.data.domain.Pageable;

import java.util.Set;


public interface UserService {
    
    PageResponse<UserDTO> getAllUsers(Pageable pageable);

    PageResponse<UserDTO> getUsersByRole(String role, Pageable pageable);

    PageResponse<UserDTO> searchUsers(String search, Pageable pageable);
    
    UserDTO getUserById(Long id);
    
    UserDTO getCurrentUser();
    
    UserDTO updateUser(Long id, UserUpdateRequest request);
    
    UserDTO updateCurrentUser(UserUpdateRequest request);
    
    void deleteUser(Long id);
    
    void changePassword(ChangePasswordRequest request);
    
    void assignRoles(Long userId, Set<String> roleNames);
    
    UserAddressDTO addAddress(UserAddressDTO addressDTO);

    void removeAddress(Long addressId);

    UserDTO activateUser(Long id);

    UserDTO deactivateUser(Long id);

    // ── Secure contact-change (OTP-gated) ────────────────────────────────────

    /** Step 1: validate uniqueness then send OTP to the new phone number. */
    void initiatePhoneChange(ContactChangeRequest request, String ipAddress);

    /** Step 2: verify OTP then atomically update phone + set mobileVerified=true. */
    UserDTO verifyPhoneChange(VerifyContactChangeRequest request);

    /** Step 1: validate uniqueness then send OTP to the new email address. */
    void initiateEmailChange(ContactChangeRequest request, String ipAddress);

    /** Step 2: verify OTP then atomically update email + set emailVerified=true. */
    UserDTO verifyEmailChange(VerifyContactChangeRequest request);
}