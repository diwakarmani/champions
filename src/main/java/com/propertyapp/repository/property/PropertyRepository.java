package com.propertyapp.repository.property;

import com.propertyapp.entity.property.Property;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface PropertyRepository extends JpaRepository<Property, Long>, 
        JpaSpecificationExecutor<Property> {
    
    Optional<Property> findByIdAndDeletedAtIsNull(Long id);
    
    Page<Property> findByOwnerIdAndDeletedAtIsNull(Long ownerId, Pageable pageable);
    
    Page<Property> findByStatusAndDeletedAtIsNull(String status, Pageable pageable);
    
    @Query("SELECT p FROM Property p WHERE p.deletedAt IS NULL " +
           "AND p.status = 'ACTIVE' " +
           "AND LOWER(p.city) = LOWER(:city)")
    Page<Property> findActiveByCity(@Param("city") String city, Pageable pageable);
    
    @Query("SELECT p FROM Property p WHERE p.deletedAt IS NULL " +
           "AND p.status = 'ACTIVE' " +
           "AND p.price BETWEEN :minPrice AND :maxPrice")
    Page<Property> findByPriceRange(
        @Param("minPrice") BigDecimal minPrice,
        @Param("maxPrice") BigDecimal maxPrice,
        Pageable pageable
    );
    
    @Query("SELECT p FROM Property p WHERE p.deletedAt IS NULL " +
           "AND p.status = 'ACTIVE' " +
           "AND p.isFeatured = true " +
           "ORDER BY p.publishedAt DESC")
    List<Property> findFeaturedProperties(Pageable pageable);
    
    @Query("SELECT COUNT(p) FROM Property p WHERE p.owner.id = :ownerId " +
           "AND p.status = :status AND p.deletedAt IS NULL")
    long countByOwnerAndStatus(@Param("ownerId") Long ownerId, @Param("status") String status);

    @Query("SELECT p FROM Property p WHERE p.owner.id IN :ownerIds " +
           "AND p.status = :status AND p.deletedAt IS NULL")
    Page<Property> findByOwnerIdInAndStatusAndDeletedAtIsNull(
            @Param("ownerIds") List<Long> ownerIds,
            @Param("status") String status,
            Pageable pageable);

    @Query("SELECT COUNT(p) FROM Property p WHERE p.owner.id IN :ownerIds " +
           "AND p.status = :status AND p.deletedAt IS NULL")
    long countByOwnerIdInAndStatusAndDeletedAtIsNull(
            @Param("ownerIds") List<Long> ownerIds,
            @Param("status") String status);

    @Query("SELECT COUNT(p) FROM Property p WHERE p.owner.id IN :ownerIds " +
           "AND p.status = :status AND p.updatedAt > :since AND p.deletedAt IS NULL")
    long countByOwnerIdInAndStatusAndUpdatedAtAfterAndDeletedAtIsNull(
            @Param("ownerIds") List<Long> ownerIds,
            @Param("status") String status,
            @Param("since") java.time.LocalDateTime since);

    @Query("SELECT COALESCE(SUM(p.viewCount), 0) FROM Property p " +
           "WHERE p.owner.id = :ownerId AND p.deletedAt IS NULL")
    long sumViewCountByOwnerId(@Param("ownerId") Long ownerId);

    @Query("SELECT COUNT(p) FROM Property p WHERE p.status = :status AND p.deletedAt IS NULL")
    long countByStatusAndDeletedAtIsNull(@Param("status") String status);

    @Query("SELECT COUNT(p) FROM Property p WHERE p.createdAt > :since AND p.deletedAt IS NULL")
    long countCreatedAfter(@Param("since") java.time.LocalDateTime since);
}