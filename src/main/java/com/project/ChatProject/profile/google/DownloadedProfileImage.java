package com.project.ChatProject.profile.google;

public record DownloadedProfileImage(

        // 다운로드 된 이미지 byte만 가짐
        byte[] content
) {

    public long sizeBytes() {
        return content.length;
    }
}
