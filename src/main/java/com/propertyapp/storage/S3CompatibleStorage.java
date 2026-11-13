package com.propertyapp.storage;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import java.time.Duration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.net.URI;
import java.util.Optional;

/**
 * Any S3-compatible bucket: Cloudflare R2 (recommended, no egress fees), AWS S3, Backblaze B2, MinIO.
 * Objects are public through {@code publicBaseUrl} (a CDN or the bucket's public domain); keys are
 * random UUIDs, so they are cached for a year.
 */
public class S3CompatibleStorage implements StorageService {

    static final String CACHE_CONTROL = "public, max-age=31536000, immutable";

    private final S3Client client;
    private final String bucket;
    private final String publicBaseUrl;

    public S3CompatibleStorage(S3Client client, String bucket, String publicBaseUrl) {
        this.client = client;
        this.bucket = bucket;
        this.publicBaseUrl = LocalDiskStorage.stripSlash(publicBaseUrl) + "/";
    }

    public static S3Client client(String endpoint, String region, String accessKey, String secretKey, boolean pathStyle) {
        var builder = S3Client.builder()
                // Apache client: pooled connections are checked before reuse. The URL-connection client reused
                // a connection the server had closed and failed uploads with "Unexpected end of file" (S13).
                .httpClient(ApacheHttpClient.builder()
                        .connectionTimeout(Duration.ofSeconds(5))
                        .socketTimeout(Duration.ofSeconds(30))
                        .connectionMaxIdleTime(Duration.ofSeconds(20))
                        .build())
                .region(Region.of(region == null || region.isBlank() ? "auto" : region))
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey)))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(pathStyle).build());
        if (endpoint != null && !endpoint.isBlank()) builder.endpointOverride(URI.create(endpoint));
        return builder.build();
    }

    @Override
    public void put(String key, byte[] bytes, String contentType) {
        client.putObject(PutObjectRequest.builder()
                        .bucket(bucket).key(key)
                        .contentType(contentType)
                        .cacheControl(CACHE_CONTROL)
                        .build(),
                RequestBody.fromBytes(bytes));
    }

    @Override
    public Optional<byte[]> get(String key) {
        try {
            return Optional.of(client.getObjectAsBytes(GetObjectRequest.builder().bucket(bucket).key(key).build())
                    .asByteArray());
        } catch (NoSuchKeyException e) {
            return Optional.empty();
        }
    }

    @Override
    public boolean exists(String key) {
        try {
            client.headObject(HeadObjectRequest.builder().bucket(bucket).key(key).build());
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        } catch (S3Exception e) {
            if (e.statusCode() == 404) return false;
            throw e;
        }
    }

    @Override
    public void delete(String key) {
        client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
    }

    @Override
    public String publicUrl(String key) {
        return publicBaseUrl + key;
    }

    @Override
    public String kind() {
        return "s3";
    }
}
