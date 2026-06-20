package com.propertyapp.repository.user;

import com.propertyapp.entity.property.Property;
import com.propertyapp.entity.user.UserFavorite;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserFavoriteRepository extends JpaRepository<UserFavorite, Long> {

    @Query("SELECT f FROM UserFavorite f WHERE f.user.id = :userId ORDER BY f.createdAt DESC")
    Page<UserFavorite> findByUserId(@Param("userId") Long userId, Pageable pageable);

    @Query("SELECT f.property.id FROM UserFavorite f WHERE f.user.id = :userId ORDER BY f.createdAt DESC")
    Page<Long> findPropertyIdsByUserId(@Param("userId") Long userId, Pageable pageable);

    @Query("""
            SELECT DISTINCT p FROM Property p
            LEFT JOIN FETCH p.images
            JOIN FETCH p.owner
            JOIN FETCH p.propertyType
            LEFT JOIN FETCH p.propertySubType
            WHERE p.id IN :ids
            """)
    List<Property> findByIdsWithImages(@Param("ids") List<Long> ids);

    Optional<UserFavorite> findByUserIdAndPropertyId(Long userId, Long propertyId);

    boolean existsByUserIdAndPropertyId(Long userId, Long propertyId);

    void deleteByUserIdAndPropertyId(Long userId, Long propertyId);

    long countByUserId(Long userId);

    /** Single query ownership check — avoids N round-trips for compare validation. */
    @Query("SELECT COUNT(f) FROM UserFavorite f WHERE f.user.id = :userId AND f.property.id IN :ids")
    long countByUserIdAndPropertyIdIn(@Param("userId") Long userId, @Param("ids") List<Long> ids);

    /** Eagerly fetches images + amenities for comparison — avoids MultipleBagFetchException
     *  because both collections are Set (not List). */
    @Query("""
            SELECT DISTINCT p FROM Property p
            LEFT JOIN FETCH p.images
            JOIN FETCH p.owner
            JOIN FETCH p.propertyType
            LEFT JOIN FETCH p.propertySubType
            LEFT JOIN FETCH p.amenities
            WHERE p.id IN :ids
            """)
    List<Property> findByIdsWithImagesAndAmenities(@Param("ids") List<Long> ids);
}
