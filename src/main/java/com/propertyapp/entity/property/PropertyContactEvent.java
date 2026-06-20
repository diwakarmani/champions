package com.propertyapp.entity.property;

import com.propertyapp.entity.base.AuditableEntity;
import com.propertyapp.entity.user.User;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "property_contact_events", indexes = {
    @Index(name = "idx_pce_property", columnList = "property_id"),
    @Index(name = "idx_pce_user", columnList = "user_id"),
    @Index(name = "idx_pce_property_user", columnList = "property_id,user_id", unique = false)
})
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SuperBuilder
public class PropertyContactEvent extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "property_id", nullable = false)
    private Property property;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User contactedBy;
}
