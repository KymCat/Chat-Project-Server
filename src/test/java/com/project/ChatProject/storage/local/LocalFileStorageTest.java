package com.project.ChatProject.storage.local;

import com.project.ChatProject.config.storage.StorageProperties;
import com.project.ChatProject.exception.CustomException;
import com.project.ChatProject.exception.ErrorCode;
import com.project.ChatProject.storage.StoredFile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.Resource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LocalFileStorageTest {

    @TempDir
    Path tempDirectory;

    private Path root;
    private LocalFileStorage fileStorage;

    @BeforeEach
    void setUp() {
        root = tempDirectory.resolve("uploads");
        StorageProperties properties = new StorageProperties(
                root,
                DataSize.ofMegabytes(10),
                DataSize.ofMegabytes(50)
        );

        fileStorage = new LocalFileStorage(properties);
        fileStorage.init();
    }

    @Test
    void initializeCreatesConfiguredRootDirectory() {
        assertThat(root).isDirectory();
    }

    @Test
    void storeWritesFileAndReturnsStoredMetadata() throws IOException {
        byte[] content = "attachment-content".getBytes();
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "C:\\fakepath\\photo.png",
                "image/png",
                content
        );

        StoredFile storedFile = fileStorage.store(file);

        assertThat(storedFile.storageKey())
                .matches("\\d{4}/\\d{2}/\\d{2}/[0-9a-f-]{36}");
        assertThat(storedFile.originalName()).isEqualTo("photo.png");
        assertThat(storedFile.contentType()).isEqualTo("image/png");
        assertThat(storedFile.sizeBytes()).isEqualTo(content.length);
        assertThat(root.resolve(storedFile.storageKey()))
                .hasBinaryContent(content);
    }

    @Test
    void storeUsesFallbackMetadataWhenFilenameAndContentTypeAreMissing() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                null,
                null,
                "content".getBytes()
        );

        StoredFile storedFile = fileStorage.store(file);

        assertThat(storedFile.originalName()).isEqualTo("file");
        assertThat(storedFile.contentType())
                .isEqualTo("application/octet-stream");
    }

    @Test
    void storeCreatesDifferentStorageKeysForEachFile() {
        MockMultipartFile firstFile = new MockMultipartFile(
                "file",
                "first.txt",
                "text/plain",
                "first".getBytes()
        );
        MockMultipartFile secondFile = new MockMultipartFile(
                "file",
                "second.txt",
                "text/plain",
                "second".getBytes()
        );

        StoredFile firstStoredFile = fileStorage.store(firstFile);
        StoredFile secondStoredFile = fileStorage.store(secondFile);

        assertThat(firstStoredFile.storageKey())
                .isNotEqualTo(secondStoredFile.storageKey());
    }

    @Test
    void loadReturnsStoredFileResource() throws IOException {
        byte[] content = "download-content".getBytes();
        StoredFile storedFile = fileStorage.store(
                new MockMultipartFile(
                        "file",
                        "document.txt",
                        "text/plain",
                        content
                )
        );

        Resource resource = fileStorage.load(storedFile.storageKey());

        assertThat(resource.exists()).isTrue();
        try (InputStream inputStream = resource.getInputStream()) {
            assertThat(inputStream.readAllBytes()).isEqualTo(content);
        }
    }

    @Test
    void loadRejectsMissingFile() {
        assertThatThrownBy(() ->
                fileStorage.load("2026/09/23/missing-file")
        ).isInstanceOfSatisfying(
                CustomException.class,
                exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.ATTACHMENT_NOT_FOUND)
        );
    }

    @Test
    void loadRejectsStorageKeyOutsideRoot() {
        assertThatThrownBy(() ->
                fileStorage.load("../../outside-file")
        ).isInstanceOfSatisfying(
                CustomException.class,
                exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.INVALID_ATTACHMENT_STORAGE_KEY)
        );
    }

    @Test
    void deleteRemovesStoredFileAndIsIdempotent() {
        StoredFile storedFile = fileStorage.store(
                new MockMultipartFile(
                        "file",
                        "document.txt",
                        "text/plain",
                        "content".getBytes()
                )
        );
        Path storedPath = root.resolve(storedFile.storageKey());

        fileStorage.delete(storedFile.storageKey());
        fileStorage.delete(storedFile.storageKey());

        assertThat(storedPath).doesNotExist();
    }

    @Test
    void storeRemovesPartiallyWrittenFileWhenCopyFails() throws IOException {
        MultipartFile file = mock(MultipartFile.class);
        when(file.getInputStream())
                .thenReturn(new FailingInputStream("partial-content".getBytes(), 4));

        assertThatThrownBy(() -> fileStorage.store(file))
                .isInstanceOfSatisfying(
                        CustomException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.ATTACHMENT_STORAGE_FAILED)
                );

        try (Stream<Path> paths = Files.walk(root)) {
            assertThat(paths.filter(Files::isRegularFile)).isEmpty();
        }
    }

    private static final class FailingInputStream extends InputStream {

        private final byte[] content;
        private final int failureIndex;
        private int index;

        private FailingInputStream(byte[] content, int failureIndex) {
            this.content = content;
            this.failureIndex = failureIndex;
        }

        @Override
        public int read() throws IOException {
            if (index >= failureIndex) {
                throw new IOException("테스트를 위한 읽기 실패");
            }

            if (index >= content.length) {
                return -1;
            }

            return content[index++] & 0xff;
        }
    }
}
