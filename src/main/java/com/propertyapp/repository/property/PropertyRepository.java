package com.propertyapp.repository.property;

import com.propertyapp.entity.property.Property;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface PropertyRepository extends JpaRepository<Property, Long>,
        JpaSpecificationExecutor<Property> {

    /** Public results only show listings an active agent answers for. Same rule as PropertyVisibility.isActiveAgent. */
    String ACTIVE_AGENT_OWNER = "AND EXISTS (SELECT 1 FROM User agent JOIN agent.roles agentRole "
            + "WHERE agent = p.owner AND agent.isActive = true AND agent.deletedAt IS NULL "
            + "AND agent.agentSuspendedAt IS NULL "
            + "AND agentRole.name = 'REALTOR') ";

    @Modifying
    @Query("UPDATE Property p SET p.viewCount = COALESCE(p.viewCount, 0) + 1 WHERE p.id = :id")
    void incrementViewCount(@Param("id") Long id);


    Optional<Property> findByIdAndDeletedAtIsNull(Long id);

    /** An active agent's ACTIVE listings, newest first (public profile; the caller has checked the agent). */
    @Query("SELECT p FROM Property p WHERE p.owner.id = :ownerId AND p.status = 'ACTIVE' AND p.deletedAt IS NULL")
    Page<Property> findActiveByOwner(@Param("ownerId") Long ownerId, Pageable pageable);

    /** Sitemap feed: ACTIVE listings of active agents only (ADR-0006 Amendment 1). */
    @Query("SELECT p.id AS id, p.title AS title, p.city AS city, p.state AS state, p.updatedAt AS updatedAt "
            + "FROM Property p WHERE p.status = 'ACTIVE' AND p.deletedAt IS NULL " + ACTIVE_AGENT_OWNER)
    Page<SitemapRow> findSitemapRows(Pageable pageable);

    /** Public search: ACTIVE listings of active agents, optionally narrowed to one city and listing type. */
    @Query("SELECT p FROM Property p WHERE p.status = 'ACTIVE' AND p.deletedAt IS NULL " + ACTIVE_AGENT_OWNER
            + "AND (:listingType IS NULL OR p.listingType = :listingType) "
            + "AND (:state IS NULL OR LOWER(p.state) = LOWER(CAST(:state AS string))) "
            + "AND (:city IS NULL OR LOWER(p.city) = LOWER(CAST(:city AS string))) "
            + "AND (:minPrice IS NULL OR p.price >= :minPrice) "
            + "AND (:maxPrice IS NULL OR p.price <= :maxPrice) "
            + "AND (:minBeds IS NULL OR p.bedrooms >= :minBeds)")
    Page<Property> searchPublic(@Param("listingType") String listingType, @Param("state") String state,
                                @Param("city") String city, @Param("minPrice") java.math.BigDecimal minPrice,
                                @Param("maxPrice") java.math.BigDecimal maxPrice, @Param("minBeds") Integer minBeds,
                                Pageable pageable);

    /** Cities with public listings, per listing type (city pages and the city sitemap). */
    @Query("SELECT p.state AS state, p.city AS city, p.listingType AS listingType, COUNT(p) AS listings, "
            + "MAX(p.updatedAt) AS lastModified FROM Property p "
            + "WHERE p.status = 'ACTIVE' AND p.deletedAt IS NULL " + ACTIVE_AGENT_OWNER
            + "AND p.state IS NOT NULL AND p.city IS NOT NULL AND p.listingType IS NOT NULL "
            + "GROUP BY p.state, p.city, p.listingType ORDER BY p.state, p.city, p.listingType")
    List<MarketRow> findPublicMarkets();

    interface MarketRow {
        String getState();
        String getCity();
        String getListingType();
        Long getListings();
        java.time.LocalDateTime getLastModified();
    }

    interface SitemapRow {
        Long getId();
        String getTitle();
        String getCity();
        String getState();
        java.time.LocalDateTime getUpdatedAt();
    }

    Page<Property> findByDeletedAtIsNull(Pageable pageable);

    Page<Property> findByOwnerIdAndDeletedAtIsNull(Long ownerId, Pageable pageable);

    Page<Property> findByStatusAndDeletedAtIsNull(String status, Pageable pageable);

    @Query("SELECT p FROM Property p WHERE p.deletedAt IS NULL " +
           "AND p.status = 'ACTIVE' " + ACTIVE_AGENT_OWNER +
           "AND LOWER(p.city) = LOWER(:city)")
    Page<Property> findActiveByCity(@Param("city") String city, Pageable pageable);

    @Query("SELECT p FROM Property p WHERE p.deletedAt IS NULL " +
           "AND p.status = 'ACTIVE' " + ACTIVE_AGENT_OWNER +
           "AND p.price BETWEEN :minPrice AND :maxPrice")
    Page<Property> findByPriceRange(
        @Param("minPrice") BigDecimal minPrice,
        @Param("maxPrice") BigDecimal maxPrice,
        Pageable pageable
    );

    @Query("SELECT p FROM Property p WHERE p.deletedAt IS NULL " +
           "AND p.status = 'ACTIVE' " + ACTIVE_AGENT_OWNER +
           "AND p.isFeatured = true " +
           "ORDER BY p.publishedAt DESC")
    List<Property> findFeaturedProperties(Pageable pageable);

    @Query("SELECT COUNT(p) FROM Property p WHERE p.owner.id = :ownerId " +
           "AND p.status = :status AND p.deletedAt IS NULL")
    long countByOwnerAndStatus(@Param("ownerId") Long ownerId, @Param("status") String status);

    @Query("SELECT DISTINCT p.city FROM Property p WHERE p.owner.id = :ownerId " +
           "AND p.status = 'ACTIVE' AND p.deletedAt IS NULL ORDER BY p.city")
    List<String> findDistinctActiveCitiesByOwnerId(@Param("ownerId") Long ownerId);

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

    @Query("SELECT COUNT(p) FROM Property p WHERE p.owner.id = :ownerId AND p.title = :title " +
           "AND p.createdAt > :since AND p.deletedAt IS NULL")
    long countByOwnerIdAndTitleAndCreatedAtAfterAndDeletedAtIsNull(
            @Param("ownerId") Long ownerId,
            @Param("title") String title,
            @Param("since") java.time.LocalDateTime since);
}
