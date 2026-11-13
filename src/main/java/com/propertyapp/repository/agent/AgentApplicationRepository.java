package com.propertyapp.repository.agent;

import com.propertyapp.entity.agent.AgentApplication;
import com.propertyapp.entity.agent.AgentApplicationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AgentApplicationRepository extends JpaRepository<AgentApplication, Long> {

    boolean existsByUserIdAndStatus(Long userId, AgentApplicationStatus status);

    @EntityGraph(attributePaths = "user")
    List<AgentApplication> findByUserIdOrderByCreatedAtDesc(Long userId);

    @EntityGraph(attributePaths = "user")
    Page<AgentApplication> findByStatus(AgentApplicationStatus status, Pageable pageable);

    @Override
    @EntityGraph(attributePaths = "user")
    Page<AgentApplication> findAll(Pageable pageable);

    /**
     * Account deletion: a request still waiting is closed, so no admin can approve a deleted person (ADR-0008).
     * The applicant is recorded as the one who closed it.
     */
    @Modifying
    @Query("UPDATE AgentApplication a SET a.status = com.propertyapp.entity.agent.AgentApplicationStatus.REJECTED, "
            + "a.reviewedBy = :userId, a.reviewedAt = :now, a.rejectionReason = 'Account deleted', a.version = COALESCE(a.version, 0) + 1 "
            + "WHERE a.user.id = :userId AND a.status = com.propertyapp.entity.agent.AgentApplicationStatus.PENDING")
    int closePendingForDeletedUser(@Param("userId") Long userId, @Param("now") LocalDateTime now);

    /** Account deletion: every application keeps its decision but loses license, brokerage, bio and photo (ADR-0008). */
    @Modifying
    @Query("UPDATE AgentApplication a SET a.licenseNumber = 'deleted', a.licenseState = '--', "
            + "a.brokerageName = 'deleted', a.yearsExperience = NULL, a.serviceAreas = NULL, a.bio = NULL, "
            + "a.photoUrl = NULL, a.version = COALESCE(a.version, 0) + 1 WHERE a.user.id = :userId")
    int anonymiseForDeletedUser(@Param("userId") Long userId);
}
