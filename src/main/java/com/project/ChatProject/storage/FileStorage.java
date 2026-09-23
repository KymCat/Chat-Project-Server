package com.project.ChatProject.storage;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

public interface FileStorage {

    StoredFile store(MultipartFile file);

    Resource load(String storageKey);

    void delete(String storageKey);
}
