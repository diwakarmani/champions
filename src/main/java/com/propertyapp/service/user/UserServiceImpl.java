package com.propertyapp.service.user;

import com.propertyapp.dto.common.PageResponse;
import com.propertyapp.dto.user.*;
import com.propertyapp.entity.user.Role;
import com.propertyapp.entity.user.User;
import com.propertyapp.entity.user.UserAddress;
import com.propertyapp.exception.BadRequestException;
import com.propertyapp.exception.ResourceNotFoundException;
import com.propertyapp.exception.UnauthorizedException;
import com.propertyapp.mapper.UserMapper;
import com.propertyapp.repository.user.RoleRepository;
import com.propertyapp.repository.user.UserAddressRepository;
import com.propertyapp.repository.user.UserRepository;
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

@Service
@RequiredArgsConstructor
@Slf4j
public class UserServiceImpl implements UserService {
    
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserAddressRepository addressRepository;
    private final UserMapper userMapper;
    
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
        if (request.getPhone() != null) {
            user.setPhone(request.getPhone());
        }
        if (request.getDateOfBirth() != null) {
            user.setDateOfBirth(request.getDateOfBirth());
        }
        if (request.getGender() != null) {
            user.setGender(request.getGender());
        }
        if (request.getBio() != null) {
            user.setBio(request.getBio());
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
        
        Set<Role> roles = roleRepository.findByNameIn(roleNames);
        user.setRoles(roles);
        userRepository.save(user);
        
        log.info("Roles assigned successfully to user: {}", userId);
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
}