package com.project.ChatProject.dto.result;

import org.springframework.core.io.Resource;

public record ProfileImageResult(
        Resource resource,
        String externalUrl,
        String contentType,
        Long sizeBytes
) {

    public static ProfileImageResult stored(
            Resource resource,
            String contentType,
            long sizeBytes
    ) {
        return new ProfileImageResult(
                resource,
                null,
                contentType,
                sizeBytes
        );
    }

    public static ProfileImageResult external(String externalUrl) {
        return new ProfileImageResult(
                null,
                externalUrl,
                null,
                null
        );
    }

    public boolean isExternal() {
        return externalUrl != null;
    }
}
