package com.project.ChatProject.profile;

/**
 * 실제 파일 내용을 검사한 후 신뢰할 수 있는 MIME type을 전달.
 * @param contentType
 */
public record ValidatedProfileImage(
        String contentType
) {
}
