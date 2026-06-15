package com.propertyapp.repository.realtor;

import com.propertyapp.entity.realtor.RealtorUserInteraction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface RealtorUserInteractionRepository extends JpaRepository<RealtorUserInteraction, Long> {

    @Query("SELECT COUNT(DISTINCT i.user.id) FROM RealtorUserInteraction i WHERE i.realtor.id = :realtorId")
    long countDistinctUsersByRealtorId(@Param("realtorId") Long realtorId);
}
