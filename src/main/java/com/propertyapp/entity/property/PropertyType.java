package com.propertyapp.entity.property;

import com.propertyapp.entity.base.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "property_types")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PropertyType extends BaseEntity {
    
    @Column(name = "name", nullable = false, unique = true, length = 50)
    private String name;
    
    @Column(name = "description")
    private String description;
    
    @Column(name = "icon_url")
    private String iconUrl;
    
    @Column(name = "display_order")
    private Integer displayOrder;
    
    @Column(name = "is_active")
    @Builder.Default
    private boolean isActive = true;
    
    @OneToMany(mappedBy = "propertyType", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private Set<PropertySubType> subTypes = new HashSet<>();
}