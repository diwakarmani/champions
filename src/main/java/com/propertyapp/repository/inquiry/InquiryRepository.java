package com.propertyapp.repository.inquiry;

import com.propertyapp.entity.inquiry.Inquiry;
import com.propertyapp.entity.inquiry.InquiryStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface InquiryRepository extends JpaRepository<Inquiry, Long> {

    // ── Received / Sent page queries ─────────────────────────────────────────

    @Query("SELECT i FROM Inquiry i JOIN FETCH i.property p JOIN FETCH i.inquirer JOIN FETCH p.owner o LEFT JOIN FETCH o.roles WHERE p.owner.id = :ownerId ORDER BY i.createdAt DESC")
    Page<Inquiry> findReceivedByOwnerId(@Param("ownerId") Long ownerId, Pageable pageable);

    @Query("SELECT i FROM Inquiry i JOIN FETCH i.property p JOIN FETCH p.owner o LEFT JOIN FETCH o.roles WHERE i.inquirer.id = :inquirerId ORDER BY i.createdAt DESC")
    Page<Inquiry> findSentByInquirerId(@Param("inquirerId") Long inquirerId, Pageable pageable);

    // ── Reveal-contact dedup ──────────────────────────────────────────────────

    /** True when the buyer already has ANY inquiry (form or reveal) for this property. */
    boolean existsByProperty_IdAndInquirer_Id(Long propertyId, Long inquirerId);

    // ── Rating eligibility — dual check ──────────────────────────────────────

    /**
     * Returns true when the buyer is eligible to rate a specific realtor for a specific property.
     * Eligible if they have an inquiry for THIS property owned by THIS realtor where:
     *   - status is CONTACTED or CLOSED (realtor confirmed contact), OR
     *   - the inquiry is older than {@code cutoff} (time-based safety net).
     *
     * The realtorId check ensures eligibility is explicitly tied to the realtor being rated,
     * preventing cross-realtor eligibility in a multi-realtor, multi-property system.
     */
    @Query("""
            SELECT COUNT(i) > 0
            FROM Inquiry i
            WHERE i.inquirer.id = :raterId
              AND i.property.id = :propertyId
              AND i.property.owner.id = :realtorId
              AND (i.status IN (:contacted, :closed) OR i.createdAt <= :cutoff)
            """)
    boolean existsRatingEligibleInquiry(
            @Param("realtorId")  Long realtorId,
            @Param("raterId")    Long raterId,
            @Param("propertyId") Long propertyId,
            @Param("cutoff")     LocalDateTime cutoff,
            @Param("contacted")  InquiryStatus contacted,
            @Param("closed")     InquiryStatus closed
    );

    // ── Scheduler: auto-CONTACTED ─────────────────────────────────────────────

    /** Find all NEW inquiries older than cutoff that have not yet been auto-transitioned. */
    @Query("SELECT i FROM Inquiry i JOIN FETCH i.inquirer JOIN FETCH i.property p JOIN FETCH p.owner WHERE i.status = :status AND i.createdAt <= :cutoff")
    List<Inquiry> findStaleNewInquiries(
            @Param("status") InquiryStatus status,
            @Param("cutoff") LocalDateTime cutoff
    );

    /** Bulk-update stale NEW → CONTACTED (used by scheduler as a single-query fast path). */
    @Modifying
    @Query("UPDATE Inquiry i SET i.status = :newStatus WHERE i.status = :oldStatus AND i.createdAt <= :cutoff")
    int bulkTransitionStatus(
            @Param("oldStatus") InquiryStatus oldStatus,
            @Param("newStatus") InquiryStatus newStatus,
            @Param("cutoff")    LocalDateTime cutoff
    );
}
