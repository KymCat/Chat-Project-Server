package com.project.ChatProject.dto.result;

import org.springframework.core.io.Resource;

public record ProfileImageResult(
        Resource resource,
        String contentType,
        long sizeBytes
) {

    public static ProfileImageResult stored(
            Resource resource,
            String contentType,
            long sizeBytes
    ) {
        return new ProfileImageResult(
                resource,
                contentType,
                sizeBytes
        );
    }
}