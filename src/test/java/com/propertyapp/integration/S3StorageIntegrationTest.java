package com.propertyapp.integration;

import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.exif.GpsDirectory;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.propertyapp.entity.user.User;
import com.propertyapp.repository.property.PropertyRepository;
import com.propertyapp.repository.property.PropertyTypeRepository;
import com.propertyapp.repository.user.UserRepository;
import com.propertyapp.storage.LegacyImageMigration;
import com.propertyapp.storage.S3CompatibleStorage;
import com.propertyapp.storage.StorageKeys;
import com.propertyapp.storage.StorageService;
import com.propertyapp.storage.TestImages;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.UUID;

import static com.propertyapp.integration.Fixtures.authed;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * S13, ADR-0016: the app on real S3-compatible storage (MinIO) and real PostGIS. Uploads go to the
 * bucket without camera metadata, the database keeps keys, the API hands out CDN URLs, and the
 * migration tool moves old /uploads/ files.
 */
class S3StorageIntegrationTest extends AbstractPostgisIntegrationTest {

    static final String BUCKET = "champion-images";
    static final String CDN = "https://img.champion.test";
    static final Path UPLOADS = Paths.get("target/test-uploads").toAbsolutePath();

    // MinIO stopped publishing images to Docker Hub and quay.io (found in S13); Bitnami's frozen
    // archive still serves this MinIO release. Same server, so the adapter is tested on MinIO (ADR-0016).
    static final String MINIO_USER = "champion";
    static final String MINIO_PASSWORD = "champion-secret";
    static final GenericContainer<?> MINIO = new GenericContainer<>("bitnamilegacy/minio:2024.10.13")
            .withEnv("MINIO_ROOT_USER", MINIO_USER)
            .withEnv("MINIO_ROOT_PASSWORD", MINIO_PASSWORD)
            .withExposedPorts(9000)
            .waitingFor(Wait.forHttp("/minio/health/live").forPort(9000));
    static final S3Client S3;

    static String s3Url() {
        return "http://" + MINIO.getHost() + ":" + MINIO.getMappedPort(9000);
    }

    static {
        MINIO.start();
        S3 = S3CompatibleStorage.client(s3Url(), "us-east-1", MINIO_USER, MINIO_PASSWORD, true);
        S3.createBucket(CreateBucketRequest.builder().bucket(BUCKET).build());
    }

    @DynamicPropertySource
    static void storage(DynamicPropertyRegistry registry) {
        registry.add("app.storage.type", () -> "s3");
        registry.add("app.storage.s3.endpoint", S3StorageIntegrationTest::s3Url);
        registry.add("app.storage.s3.region", () -> "us-east-1");
        registry.add("app.storage.s3.bucket", () -> BUCKET);
        registry.add("app.storage.s3.access-key", () -> MINIO_USER);
        registry.add("app.storage.s3.secret-key", () -> MINIO_PASSWORD);
        registry.add("app.storage.public-base-url", () -> CDN + "/");
        registry.add("app.base-url", () -> "http://api.champion.test");
        registry.add("app.upload.dir", UPLOADS::toString);
    }

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserRepository userRepository;
    @Autowired PropertyRepository propertyRepository;
    @Autowired PropertyTypeRepository propertyTypeRepository;
    @Autowired JdbcTemplate jdbc;
    @Autowired StorageService storage;
    @Autowired LegacyImageMigration migration;

    Fixtures f;
    User agent;
    String token;

    @BeforeEach
    void setUp() throws Exception {
        f = new Fixtures(mockMvc, objectMapper, userRepository, propertyRepository, propertyTypeRepository, jdbc);
        agent = f.user("REALTOR");
        token = f.login(agent.getEmail());
    }

    @Test
    void theAdapterWritesReadsAndDeletes() {
        assertThat(storage.kind()).isEqualTo("s3");
        String key = "images/" + UUID.randomUUID() + ".jpg";
        assertThat(storage.exists(key)).isFalse();
        assertThat(storage.get(key)).isEmpty();
        storage.put(key, new byte[]{1, 2, 3}, "image/jpeg");
        assertThat(storage.exists(key)).isTrue();
        assertThat(storage.get(key)).hasValueSatisfying(b -> assertThat(b).containsExactly(1, 2, 3));
        storage.delete(key);
        assertThat(storage.exists(key)).isFalse();
        assertThat(storage.publicUrl(key)).isEqualTo(CDN + "/" + key);
    }

