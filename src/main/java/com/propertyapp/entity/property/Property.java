package com.propertyapp.entity.property;

import com.propertyapp.entity.base.SoftDeletableEntity;
import com.propertyapp.entity.locality.Locality;
import com.propertyapp.entity.user.User;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "properties", indexes = {
    @Index(name = "idx_property_type", columnList = "property_type_id"),
    @Index(name = "idx_owner", columnList = "owner_id"),
    @Index(name = "idx_city", columnList = "city"),
    @Index(name = "idx_status", columnList = "status"),
    @Index(name = "idx_listing_type", columnList = "listing_type"),
    @Index(name = "idx_price", columnList = "price"),
    @Index(name = "idx_deleted_at", columnList = "deleted_at")
})
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SuperBuilder
public class Property extends SoftDeletableEntity {

    @Column(columnDefinition = "geography(Point,4326)")
    private org.locationtech.jts.geom.Point location;

    @Column(name = "title", nullable = false)
    private String title;
    
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "property_type_id", nullable = false)
    private PropertyType propertyType;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "property_sub_type_id")
    private PropertySubType propertySubType;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;
    
    @Column(name = "listing_type", nullable = false, length = 20)
    private String listingType; // SALE, RENT, LEASE
    
    @Column(name = "price", precision = 15, scale = 2, nullable = false)
    private BigDecimal price;
    
    @Column(name = "deposit_amount", precision = 15, scale = 2)
    private BigDecimal depositAmount;
    
    @Column(name = "maintenance_charge", precision = 10, scale = 2)
    private BigDecimal maintenanceCharge;
    
    // Address fields
    @Column(name = "address_line1", nullable = false)
    private String addressLine1;
    
    @Column(name = "address_line2")
    private String addressLine2;
    
    @Column(name = "locality", length = 100)
    private String locality;
    
    @Column(name = "city", nullable = false, length = 100)
    private String city;
    
    @Column(name = "state", nullable = false, length = 100)
    private String state;
    
    @Column(name = "country", nullable = false, length = 100)
    private String country;
    
    @Column(name = "postal_code", length = 20)
    private String postalCode;
    
    @Column(name = "latitude", precision = 10, scale = 8)
    private BigDecimal latitude;
    
    @Column(name = "longitude", precision = 11, scale = 8)
    private BigDecimal longitude;
    
    // Property details
    @Column(name = "bedrooms")
    private Integer bedrooms;
    
    @Column(name = "bathrooms")
    private Integer bathrooms;
    
    @Column(name = "balconies")
    private Integer balconies;
    
    @Column(name = "floor_number")
    private Integer floorNumber;
    
    @Column(name = "total_floors")
    private Integer totalFloors;
    
    @Column(name = "carpet_area")
    private Integer carpetArea;
    
    @Column(name = "built_up_area")
    private Integer builtUpArea;
    
    @Column(name = "plot_area")
    private Integer plotArea;
    
    @Column(name = "facing_direction", length = 20)
    private String facingDirection;
    
    @Column(name = "furnished_status", length = 20)
    private String furnishedStatus; // FURNISHED, SEMI_FURNISHED, UNFURNISHED
    
    @Column(name = "parking_covered")
    private Integer parkingCovered;
    
    @Column(name = "parking_open")
    private Integer parkingOpen;
    
    @Column(name = "age_of_property")
    private Integer ageOfProperty;
    
    @Column(name = "available_from")
    private LocalDateTime availableFrom;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "locality_id")
    private Locality localityRef;
    
    // Status & verification
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private String status = "DRAFT"; // DRAFT, PENDING_APPROVAL, ACTIVE, SOLD, RENTED, INACTIVE
    
    @Column(name = "is_verified")
    @Builder.Default
    private boolean isVerified = false;
    
    @Column(name = "is_featured")
    @Builder.Default
    private boolean isFeatured = false;
    
    @Column(name = "is_premium")
    @Builder.Default
    private boolean isPremium = false;
    
    // Metrics
    @Column(name = "view_count")
    @Builder.Default
    private Integer viewCount = 0;
    
    @Column(name = "inquiry_count")
    @Builder.Default
    private Integer inquiryCount = 0;
    
    @Column(name = "published_at")
    private LocalDateTime publishedAt;
    
    @OneToMany(mappedBy = "property", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private Set<PropertyImage> images = new HashSet<>();
    
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "property_amenity_mapping",
        joinColumns = @JoinColumn(name = "property_id"),
        inverseJoinColumns = @JoinColumn(name = "amenity_id")
    )
    @Builder.Default
    private Set<PropertyAmenity> amenities = new HashSet<>();
    
    public void addImage(PropertyImage image) {
        images.add(image);
        image.setProperty(this);
    }
    
    public void removeImage(PropertyImage image) {
        images.remove(image);
        image.setProperty(null);
    }
    
    public void addAmenity(PropertyAmenity amenity) {
        amenities.add(amenity);
    }
    
    public void removeAmenity(PropertyAmenity amenity) {
        amenities.remove(amenity);
    }
    
    public void incrementViewCount() {
        this.viewCount++;
    }
    
    public void incrementInquiryCount() {
        this.inquiryCount++;
    }
    
    public void publish() {
        this.status = "ACTIVE";
        this.publishedAt = LocalDateTime.now();
    }
}