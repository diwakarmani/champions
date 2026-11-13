package com.propertyapp.storage;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.UUID;

/** Processes an upload and stores both sizes. Returns the full-size key. */
@Slf4j
@RequiredArgsConstructor
public class ImageUploadService {

    private final ImageProcessor processor;
    private final StorageService storage;

    public String store(byte[] upload) {
        ImageProcessor.Result result = processor.process(upload);
        String key = "images/" + UUID.randomUUID() + result.extension();
        // Card first: a full-size key in the database always has its card next to it.
        storage.put(StorageKeys.cardKeyFor(key), result.card(), result.contentType());
        storage.put(key, result.full(), result.contentType());
        log.info("Stored image {} ({}x{}, {} bytes) in {}", key, result.width(), result.height(),
                result.full().length, storage.kind());
        return key;
    }
}
