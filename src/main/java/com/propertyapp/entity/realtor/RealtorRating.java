package com.propertyapp.entity.realtor;

import com.propertyapp.entity.base.AuditableEntity;
import com.propertyapp.entity.property.Property;
import com.propertyapp.entity.user.User;
import jakarta.persistence.*;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Entity
@Table(
        name = "realtor_ratings",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_realtor_rater",
                columnNames = {"realtor_id", "rater_id"}
        ),
        indexes = {
                @Index(name = "idx_realtor_ratings_realtor",  columnList = "realtor_id"),
                @Index(name = "idx_realtor_ratings_rater",    columnList = "rater_id"),
                @Index(name = "idx_realtor_ratings_property", columnList = "property_id")
        }
)
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RealtorRating extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "realtor_id", nullable = false)
    private User realtor;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "rater_id", nullable = false)
    private User rater;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "property_id", nullable = false)
    private Property property;

    /** 1–5 star rating. */
    @Min(1) @Max(5)
    @Column(nullable = false)
    private int rating;

    /** Optional written review (max 500 chars enforced at DTO level). */
    @Column(columnDefinition = "TEXT")
    private String comment;
}
