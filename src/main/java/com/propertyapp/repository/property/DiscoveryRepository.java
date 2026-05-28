package com.propertyapp.repository.property;

import com.propertyapp.entity.property.Property;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DiscoveryRepository extends JpaRepository<Property, Long> {

    @Query(value = """
    SELECT p.* FROM properties p
    JOIN discovery_cache dc ON p.id = dc.property_id
    WHERE dc.user_id = :userId
    AND dc.category = :category
    AND (:city IS NULL OR dc.city = :city)
    AND p.deleted_at IS NULL
    AND p.status = 'ACTIVE'
    ORDER BY dc.rank ASC
    LIMIT :limit
""", nativeQuery = true)
    List<Property> getCachedProperties(
            @Param("userId") Long userId,
            @Param("category") String category,
            @Param("city") String city,
            @Param("limit") int limit
    );

    @Query(value = """
        SELECT * FROM properties
        WHERE status = 'ACTIVE'
        AND deleted_at IS NULL
        ORDER BY location <->
        ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography
        LIMIT :limit
    """, nativeQuery = true)
    List<Property> findNearest(
            @Param("lat") double lat,
            @Param("lng") double lng,
            @Param("limit") int limit
    );

    // FALLBACK for home sections — image-first, then most recent
    @Query(value = """
SELECT
p.id as id,
p.title as title,
p.listing_type as listingType,
p.price as price,
p.city as city,
p.locality as locality,
p.bedrooms as bedrooms,
p.furnished_status as furnishedStatus,
pi.image_url as primaryImageUrl,
p.is_verified as verified,
p.is_premium as premium,
null as distanceInKm
FROM properties p
LEFT JOIN property_images pi ON p.id = pi.property_id AND pi.is_primary = true
WHERE p.status = 'ACTIVE'
AND p.deleted_at IS NULL
AND (:city IS NULL OR p.city = :city)
ORDER BY CASE WHEN pi.image_url IS NOT NULL THEN 0 ELSE 1 END ASC, p.created_at DESC
LIMIT :limit
""", nativeQuery = true)
    List<PropertyCardProjection> getLatestActiveCards(
            @Param("city") String city,
            @Param("limit") int limit
    );

    // POPULAR / RECOMMENDED home sections — image-first, then by discovery rank
    @Query(value = """
SELECT
p.id as id,
p.title as title,
p.listing_type as listingType,
p.price as price,
p.city as city,
p.locality as locality,
p.bedrooms as bedrooms,
p.furnished_status as furnishedStatus,
pi.image_url as primaryImageUrl,
p.is_verified as verified,
p.is_premium as premium,
null as distanceInKm
FROM properties p
JOIN discovery_cache dc ON p.id = dc.property_id
LEFT JOIN property_images pi ON p.id = pi.property_id AND pi.is_primary = true
WHERE dc.user_id = :userId
AND dc.category = :category
AND (:city IS NULL OR dc.city = :city)
AND p.deleted_at IS NULL
AND p.status = 'ACTIVE'
ORDER BY CASE WHEN pi.image_url IS NOT NULL THEN 0 ELSE 1 END ASC, dc.rank ASC
LIMIT :limit
""", nativeQuery = true)
    List<PropertyCardProjection> getHomeCards(
            @Param("userId") Long userId,
            @Param("category") String category,
            @Param("city") String city,
            @Param("limit") int limit
    );

    // NEAREST home section — image-first, then by proximity
    @Query(value = """
SELECT
p.id as id,
p.title as title,
p.listing_type as listingType,
p.price as price,
p.city as city,
p.locality as locality,
p.bedrooms as bedrooms,
p.furnished_status as furnishedStatus,
pi.image_url as primaryImageUrl,
p.is_verified as verified,
p.is_premium as premium,
ST_Distance(
p.location,
ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography
) / 1000 as distanceInKm
FROM properties p
LEFT JOIN property_images pi ON p.id = pi.property_id AND pi.is_primary = true
WHERE p.status = 'ACTIVE'
AND p.deleted_at IS NULL
ORDER BY CASE WHEN pi.image_url IS NOT NULL THEN 0 ELSE 1 END ASC,
p.location <-> ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography
LIMIT :limit
""", nativeQuery = true)
    List<PropertyCardProjection> getNearestCards(
            @Param("lat") double lat,
            @Param("lng") double lng,
            @Param("limit") int limit
    );

    // VIEW ALL (cache-based) — same image-first + rank ordering as home sections for consistency
    @Query(value = """
    SELECT
        p.id as id,
        p.title as title,
        p.listing_type as listingType,
        p.price as price,
        p.city as city,
        p.locality as locality,
        p.bedrooms as bedrooms,
        p.furnished_status as furnishedStatus,
        pi.image_url as primaryImageUrl,
        p.is_verified as verified,
        p.is_premium as premium,
        null as distanceInKm
    FROM properties p
    JOIN discovery_cache dc ON p.id = dc.property_id
    LEFT JOIN property_images pi ON p.id = pi.property_id AND pi.is_primary = true
    WHERE dc.user_id = :userId
    AND dc.category = :category
    AND (:city IS NULL OR p.city = :city)
    AND p.deleted_at IS NULL
    AND p.status = 'ACTIVE'
    ORDER BY CASE WHEN pi.image_url IS NOT NULL THEN 0 ELSE 1 END ASC, dc.rank ASC
""",
            countQuery = """
    SELECT count(*)
    FROM properties p
    JOIN discovery_cache dc ON p.id = dc.property_id
    WHERE dc.user_id = :userId
    AND dc.category = :category
    AND (:city IS NULL OR p.city = :city)
    AND p.deleted_at IS NULL
    AND p.status = 'ACTIVE'
""",
            nativeQuery = true)
    Page<PropertyCardProjection> getViewMore(
            @Param("userId") Long userId,
            @Param("category") String category,
            @Param("city") String city,
            Pageable pageable
    );

    // VIEW ALL fallback (no cache) — same image-first + recency ordering as home fallback
    @Query(value = """
SELECT p.id as id, p.title as title, p.listing_type as listingType, p.price as price,
p.city as city, p.locality as locality, p.bedrooms as bedrooms,
p.furnished_status as furnishedStatus, pi.image_url as primaryImageUrl,
p.is_verified as verified, p.is_premium as premium, null as distanceInKm
FROM properties p
LEFT JOIN property_images pi ON p.id = pi.property_id AND pi.is_primary = true
WHERE p.status = 'ACTIVE' AND p.deleted_at IS NULL
AND (:city IS NULL OR p.city = :city)
ORDER BY CASE WHEN pi.image_url IS NOT NULL THEN 0 ELSE 1 END ASC, p.created_at DESC
""",
            countQuery = "SELECT count(*) FROM properties p WHERE p.status = 'ACTIVE' AND p.deleted_at IS NULL AND (:city IS NULL OR p.city = :city)",
            nativeQuery = true)
    Page<PropertyCardProjection> getLatestActivePageable(@Param("city") String city, Pageable pageable);
}
