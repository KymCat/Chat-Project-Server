package com.project.ChatProject.storage.local;

import com.project.ChatProject.config.storage.StorageProperties;
import com.project.ChatProject.exception.CustomException;
import com.project.ChatProject.exception.ErrorCode;
import com.project.ChatProject.storage.FileStorage;
import com.project.ChatProject.storage.StoredFile;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

@Slf4j
@Component
public class LocalFileStorage implements FileStorage {

    private final Path root;

    public LocalFileStorage(StorageProperties properties) {
        this.root = properties.localRoot()
                .toAbsolutePath()
                .normalize();
    }

    @PostConstruct
    void init() {
        try {
            Files.createDirectories(root);
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Local 첨부파일 저장 디렉터리를 생성할 수 없습니다:" + root,
                    exception
            );
        }
    }

    @Override
    public StoredFile store(MultipartFile file) {
        String storageKey = createStorageKey();
        Path target = resolveStorageKey(storageKey);
        boolean targetCreated = false;

        try {
            Files.createDirectories(target.getParent());

            try (
                    InputStream inputStream = file.getInputStream();
                    OutputStream outputStream = Files.newOutputStream(
                            target,
                            StandardOpenOption.CREATE_NEW,
                            StandardOpenOption.WRITE
                    )
            ) {
                // 새 파일 생성하여 true로 전환
                targetCreated = true;
                inputStream.transferTo(outputStream); // write
            }

            return new StoredFile(
                    storageKey,
                    resolveOriginalName(file),
                    resolveContentType(file),
                    file.getSize()
            );

        } catch (IOException exception) {
            log.error(
                    "Local attachment store failed, storageKey={}",
                    storageKey,
                    exception
            );

            // 새 파일이 만들어졌다면 해당 파일을 삭제
            if (targetCreated) {
                deleteQuietly(target);
            }

            throw new CustomException(
                    ErrorCode.ATTACHMENT_STORAGE_FAILED
            );
        }
    }

    @Override
    public Resource load(String storageKey) {
        Path target = resolveStorageKey(storageKey);

        // 해당 경로가 실제로 존재하는 일반 파일인지 확인
        if (!Files.isRegularFile(target)) {
            throw new CustomException(
                    ErrorCode.ATTACHMENT_NOT_FOUND
            );
        }

        return new FileSystemResource(target);
    }

    @Override
    public void delete(String storageKey) {
        Path target = resolveStorageKey(storageKey);

        try {
            Files.deleteIfExists(target);
        } catch (IOException exception) {
            log.error(
                    "Local attachment delete failed, storageKey={}",
                    storageKey,
                    exception
            );

            throw new CustomException(
                    ErrorCode.ATTACHMENT_STORAGE_FAILED
            );
        }

    }

    private String createStorageKey() {
        LocalDate currentDate = LocalDate.now(ZoneOffset.UTC);

        return "%d/%02d/%02d/%s".formatted(
                currentDate.getYear(),
                currentDate.getMonthValue(),
                currentDate.getDayOfMonth(),
                UUID.randomUUID()
        );
    }

    /**
     * storageKey를 root 아래의 절대 파일 경로로 반환
     * @param storageKey
     * @return 절대 파일 경로
     */
    private Path resolveStorageKey(String storageKey) {
        if (storageKey == null || storageKey.isBlank()) {
            throw new CustomException(
                    ErrorCode.INVALID_ATTACHMENT_STORAGE_KEY
            );
        }

        try {
            Path target = root.resolve(storageKey).normalize();

            if (!target.startsWith(root)) {
                throw new CustomException(
                        ErrorCode.INVALID_ATTACHMENT_STORAGE_KEY
                );
            }

            return target;
        } catch (InvalidPathException exception) {
            log.error(
                    "Invalid attachment storage key, storageKey={}",
                    storageKey,
                    exception
            );

            throw new CustomException(
                    ErrorCode.INVALID_ATTACHMENT_STORAGE_KEY
            );
        }

    }

    /**
     * 원본 파일이름 반환
     * @param file
     * @return 원본 파일이름
     */
    private String resolveOriginalName(MultipartFile file) {
        String originalName = file.getOriginalFilename();

        if (originalName == null || originalName.isBlank()) {
            return "file";
        }

        String normalizeName = originalName.replace('\\', '/');
        int separatorIndex = normalizeName.lastIndexOf('/');

        String fileName = separatorIndex >= 0
                ? normalizeName.substring(separatorIndex + 1)
                : normalizeName;

        return fileName.isBlank() ? "file" : fileName;
    }

    /**
     * 파일에 대한 타입을 반환
     * @param file
     * @return 파일 타입
     */
    private String resolveContentType(MultipartFile file) {
        String contentType = file.getContentType();

        return contentType == null || contentType.isBlank()
                ? MediaType.APPLICATION_OCTET_STREAM_VALUE
                : contentType;
    }

    /**
     * 파일이 존재하면 삭제
     * @param target
     */
    private void deleteQuietly(Path target) {
        try {
            Files.deleteIfExists(target);
        } catch (IOException cleanupException) {
            log.warn(
                    "Failed to clean up attachment after store failure, path={}",
                    target,
                    cleanupException
            );
        }
    }
}