    @Test
    void anUploadLandsInTheBucketWithoutGpsAndTheListingKeepsAKey() throws Exception {
        String url = upload(TestImages.jpegWithExif(1200, 900, 6));
        assertThat(url).matches("https://img\\.champion\\.test/images/[0-9a-f-]{36}\\.jpg");
        String key = url.substring(CDN.length() + 1);

        var object = S3.getObjectAsBytes(GetObjectRequest.builder().bucket(BUCKET).key(key).build());
        assertThat(object.response().contentType()).isEqualTo("image/jpeg");
        assertThat(object.response().cacheControl()).contains("immutable");
        var metadata = ImageMetadataReader.readMetadata(new ByteArrayInputStream(object.asByteArray()));
        assertThat(metadata.getFirstDirectoryOfType(GpsDirectory.class)).isNull();
        assertThat(storage.exists(StorageKeys.cardKeyFor(key))).isTrue();

        long listing = f.listing(agent, "DRAFT");
        mockMvc.perform(authed(post("/api/properties/" + listing + "/images"), token)
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(Map.of("imageUrl", url))))
                .andExpect(status().isCreated());
        Map<String, Object> row = jdbc.queryForMap("SELECT image_url, thumbnail_url FROM property_images WHERE property_id = ?", listing);
        assertThat(row.get("image_url")).isEqualTo(key);
        assertThat(row.get("thumbnail_url")).isEqualTo(StorageKeys.cardKeyFor(key));

