package com.project.ChatProject.storage.s3;

import com.project.ChatProject.config.storage.S3Properties;
import com.project.ChatProject.exception.CustomException;
import com.project.ChatProject.exception.ErrorCode;
import com.project.ChatProject.storage.FileStorage;
import com.project.ChatProject.storage.StorageCategory;
import com.project.ChatProject.storage.StoredFile;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.InputStreamSource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

@Slf4j
@Component
@Profile("s3")
@RequiredArgsConstructor
public class S3FileStorage implements FileStorage {

    private final S3Client s3Client;
    private final S3Properties properties;

    /**
     * MultipartFile을 S3에 저장
     *
     * 실제 서비스 흐름에서는 허용된 파일만 이곳에 도달
     */
    @Override
    public StoredFile store(
            MultipartFile file,
            StorageCategory category
    ) {
        return store(
                file,
                resolveOriginalName(file),
                resolveContentType(file),
                file.getSize(),
                category
        );
    }

    @Override
    public StoredFile store(
            byte[] content,
            String originalName,
            String contentType,
            StorageCategory category
    ) {
        return store(
                new ByteArrayResource(content),
                originalName,
                contentType,
                content.length,
                category
        );
    }

    private StoredFile store(
            InputStreamSource source,
            String originalName,
            String contentType,
            long sizeBytes,
            StorageCategory category
    ) {
        String storageKey =
                createStorageKey(category);

        PutObjectRequest request =
                PutObjectRequest.builder()
                        .bucket(properties.bucket())
                        .key(storageKey)
                        .contentType(contentType)
                        .build();

        try (InputStream inputStream =
                     source.getInputStream()) {
            s3Client.putObject(
                    request,
                    RequestBody.fromInputStream(
                            inputStream,
                            sizeBytes
                    )
            );

            return new StoredFile(
                    storageKey,
                    originalName,
                    contentType,
                    sizeBytes
            );
        } catch (IOException | SdkException exception) {
            log.error(
                    "S3 file upload failed, bucket={}, storageKey={}",
                    properties.bucket(),
                    storageKey,
                    exception
            );

            throw new CustomException(
                    ErrorCode.STORAGE_OPERATION_FAILED
            );
        }
    }

    /**
     * S3 object를 streaming Resource로 반환
     *
     * ByteArrayResource를 사용하면 파일 전체가 Memory에 올라감
     * InputStreamResource는 S3 response stream을 그대로 HTTP 응답으로 전달
     */
    @Override
    public Resource load(String storageKey) {
        validateStorageKey(storageKey);

        GetObjectRequest request = GetObjectRequest.builder()
                .bucket(properties.bucket())
                .key(storageKey)
                .build();

        try {
            ResponseInputStream<GetObjectResponse> inputStream =
                    s3Client.getObject(request);

            return new InputStreamResource(inputStream);
        } catch (S3Exception exception) {
            if (exception.statusCode() == 404) {
                throw new CustomException(
                        ErrorCode.STORAGE_OBJECT_NOT_FOUND
                );
            }

            log.error(
                    "S3 file download failed, bucket={}, storageKey={}",
                    properties.bucket(),
                    storageKey,
                    exception
            );

            throw new CustomException(
                    ErrorCode.STORAGE_OPERATION_FAILED
            );
        } catch (SdkException exception) {
            log.error(
                    "S3 file download failed, bucket={}, storageKey={}",
                    properties.bucket(),
                    storageKey,
                    exception
            );

            throw new CustomException(
                    ErrorCode.STORAGE_OPERATION_FAILED
            );
        }
    }

    /**
     * S3 object를 삭제
     *
     * S3 DeleteObject는 대상 object가 없어도 성공으로 처리
     * 따라서 LocalFileStorage의 deleteIfExists처럼 멱등적으로 사용 가능
     */
    @Override
    public void delete(String storageKey) {
        validateStorageKey(storageKey);

        DeleteObjectRequest request = DeleteObjectRequest.builder()
                .bucket(properties.bucket())
                .key(storageKey)
                .build();

        try {
            s3Client.deleteObject(request);
        } catch (SdkException exception) {
            log.error(
                    "S3 file delete failed, bucket={}, storageKey={}",
                    properties.bucket(),
                    storageKey,
                    exception
            );

            throw new CustomException(
                    ErrorCode.STORAGE_OPERATION_FAILED
            );
        }
    }

    /**
     * 사용자 파일명을 S3 key에 사용하지 않음
     *
     * 이유:
     * 1. 같은 이름의 파일 충돌 방지
     * 2. 경로 조작 문자열 차단
     * 3. 사용자 파일명 노출 방지
     */
    private String createStorageKey(StorageCategory category) {
        LocalDate currentDate = LocalDate.now(ZoneOffset.UTC);

        return "%s/%d/%02d/%02d/%s".formatted(
                category.getPrefix(),
                currentDate.getYear(),
                currentDate.getMonthValue(),
                currentDate.getDayOfMonth(),
                UUID.randomUUID()
        );
    }

    private String resolveOriginalName(MultipartFile file) {
        String originalName = file.getOriginalFilename();

        if (originalName == null || originalName.isBlank()) {
            return "file";
        }

        // 브라우저가 C:\fakepath\photo.png처럼 전달하는 경우에 대비
        String normalizedName = originalName.replace('\\', '/');
        int separatorIndex = normalizedName.lastIndexOf('/');

        String fileName = separatorIndex >= 0
                ? normalizedName.substring(separatorIndex + 1)
                : normalizedName;

        return fileName.isBlank() ? "file" : fileName;
    }

    private String resolveContentType(MultipartFile file) {
        String contentType = file.getContentType();

        return contentType == null || contentType.isBlank()
                ? MediaType.APPLICATION_OCTET_STREAM_VALUE
                : contentType;
    }

    private void validateStorageKey(String storageKey) {
        if (storageKey == null || storageKey.isBlank()) {
            throw new CustomException(
                    ErrorCode.INVALID_STORAGE_KEY
            );
        }
    }
}