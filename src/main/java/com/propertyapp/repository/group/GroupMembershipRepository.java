package com.propertyapp.repository.group;

import com.propertyapp.entity.group.GroupMembership;
import com.propertyapp.entity.group.RealtorGroup;
import com.propertyapp.entity.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface GroupMembershipRepository extends JpaRepository<GroupMembership, Long> {

    List<GroupMembership> findByGroupAndIsActiveTrue(RealtorGroup group);

    Optional<GroupMembership> findByGroupAndUser(RealtorGroup group, User user);

    boolean existsByGroupAndUserAndIsActiveTrue(RealtorGroup group, User user);

    @Query("SELECT m FROM GroupMembership m WHERE m.user = :user AND m.isActive = true")
    List<GroupMembership> findActiveByUser(@Param("user") User user);

    @Query("SELECT COUNT(m) FROM GroupMembership m WHERE m.group = :group AND m.isActive = true")
    long countActiveMembersByGroup(@Param("group") RealtorGroup group);
}
