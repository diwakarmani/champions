package com.propertyapp.service.user;

import com.propertyapp.dto.common.PageResponse;
import com.propertyapp.dto.user.*;
import org.springframework.data.domain.Pageable;

import java.util.Set;

public interface UserService {
    
    PageResponse<UserDTO> getAllUsers(Pageable pageable);
    
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
}