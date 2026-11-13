package com.propertyapp.controller;

import com.propertyapp.dto.common.ApiResponse;
import com.propertyapp.storage.ImageProcessor;
import com.propertyapp.storage.ImageUploadService;
import com.propertyapp.storage.StorageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Set;

@RestController
@RequestMapping("/api/upload")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "File Upload", description = "Upload images and files")
public class FileUploadController {

    private final ImageUploadService uploads;
    private final StorageService storage;

    private static final Set<String> ALLOWED_TYPES = Set.of(
            "image/jpeg", "image/jpg", "image/png", "image/gif", "image/webp"
    );

    /**
     * Stores a photo (ADR-0016): the type is checked by its bytes, the picture is turned upright,
     * camera metadata (GPS location) is removed and a card size is made. Returns the full-size URL.
     */
    @PostMapping("/image")
    // Listing photos only: agents and admins (ADR-0016 Am. 1). Others could otherwise use our bucket as free hosting.
    @PreAuthorize("hasAnyRole('REALTOR','SUPER_ADMIN')")
    @SecurityRequirement(name = "Bearer Authentication")
    @Operation(summary = "Upload image", description = "Upload an image file and get back its URL. "
            + "JPEG, PNG, GIF or WebP; metadata is removed and the image is stored as JPEG or PNG.")
    public ResponseEntity<ApiResponse<String>> uploadImage(
            @RequestParam("file") MultipartFile file
    ) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("File is empty"));
        }

        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_TYPES.contains(contentType.toLowerCase())) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(ImageProcessor.NOT_AN_IMAGE));
        }

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException ex) {
            log.error("Could not read upload", ex);
            return ResponseEntity.badRequest().body(ApiResponse.error("We could not read the file. Please try again."));
        }
        // BadRequestException from the processor becomes a 400 with its message.
        String key;
        try {
            key = uploads.store(bytes);
        } catch (com.propertyapp.exception.BadRequestException bad) {
            throw bad;
        } catch (RuntimeException storageDown) {
            // Storage unreachable or refusing (S3 error, network): the client may retry.
            log.error("Could not store image in {}", storage.kind(), storageDown);
            return ResponseEntity.status(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE)
                    .body(ApiResponse.error("We could not store the photo right now. Please try again."));
        }
        return ResponseEntity.ok(ApiResponse.success("Image uploaded successfully", storage.publicUrl(key)));
    }
}
