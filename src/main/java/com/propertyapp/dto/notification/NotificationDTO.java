package com.propertyapp.dto.notification;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class NotificationDTO {
    private Long id;
    private String type;
    private String title;
    private String body;
    private String entityType;
    private Long entityId;
    private boolean read;
    private LocalDateTime readAt;
    private LocalDateTime createdAt;
}
