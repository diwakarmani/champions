package com.propertyapp.service.notification;

import com.propertyapp.dto.notification.NotificationDTO;
import com.propertyapp.enums.NotificationType;
import org.springframework.data.domain.Page;

public interface NotificationService {

    void send(Long recipientId, NotificationType type, String title, String body,
              String entityType, Long entityId);

    Page<NotificationDTO> listByUser(Long userId, int page, int size);

    long unreadCount(Long userId);

    void markRead(Long notificationId, Long userId);

    int markAllRead(Long userId);
}
