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

    boolean existsByEmailAndDeletedAtIsNull(String email);

    boolean existsByPhone(String phone);

    boolean existsByPhoneAndDeletedAtIsNull(String phone);
    
    @Query("SELECT u FROM User u WHERE u.deletedAt IS NULL " +
           "AND (LOWER(u.firstName) LIKE LOWER(CONCAT('%', :search, '%')) " +
           "OR LOWER(u.lastName) LIKE LOWER(CONCAT('%', :search, '%')) " +
           "OR LOWER(u.email) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<User> searchUsers(@Param("search") String search, Pageable pageable);
    
    @Query("SELECT u FROM User u JOIN u.roles r WHERE r.name = :roleName AND u.deletedAt IS NULL")
    Page<User> findByRole(@Param("roleName") String roleName, Pageable pageable);
    
    Page<User> findAllByDeletedAtIsNull(Pageable pageable);

    // Find by email or phone — used by OTP flow; excludes soft-deleted accounts,
    // and uses LOWER() on email so mixed-case stored addresses are found correctly.
    @Query("SELECT u FROM User u WHERE u.deletedAt IS NULL AND (LOWER(u.email) = LOWER(:email) OR u.phone = :phone)")
    Optional<User> findByEmailOrPhone(@Param("email") String email, @Param("phone") String phone);

    @Query("SELECT COUNT(u) FROM User u WHERE u.createdAt > :since AND u.deletedAt IS NULL")
    long countCreatedAfter(@Param("since") java.time.LocalDateTime since);

    @Query("SELECT COUNT(u) FROM User u JOIN u.roles r WHERE r.name = :roleName AND u.deletedAt IS NULL")
    long countByRole(@Param("roleName") String roleName);

}