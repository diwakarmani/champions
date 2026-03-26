package com.propertyapp.entity.user;

import com.propertyapp.entity.base.AuditableEntity;
import com.propertyapp.entity.property.Property;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "user_favorites", uniqueConstraints = {
    @UniqueConstraint(name = "uk_user_property_favorite", columnNames = {"user_id", "property_id"})
})
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserFavorite extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "property_id", nullable = false)
    private Property property;
}
