package com.propertyapp.controller;

import com.propertyapp.dto.common.ApiResponse;
import com.propertyapp.dto.common.PageResponse;
import com.propertyapp.dto.notification.NotificationDTO;
import com.propertyapp.service.notification.NotificationService;
import com.propertyapp.exception.UnauthorizedException;
import com.propertyapp.util.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
@Tag(name = "Notifications", description = "In-app notification endpoints")
@SecurityRequirement(name = "Bearer Authentication")
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    @Operation(summary = "List notifications (paginated)")
    public ResponseEntity<ApiResponse<PageResponse<NotificationDTO>>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Long userId = SecurityUtils.getCurrentUserId().orElseThrow(() -> new UnauthorizedException("Authentication required"));
        var pageResult = notificationService.listByUser(userId, page, size);
        return ResponseEntity.ok(ApiResponse.success(PageResponse.of(pageResult)));
    }

    @GetMapping("/unread-count")
    @Operation(summary = "Unread notification count")
    public ResponseEntity<ApiResponse<Map<String, Long>>> unreadCount() {
        Long userId = SecurityUtils.getCurrentUserId().orElseThrow(() -> new UnauthorizedException("Authentication required"));
        long count = notificationService.unreadCount(userId);
        return ResponseEntity.ok(ApiResponse.success(Map.of("count", count)));
    }

    @PatchMapping("/{id}/read")
    @Operation(summary = "Mark notification as read")
    public ResponseEntity<ApiResponse<Void>> markRead(@PathVariable Long id) {
        Long userId = SecurityUtils.getCurrentUserId().orElseThrow(() -> new UnauthorizedException("Authentication required"));
        notificationService.markRead(id, userId);
        return ResponseEntity.ok(ApiResponse.success("Marked as read", null));
    }

    @PostMapping("/mark-all-read")
    @Operation(summary = "Mark all notifications as read")
    public ResponseEntity<ApiResponse<Map<String, Integer>>> markAllRead() {
        Long userId = SecurityUtils.getCurrentUserId().orElseThrow(() -> new UnauthorizedException("Authentication required"));
        int updated = notificationService.markAllRead(userId);
        return ResponseEntity.ok(ApiResponse.success(Map.of("updated", updated)));
    }
}
