package com.propertyapp.entity.inquiry;

import com.propertyapp.entity.base.AuditableEntity;
import com.propertyapp.entity.property.Property;
import com.propertyapp.entity.user.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "inquiries", indexes = {
    @Index(name = "idx_inquiry_property", columnList = "property_id"),
    @Index(name = "idx_inquiry_inquirer", columnList = "inquirer_id")
})
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SuperBuilder
public class Inquiry extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "property_id", nullable = false)
    private Property property;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "inquirer_id")
    private User inquirer;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 100)
    private String email;

    @Column(length = 20)
    private String phone;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private InquiryStatus status;

    @PrePersist
    private void prePersist() {
        if (status == null) status = InquiryStatus.NEW;
    }
}
