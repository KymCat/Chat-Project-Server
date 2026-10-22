package com.project.ChatProject.config.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * application.yml의 chat.storage.s3 설정을 Java 객체로 변환
 *
 * @param bucket : 파일을 저장할 s3 bucket 이름
 * @param region : region s3 bucket이 생성된 AWS Region
 */
@ConfigurationProperties(prefix = "chat.storage.s3")
public record S3Properties(
        String bucket,
        String region
) {
}
