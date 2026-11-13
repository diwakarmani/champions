package com.propertyapp.storage;

import com.propertyapp.exception.BadRequestException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;

/**
 * Moves photos stored before ADR-0016 ({@code http://<host>/uploads/<file>} rows, files on the
 * server's disk) into the configured storage, through the same pipeline as new uploads (upright,
 * metadata removed, card size), and rewrites the rows to keys.
 * <p>
 * Dry run reads and processes every file but writes nothing. Apply writes objects, then updates
 * each row only if it still holds the old value. Local files are never deleted, so the old URLs keep
 * working until the server's disk goes away. Running it again finds nothing left to do.
 */
@Slf4j
@Service
public class LegacyImageMigration {

    private final JdbcTemplate jdbc;
    private final ImageProcessor processor;
    private final StorageService storage;
    private final Path uploadDir;

    public LegacyImageMigration(JdbcTemplate jdbc, ImageProcessor processor, StorageService storage,
                                @Value("${app.upload.dir:uploads/}") String uploadDir) {
        this.jdbc = jdbc;
        this.processor = processor;
        this.storage = storage;
        this.uploadDir = Paths.get(uploadDir).toAbsolutePath().normalize();
    }

    public record Report(boolean applied, int filesStored, int rowsUpdated, int alreadyKeys, int otherHosts,
                         List<String> problems, List<String> planned) {
    }

    private record Column(String table, String column, String thumbnailColumn) {
    }

    private static final List<Column> COLUMNS = List.of(
            new Column("property_images", "image_url", "thumbnail_url"),
            new Column("users", "profile_image_url", null),
            new Column("agent_applications", "photo_url", null));

    public Report run(boolean apply) {
        Map<String, Optional<String>> keyByFile = new HashMap<>();
        List<String> problems = new ArrayList<>();
        List<String> planned = new ArrayList<>();
        int[] counts = new int[4]; // files stored, rows updated, already keys, other hosts

        for (Column c : COLUMNS) {
            String select = "SELECT id, " + c.column() + (c.thumbnailColumn() != null ? ", " + c.thumbnailColumn() : "")
                    + " FROM " + c.table() + " WHERE " + c.column() + " IS NOT NULL ORDER BY id";
            for (Map<String, Object> row : jdbc.queryForList(select)) {
                long id = ((Number) row.get("id")).longValue();
                String value = (String) row.get(c.column());
                String where = c.table() + "#" + id;
                if (StorageKeys.isKey(value)) { counts[2]++; continue; }
                Matcher m = ImageRefs.LEGACY_UPLOAD.matcher(value);
                if (!m.matches()) { counts[3]++; continue; }
                String file = m.group(1);
                Optional<String> key = keyByFile.computeIfAbsent(file, f -> migrateFile(f, apply, problems, where, counts));
                if (key.isEmpty()) continue;
                planned.add(where + ": " + value + " -> " + key.get());
                if (!apply) continue;
                int updated;
                if (c.thumbnailColumn() != null) {
                    String thumb = (String) row.get(c.thumbnailColumn());
                    // Old rows had no thumbnail, or the same file; an outside thumbnail is kept.
                    boolean replaceThumb = thumb == null || thumb.equals(value) || ImageRefs.LEGACY_UPLOAD.matcher(thumb).matches();
                    updated = jdbc.update("UPDATE " + c.table() + " SET " + c.column() + " = ?, " + c.thumbnailColumn()
                                    + " = ? WHERE id = ? AND " + c.column() + " = ?",
                            key.get(), replaceThumb ? StorageKeys.cardKeyFor(key.get()) : thumb, id, value);
                } else {
                    updated = jdbc.update("UPDATE " + c.table() + " SET " + c.column() + " = ? WHERE id = ? AND "
                            + c.column() + " = ?", key.get(), id, value);
                }
                if (updated == 1) counts[1]++;
                else problems.add(where + ": changed while migrating, left as is");
            }
        }
        Report report = new Report(apply, counts[0], counts[1], counts[2], counts[3], problems, planned);
        log.info("Image migration ({}): {} files {}, {} rows {}, {} already keys, {} on other hosts, {} problems",
                apply ? "apply" : "dry run", counts[0], apply ? "stored" : "to store", apply ? counts[1] : planned.size(),
                apply ? "updated" : "to update", counts[2], counts[3], problems.size());
        planned.forEach(p -> log.info("  {}", p));
        problems.forEach(p -> log.warn("  {}", p));
        return report;
    }

    private Optional<String> migrateFile(String file, boolean apply, List<String> problems, String where, int[] counts) {
        Path path = uploadDir.resolve(file).normalize();
        if (!path.startsWith(uploadDir) || !Files.isRegularFile(path)) {
            problems.add(where + ": file " + file + " not found in the upload folder, row left as is");
            return Optional.empty();
        }
        ImageProcessor.Result result;
        try {
            result = processor.process(Files.readAllBytes(path));
        } catch (IOException | BadRequestException e) {
            problems.add(where + ": file " + file + " could not be read as an image (" + e.getMessage() + "), row left as is");
            return Optional.empty();
        }
        String key = "images/" + UUID.randomUUID() + result.extension();
        if (apply) {
            storage.put(StorageKeys.cardKeyFor(key), result.card(), result.contentType());
            storage.put(key, result.full(), result.contentType());
        }
        counts[0]++;
        return Optional.of(key);
    }
}
