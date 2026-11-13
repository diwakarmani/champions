package com.propertyapp.repository.property;

import com.propertyapp.entity.property.ListingReassignment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ListingReassignmentRepository extends JpaRepository<ListingReassignment, Long> {

    List<ListingReassignment> findByPropertyIdOrderByCreatedAtDesc(Long propertyId);
}
