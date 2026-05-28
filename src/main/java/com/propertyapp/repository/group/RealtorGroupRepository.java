package com.propertyapp.repository.group;

import com.propertyapp.entity.group.RealtorGroup;
import com.propertyapp.entity.user.User;
import com.propertyapp.enums.GroupStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface RealtorGroupRepository extends JpaRepository<RealtorGroup, Long> {

    Page<RealtorGroup> findByDeletedAtIsNull(Pageable pageable);

    Page<RealtorGroup> findByStatusAndDeletedAtIsNull(GroupStatus status, Pageable pageable);

    Optional<RealtorGroup> findByIdAndDeletedAtIsNull(Long id);

    Optional<RealtorGroup> findByGroupAdminAndDeletedAtIsNull(User groupAdmin);

    boolean existsByNameAndDeletedAtIsNull(String name);

    @Query("SELECT g FROM RealtorGroup g JOIN g.memberships m WHERE m.user = :user AND m.isActive = true AND g.deletedAt IS NULL")
    List<RealtorGroup> findGroupsByMember(@Param("user") User user);

    @Query("SELECT COUNT(g) FROM RealtorGroup g WHERE g.status = :status AND g.deletedAt IS NULL")
    long countByStatus(@Param("status") GroupStatus status);
}
