package com.propertyapp.entity.locality;

import com.propertyapp.entity.base.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "states",
        uniqueConstraints = @UniqueConstraint(columnNames = {"name","country_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class State extends BaseEntity {

    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    private Country country;

    private Long osmId;
    private String osmType;

    private BigDecimal latitude;
    private BigDecimal longitude;

    private Boolean isActive;
}