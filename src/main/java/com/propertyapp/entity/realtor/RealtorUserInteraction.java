package com.propertyapp.entity.realtor;

import com.propertyapp.entity.base.AuditableEntity;
import com.propertyapp.entity.property.Property;
import com.propertyapp.entity.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Entity
@Table(
        name = "realtor_user_interactions",
        indexes = {
                @Index(name = "idx_realtor_interactions_realtor", columnList = "realtor_id"),
                @Index(name = "idx_realtor_interactions_user", columnList = "user_id"),
                @Index(name = "idx_realtor_interactions_property", columnList = "property_id")
        },
        uniqueConstraints = @UniqueConstraint(
                name = "uk_realtor_user_interaction_type",
                columnNames = {"realtor_id", "user_id", "interaction_type"}
        )
)
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RealtorUserInteraction extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "realtor_id", nullable = false)
    private User realtor;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "property_id")
    private Property property;

    @Column(name = "interaction_type", nullable = false, length = 40)
    private String interactionType;

    @Column(name = "message", columnDefinition = "TEXT")
    private String message;
}
