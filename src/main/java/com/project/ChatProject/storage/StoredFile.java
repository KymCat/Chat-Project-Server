package com.project.ChatProject.storage;

public record StoredFile(
        String storageKey,
        String originalName,
        String contentType,
        long sizeBytes
) {
}
