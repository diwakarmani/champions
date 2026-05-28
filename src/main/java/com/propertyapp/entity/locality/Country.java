package com.propertyapp.entity.locality;

import com.propertyapp.entity.base.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "countries",
        uniqueConstraints = @UniqueConstraint(columnNames = "name"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Country extends BaseEntity {

    private String name;

    @Column(nullable = false)
    private Long osmId;

    @Column(nullable = false)
    private String osmType; // always relation

    private BigDecimal latitude;
    private BigDecimal longitude;

    private Boolean isActive;
}