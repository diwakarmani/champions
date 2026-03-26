package com.propertyapp.entity.locality;

import com.propertyapp.entity.base.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import org.locationtech.jts.geom.Point;

import java.math.BigDecimal;

@Entity
@Table(name = "localities",
        uniqueConstraints = @UniqueConstraint(columnNames = {"osmType","osmId"}),
        indexes = {
            @Index(name="idx_locality_city", columnList="city_id"),
            @Index(name="idx_locality_name", columnList="name")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Locality extends BaseEntity {

    @Column(nullable = false)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(nullable = false)
    private City city;

    @Column(columnDefinition = "geography(Point,4326)")
    private Point location;

    @Column(precision = 10, scale = 8)
    private BigDecimal latitude;

    @Column(precision = 11, scale = 8)
    private BigDecimal longitude;

    @Builder.Default
    private Boolean isActive = true;

    @Column(nullable = false)
    private String osmType;

    @Column(nullable = false)
    private Long osmId;
}