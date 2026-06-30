package com.propertyapp.entity.property;

import com.propertyapp.entity.base.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "property_amenities")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SuperBuilder
public class PropertyAmenity extends BaseEntity {

    @Column(name = "name", nullable = false, unique = true, length = 100)
    private String name;

    @Column(name = "icon_class", length = 50)
    private String iconClass;

    @Column(name = "category", length = 50)
    private String category;

    @Column(name = "display_order")
    private Integer displayOrder;

    @Column(name = "is_active")
    @Builder.Default
    private boolean isActive = true;
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PropertyAmenity)) return false;
        PropertyAmenity that = (PropertyAmenity) o;
        return name != null && name.equals(that.getName());
    }
    
    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}