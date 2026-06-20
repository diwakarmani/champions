package com.propertyapp.entity.notification;

import com.propertyapp.entity.base.AuditableEntity;
import com.propertyapp.entity.user.User;
import com.propertyapp.enums.NotificationType;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;

@Entity
@Table(name = "notifications", indexes = {
    @Index(name = "idx_notif_recipient", columnList = "recipient_id"),
    @Index(name = "idx_notif_read", columnList = "is_read"),
    @Index(name = "idx_notif_recipient_read", columnList = "recipient_id,is_read")
})
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SuperBuilder
public class Notification extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recipient_id", nullable = false)
    private User recipient;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 40)
    private NotificationType type;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "body", columnDefinition = "TEXT")
    private String body;

    @Column(name = "entity_type", length = 30)
    private String entityType;

    @Column(name = "entity_id")
    private Long entityId;

    @Column(name = "is_read")
    @Builder.Default
    private boolean read = false;

    @Column(name = "read_at")
    private LocalDateTime readAt;

    public void markRead() {
        this.read = true;
        this.readAt = LocalDateTime.now();
    }
}
