package com.propertyapp.entity.property;

import com.propertyapp.entity.base.AuditableEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "property_images")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PropertyImage extends AuditableEntity {
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "property_id", nullable = false)
    private Property property;
    
    @Column(name = "image_url", nullable = false)
    private String imageUrl;
    
    @Column(name = "thumbnail_url")
    private String thumbnailUrl;
    
    @Column(name = "caption")
    private String caption;
    
    @Column(name = "display_order")
    private Integer displayOrder;
    
    @Column(name = "is_primary")
    @Builder.Default
    private boolean isPrimary = false;
}