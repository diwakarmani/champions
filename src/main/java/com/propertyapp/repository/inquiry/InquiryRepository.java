package com.propertyapp.repository.inquiry;

import com.propertyapp.entity.inquiry.Inquiry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InquiryRepository extends JpaRepository<Inquiry, Long> {

    // Received: inquiries on properties owned by the given user
    @Query("SELECT i FROM Inquiry i JOIN FETCH i.property p JOIN FETCH i.inquirer WHERE p.owner.id = :ownerId ORDER BY i.createdAt DESC")
    Page<Inquiry> findReceivedByOwnerId(@Param("ownerId") Long ownerId, Pageable pageable);

    // Sent: inquiries created by the given user
    @Query("SELECT i FROM Inquiry i JOIN FETCH i.property p WHERE i.inquirer.id = :inquirerId ORDER BY i.createdAt DESC")
    Page<Inquiry> findSentByInquirerId(@Param("inquirerId") Long inquirerId, Pageable pageable);
}
