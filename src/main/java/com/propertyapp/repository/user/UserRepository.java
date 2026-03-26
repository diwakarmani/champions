package com.propertyapp.repository.user;

import com.propertyapp.entity.user.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    
    Optional<User> findByEmailAndDeletedAtIsNull(String email);
    
    Optional<User> findByPhoneAndDeletedAtIsNull(String phone);
    
    Optional<User> findByIdAndDeletedAtIsNull(Long id);
    
    boolean existsByEmail(String email);
    
    boolean existsByPhone(String phone);
    
    @Query("SELECT u FROM User u WHERE u.deletedAt IS NULL " +
           "AND (LOWER(u.firstName) LIKE LOWER(CONCAT('%', :search, '%')) " +
           "OR LOWER(u.lastName) LIKE LOWER(CONCAT('%', :search, '%')) " +
           "OR LOWER(u.email) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<User> searchUsers(@Param("search") String search, Pageable pageable);
    
    @Query("SELECT u FROM User u JOIN u.roles r WHERE r.name = :roleName AND u.deletedAt IS NULL")
    Page<User> findByRole(@Param("roleName") String roleName, Pageable pageable);
    
    Page<User> findAllByDeletedAtIsNull(Pageable pageable);

    // Find by email or mobile
    Optional<User> findByEmailOrPhone(String email, String mobile);

    // Find by mobile
    Optional<User> findByPhone(String mobile);

    @Query("SELECT COUNT(u) FROM User u WHERE u.createdAt > :since AND u.deletedAt IS NULL")
    long countCreatedAfter(@Param("since") java.time.LocalDateTime since);

}