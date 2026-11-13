package com.propertyapp.dto.publicapi;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;

/** One URL for the web sitemap, with a real last-modified time (ADR-0009). */
@Value
@Builder
public class SitemapEntryDTO {

    Long id;
    String path;
    LocalDateTime lastModified;
}
