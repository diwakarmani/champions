package com.propertyapp.repository.realtor;

import com.propertyapp.entity.realtor.RealtorRating;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface RealtorRatingRepository extends JpaRepository<RealtorRating, Long> {

    Optional<RealtorRating> findByRealtor_IdAndRater_Id(Long realtorId, Long raterId);

    long countByRealtor_Id(Long realtorId);

    @Query("SELECT AVG(r.rating) FROM RealtorRating r WHERE r.realtor.id = :realtorId")
    Optional<Double> findAverageRatingByRealtorId(@Param("realtorId") Long realtorId);

    @Query("SELECT r FROM RealtorRating r JOIN FETCH r.rater WHERE r.realtor.id = :realtorId ORDER BY r.createdAt DESC")
    Page<RealtorRating> findByRealtorIdWithRater(@Param("realtorId") Long realtorId, Pageable pageable);
}
