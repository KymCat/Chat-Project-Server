package com.project.ChatProject.storage;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

public interface FileStorage {

    StoredFile store(
            MultipartFile file,
            StorageCategory category
    );

    StoredFile store(
            byte[] content,
            String originalName,
            String contentType,
            StorageCategory category
    );

    Resource load(String storageKey);

    void delete(String storageKey);
}