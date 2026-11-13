package com.propertyapp.storage;

import com.propertyapp.exception.BadRequestException;

import java.net.URI;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Converts between what clients see (a full URL) and what the database keeps (ADR-0016).
 * <ul>
 *   <li>In: a URL of an image this app stored becomes its key; an address on an allowed stock host
 *   is kept as is; anything else is refused (it would show a broken image, or leak visitors'
 *   addresses to whatever host was typed in).</li>
 *   <li>Out: a key becomes the storage's public URL; a stored full URL (stock photos, files not yet
 *   migrated) is returned unchanged.</li>
 * </ul>
 */
public class ImageRefs {

    /** Old local uploads: {@code http(s)://<any host>/uploads/<file>}, from before keys were stored. */
    public static final Pattern LEGACY_UPLOAD = Pattern.compile("^https?://[^/\\s]+/uploads/([A-Za-z0-9._-]+)$");

    private static volatile ImageRefs current;

    private final StorageService storage;
    private final Set<String> externalHosts;
    private final String localUploadsPrefix;

    public ImageRefs(StorageService storage, String externalHosts, String baseUrl) {
        this.storage = storage;
        this.externalHosts = externalHosts == null ? Set.of() : Pattern.compile(",").splitAsStream(externalHosts)
                .map(String::trim).filter(s -> !s.isEmpty()).map(s -> s.toLowerCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
        this.localUploadsPrefix = LocalDiskStorage.stripSlash(baseUrl) + "/uploads/";
    }

    /** Used by {@link ImageUrlSerializer}, which Jackson creates without Spring. */
    public static void install(ImageRefs refs) {
        current = refs;
    }

    public static String resolveCurrent(String stored) {
        ImageRefs refs = current;
        return refs == null ? stored : refs.toPublicUrl(stored);
    }

    public String toPublicUrl(String stored) {
        if (stored == null || stored.isBlank()) return stored;
        return StorageKeys.isKey(stored) ? storage.publicUrl(stored) : stored;
    }

    /**
     * What to save for a URL a client sent. Null and blank stay as they are (callers decide whether
     * blank clears a photo).
     */
    public String toStored(String url) {
        if (url == null || url.isBlank()) return url;
        String value = url.trim();
        for (String prefix : new String[]{storage.publicUrl(""), localUploadsPrefix}) {
            if (!prefix.isEmpty() && value.startsWith(prefix)) {
                String key = value.substring(prefix.length());
                if (StorageKeys.isKey(key)) return key;
            }
        }
        // Files uploaded before keys (S13) are still served from /uploads/ on this server.
        if (value.startsWith(localUploadsPrefix) && LEGACY_UPLOAD.matcher(value).matches()) return value;
        String host;
        try {
            host = URI.create(value).getHost();
        } catch (IllegalArgumentException e) {
            host = null;
        }
        if (host != null && value.startsWith("https://") && externalHosts.contains(host.toLowerCase(Locale.ROOT))) {
            return value;
        }
        throw new BadRequestException("Upload the photo first: only photos stored by this service can be used.");
    }

    /** The card-size image stored with an uploaded one, or null. */
    public String cardFor(String stored) {
        return StorageKeys.cardKeyFor(stored);
    }
}
