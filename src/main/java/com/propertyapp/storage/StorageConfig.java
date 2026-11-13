package com.propertyapp.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Picks the storage from {@code app.storage.type} and fails at start-up when s3 is half configured. */
@Configuration
public class StorageConfig {

    @Bean
    public StorageService storageService(
            @Value("${app.storage.type:local}") String type,
            @Value("${app.upload.dir:uploads/}") String uploadDir,
            @Value("${app.base-url:http://localhost:8080}") String baseUrl,
            @Value("${app.storage.public-base-url:}") String publicBaseUrl,
            @Value("${app.storage.s3.endpoint:}") String endpoint,
            @Value("${app.storage.s3.region:auto}") String region,
            @Value("${app.storage.s3.bucket:}") String bucket,
            @Value("${app.storage.s3.access-key:}") String accessKey,
            @Value("${app.storage.s3.secret-key:}") String secretKey,
            @Value("${app.storage.s3.path-style:true}") boolean pathStyle) {
        return switch (type.trim().toLowerCase()) {
            case "local" -> new LocalDiskStorage(uploadDir, baseUrl);
            case "s3" -> {
                require("STORAGE_S3_BUCKET", bucket);
                require("STORAGE_S3_ACCESS_KEY", accessKey);
                require("STORAGE_S3_SECRET_KEY", secretKey);
                require("STORAGE_PUBLIC_BASE_URL", publicBaseUrl);
                if (!publicBaseUrl.startsWith("https://") && !publicBaseUrl.startsWith("http://")) {
                    throw new IllegalStateException("STORAGE_PUBLIC_BASE_URL must be an http(s) address");
                }
                yield new S3CompatibleStorage(
                        S3CompatibleStorage.client(endpoint, region, accessKey, secretKey, pathStyle),
                        bucket, publicBaseUrl);
            }
            default -> throw new IllegalStateException("app.storage.type must be local or s3, not " + type);
        };
    }

    @Bean
    public ImageRefs imageRefs(StorageService storage,
                               @Value("${app.storage.external-hosts:images.unsplash.com}") String externalHosts,
                               @Value("${app.base-url:http://localhost:8080}") String baseUrl) {
        ImageRefs refs = new ImageRefs(storage, externalHosts, baseUrl);
        ImageRefs.install(refs);
        return refs;
    }

    @Bean
    public ImageProcessor imageProcessor(@Value("${app.storage.max-pixels:50000000}") long maxPixels) {
        return new ImageProcessor(maxPixels);
    }

    @Bean
    public ImageUploadService imageUploadService(ImageProcessor processor, StorageService storage) {
        return new ImageUploadService(processor, storage);
    }

    private static void require(String name, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " is required when STORAGE_TYPE=s3");
        }
    }
}
