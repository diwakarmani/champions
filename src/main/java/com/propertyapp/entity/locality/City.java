package com.propertyapp.entity.locality;

import com.propertyapp.entity.base.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(
        name = "cities",
        uniqueConstraints = @UniqueConstraint(columnNames = {"osmId"}),
        indexes = {
                @Index(name="idx_city_osm", columnList="osmType,osmId")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class City extends BaseEntity {

    @Column(nullable = false)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(nullable = false)
    private State state;

    @Column(precision = 10, scale = 8)
    private BigDecimal latitude;

    @Column(precision = 11, scale = 8)
    private BigDecimal longitude;

    @Builder.Default
    private Boolean isActive = false;

    @Builder.Default
    private Boolean isImported = false;

    @Column(nullable = false)
    private String osmType; // relation | way | node

    @Column(nullable = false)
    private Long osmId;
}