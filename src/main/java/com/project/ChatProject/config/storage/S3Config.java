package com.project.ChatProject.config.storage;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

@Configuration
@Profile("s3")
public class S3Config {

    /**
     * 애플리케이션 전체에서 하나의 S3Client 공유
     *
     * S3Client는 Thread-safe 하기 때문에 요청마다 새로 만들면 안됨
     * Spring Bean으로 등록하면 애플리케이션 종료 시 close()도 호출
     */
    @Bean(destroyMethod = "close")
    public S3Client s3Client(S3Properties properties) {
        validateProperties(properties);

        // 로컬에서는 환경 변수나 AWS profile을 사용,
        // 운영에서는 IAM Role credential을 자동으로 탐색
        return S3Client.builder()
                .region(Region.of(properties.region()))
                .credentialsProvider(DefaultCredentialsProvider.builder().build())
                .build();
    }

    private void validateProperties(S3Properties properties) {
        if (!StringUtils.hasText(properties.bucket())) {
            throw new IllegalStateException(
                    "AWS_S3_BUCKET 설정이 필요합니다."
            );
        }

        if (!StringUtils.hasText(properties.region())) {
            throw new IllegalStateException(
                    "AWS_REGION 설정이 필요합니다."
            );
        }
    }
}
