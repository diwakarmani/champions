package com.propertyapp.entity.property;

import com.propertyapp.entity.base.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(
        name = "property_sub_types",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"property_type_id", "name"}),
                @UniqueConstraint(columnNames = {"property_type_id", "display_order"})
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PropertySubType extends BaseEntity {
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "property_type_id", nullable = false)
    private PropertyType propertyType;
    
    @Column(name = "name", nullable = false, length = 50)
    private String name;
    
    @Column(name = "description")
    private String description;
    
    @Column(name = "display_order")
    private Integer displayOrder;
    
    @Column(name = "is_active")
    @Builder.Default
    private boolean isActive = true;
}