        JsonNode images = json(mockMvc.perform(authed(get("/api/properties/" + listing + "/images"), token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("data");
        assertThat(images.get(0).path("imageUrl").asText()).isEqualTo(url);
        assertThat(images.get(0).path("thumbnailUrl").asText()).isEqualTo(CDN + "/" + StorageKeys.cardKeyFor(key));
    }

    @Test
    void photosOnOtherHostsAreRefusedAndStockPhotosStillWork() throws Exception {
        long listing = f.listing(agent, "DRAFT");
        for (String foreign : new String[]{"https://tracker.example/p.jpg", "http://api.champion.test/elsewhere/x.jpg",
                CDN + "/images/../x.jpg"}) {
            mockMvc.perform(authed(post("/api/properties/" + listing + "/images"), token)
                            .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(Map.of("imageUrl", foreign))))
                    .andExpect(status().isBadRequest());
        }
        mockMvc.perform(authed(post("/api/properties/" + listing + "/images"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"imageUrl\":\"https://images.unsplash.com/photo-1?w=1200\"}"))
                .andExpect(status().isCreated());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM property_images WHERE property_id = ?", Integer.class, listing))
                .isEqualTo(1);
    }

    @Test
    void aProfilePhotoIsSavedAsAKeyAndBlankRemovesIt() throws Exception {
        String url = upload(TestImages.jpeg(400, 400));
        mockMvc.perform(authed(put("/api/users/me"), token).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("profileImageUrl", url))))
                .andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT profile_image_url FROM users WHERE id = ?", String.class, agent.getId()))
                .isEqualTo(url.substring(CDN.length() + 1));
        JsonNode me = json(mockMvc.perform(authed(get("/api/users/me"), token)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        assertThat(me.path("data").path("profileImageUrl").asText()).isEqualTo(url);

        mockMvc.perform(authed(put("/api/users/me"), token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"profileImageUrl\":\"https://tracker.example/me.jpg\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(authed(put("/api/users/me"), token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"profileImageUrl\":\"\"}"))
                .andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT profile_image_url FROM users WHERE id = ?", String.class, agent.getId()))
                .isNull();
    }

    @Test
    void theMigrationToolDryRunsThenMovesOldUploadsOnce() throws Exception {
        Files.createDirectories(UPLOADS);
        String present = UUID.randomUUID() + ".jpg";
        String missing = UUID.randomUUID() + ".jpg";
        String notImage = UUID.randomUUID() + ".png";
        Files.write(UPLOADS.resolve(present), TestImages.jpegWithExif(300, 200, 1));
        Files.writeString(UPLOADS.resolve(notImage), "<svg onload=alert(1)>");
        String oldUrl = "http://192.168.0.107:8080/uploads/" + present;

        long listing = f.listing(agent, "DRAFT");
        long img = insertImage(listing, oldUrl, oldUrl);
        long missingImg = insertImage(listing, "http://192.168.0.107:8080/uploads/" + missing, null);
        long junkImg = insertImage(listing, "http://192.168.0.107:8080/uploads/" + notImage, null);
        long stockImg = insertImage(listing, "https://images.unsplash.com/photo-2", "https://images.unsplash.com/photo-2");
        jdbc.update("UPDATE users SET profile_image_url = ? WHERE id = ?", oldUrl, agent.getId());

        LegacyImageMigration.Report dry = migration.run(false);
        assertThat(dry.planned()).anySatisfy(p -> assertThat(p).startsWith("property_images#" + img + ":"));
        assertThat(dry.planned()).anySatisfy(p -> assertThat(p).startsWith("users#" + agent.getId() + ":"));
        assertThat(dry.problems()).anySatisfy(p -> assertThat(p).startsWith("property_images#" + missingImg + ":").contains("not found"));
        assertThat(dry.problems()).anySatisfy(p -> assertThat(p).startsWith("property_images#" + junkImg + ":").contains("could not be read"));
        assertThat(imageUrl(img)).as("dry run writes nothing").isEqualTo(oldUrl);

        LegacyImageMigration.Report applied = migration.run(true);
        assertThat(applied.applied()).isTrue();
        String key = imageUrl(img);
        assertThat(StorageKeys.isKey(key)).isTrue();
        assertThat(jdbc.queryForObject("SELECT thumbnail_url FROM property_images WHERE id = ?", String.class, img))
                .isEqualTo(StorageKeys.cardKeyFor(key));
        // The same file used by two rows is stored once and both rows point at it.
        assertThat(jdbc.queryForObject("SELECT profile_image_url FROM users WHERE id = ?", String.class, agent.getId()))
                .isEqualTo(key);
        assertThat(storage.exists(key)).isTrue();
        assertThat(storage.exists(StorageKeys.cardKeyFor(key))).isTrue();
        var metadata = ImageMetadataReader.readMetadata(new ByteArrayInputStream(storage.get(key).orElseThrow()));
        assertThat(metadata.getFirstDirectoryOfType(GpsDirectory.class)).isNull();
        // Problem rows and other hosts are left alone; old files stay on disk.
        assertThat(imageUrl(missingImg)).endsWith(missing);
        assertThat(imageUrl(junkImg)).endsWith(notImage);
        assertThat(imageUrl(stockImg)).isEqualTo("https://images.unsplash.com/photo-2");
        assertThat(Files.exists(UPLOADS.resolve(present))).isTrue();

        LegacyImageMigration.Report again = migration.run(true);
        assertThat(again.planned()).noneSatisfy(p -> assertThat(p).startsWith("property_images#" + img + ":"));
        assertThat(imageUrl(img)).isEqualTo(key);

        // The public API serves the CDN address for the migrated key.
        JsonNode images = json(mockMvc.perform(authed(get("/api/properties/" + listing + "/images"), token))
                .andReturn().getResponse().getContentAsString()).path("data");
        assertThat(images.toString()).contains(CDN + "/" + key);
    }

    private long insertImage(long listing, String url, String thumb) {
        return jdbc.queryForObject("INSERT INTO property_images (property_id, image_url, thumbnail_url, display_order, is_primary, created_at, updated_at) "
                + "VALUES (?, ?, ?, 0, false, now(), now()) RETURNING id", Long.class, listing, url, thumb);
    }

    private String imageUrl(long id) {
        return jdbc.queryForObject("SELECT image_url FROM property_images WHERE id = ?", String.class, id);
    }

    private String upload(byte[] bytes) throws Exception {
        String body = mockMvc.perform(authed(multipart("/api/upload/image")
                        .file(new MockMultipartFile("file", "photo.jpg", "image/jpeg", bytes)), token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json(body).path("data").asText();
    }

    private JsonNode json(String body) throws Exception {
        return objectMapper.readTree(body);
    }
}
