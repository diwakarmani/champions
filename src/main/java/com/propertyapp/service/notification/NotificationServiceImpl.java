package com.propertyapp.service.notification;

import com.propertyapp.dto.notification.NotificationDTO;
import com.propertyapp.entity.notification.Notification;
import com.propertyapp.entity.user.User;
import com.propertyapp.enums.NotificationType;
import com.propertyapp.exception.ResourceNotFoundException;
import com.propertyapp.exception.UnauthorizedException;
import com.propertyapp.repository.notification.NotificationRepository;
import com.propertyapp.repository.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    @Override
    @Async
    @Transactional
    public void send(Long recipientId, NotificationType type, String title, String body,
                     String entityType, Long entityId) {
        try {
            User recipient = userRepository.findById(recipientId).orElse(null);
            if (recipient == null) {
                log.warn("Cannot send notification: recipient {} not found", recipientId);
                return;
            }
            Notification notification = Notification.builder()
                    .recipient(recipient)
                    .type(type)
                    .title(title)
                    .body(body)
                    .entityType(entityType)
                    .entityId(entityId)
                    .build();
            notificationRepository.save(notification);
            log.debug("Notification sent to user {}: {}", recipientId, title);
        } catch (Exception e) {
            log.error("Failed to send notification to user {}: {}", recipientId, e.getMessage());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Page<NotificationDTO> listByUser(Long userId, int page, int size) {
        return notificationRepository
                .findByRecipientIdOrderByCreatedAtDesc(userId, PageRequest.of(page, size))
                .map(this::toDTO);
    }

    @Override
    @Transactional(readOnly = true)
    public long unreadCount(Long userId) {
        return notificationRepository.countByRecipientIdAndReadFalse(userId);
    }

    @Override
    @Transactional
    public void markRead(Long notificationId, Long userId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found"));
        if (!notification.getRecipient().getId().equals(userId)) {
            throw new UnauthorizedException("Not your notification");
        }
        notification.markRead();
    }

    @Override
    @Transactional
    public int markAllRead(Long userId) {
        return notificationRepository.markAllReadByRecipientId(userId);
    }

    private NotificationDTO toDTO(Notification n) {
        return NotificationDTO.builder()
                .id(n.getId())
                .type(n.getType().name())
                .title(n.getTitle())
                .body(n.getBody())
                .entityType(n.getEntityType())
                .entityId(n.getEntityId())
                .read(n.isRead())
                .readAt(n.getReadAt())
                .createdAt(n.getCreatedAt())
                .build();
    }
}
