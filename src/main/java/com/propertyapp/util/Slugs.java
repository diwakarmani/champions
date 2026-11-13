package com.propertyapp.util;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;

/** URL slugs, computed rather than stored; the id at the end is what pages look up (ADR-0006, ADR-0009). */
public final class Slugs {

    static final int MAX_LENGTH = 80;

    private Slugs() {
    }

    public static String of(String fallback, String... parts) {
        String joined = Arrays.stream(parts).filter(Objects::nonNull).collect(Collectors.joining(" "));
        String ascii = Normalizer.normalize(joined, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        String slug = ascii.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
        if (slug.length() > MAX_LENGTH) {
            slug = slug.substring(0, MAX_LENGTH);
            int lastHyphen = slug.lastIndexOf('-');
            if (lastHyphen > MAX_LENGTH / 2) {
                slug = slug.substring(0, lastHyphen);
            }
            slug = slug.replaceAll("-+$", "");
        }
        return slug.isEmpty() ? fallback : slug;
    }

    public static String propertyPath(String slug, Long id) {
        return "/property/" + slug + "-" + id;
    }

    public static String agentPath(String slug, Long id) {
        return "/agents/" + slug + "-" + id;
    }
}
