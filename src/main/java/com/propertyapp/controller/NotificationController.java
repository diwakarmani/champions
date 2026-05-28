package com.propertyapp.controller;

import com.propertyapp.dto.common.ApiResponse;
import com.propertyapp.dto.common.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
@Tag(name = "Notifications", description = "In-app notification endpoints")
@SecurityRequirement(name = "Bearer Authentication")
public class NotificationController {

    @GetMapping
    @Operation(summary = "List notifications (paginated)")
    public ResponseEntity<ApiResponse<PageResponse<Object>>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Page<Object> empty = new PageImpl<>(Collections.emptyList(), PageRequest.of(page, size), 0);
        return ResponseEntity.ok(ApiResponse.success(PageResponse.of(empty)));
    }

    @GetMapping("/unread-count")
    @Operation(summary = "Unread notification count")
    public ResponseEntity<ApiResponse<Map<String, Integer>>> unreadCount() {
        return ResponseEntity.ok(ApiResponse.success(Map.of("count", 0)));
    }

    @PatchMapping("/{id}/read")
    @Operation(summary = "Mark notification as read")
    public ResponseEntity<ApiResponse<Void>> markRead(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Marked as read", null));
    }

    @PostMapping("/mark-all-read")
    @Operation(summary = "Mark all notifications as read")
    public ResponseEntity<ApiResponse<Map<String, Integer>>> markAllRead() {
        return ResponseEntity.ok(ApiResponse.success(Map.of("updated", 0)));
    }
}
