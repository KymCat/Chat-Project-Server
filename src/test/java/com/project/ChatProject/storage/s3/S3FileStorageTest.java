package com.project.ChatProject.storage.s3;

import com.project.ChatProject.config.storage.S3Properties;
import com.project.ChatProject.exception.CustomException;
import com.project.ChatProject.exception.ErrorCode;
import com.project.ChatProject.storage.StorageCategory;
import com.project.ChatProject.storage.StoredFile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.Resource;
import org.springframework.mock.web.MockMultipartFile;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.AbortableInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class S3FileStorageTest {

    @Mock
    private S3Client s3Client;

    private S3FileStorage fileStorage;

    @BeforeEach
    void setUp() {
        S3Properties properties = new S3Properties(
                "test-bucket",
                "ap-northeast-2"
        );

        fileStorage = new S3FileStorage(
                s3Client,
                properties
        );
    }

    @Test
    void storeUploadsFileAndReturnsMetadata() {
        byte[] content = "attachment-content".getBytes();
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "photo.png",
                "image/png",
                content
        );

        when(s3Client.putObject(
                any(PutObjectRequest.class),
                any(RequestBody.class)
        )).thenReturn(PutObjectResponse.builder().build());

        StoredFile storedFile = fileStorage.store(
                file,
                StorageCategory.CHAT_ATTACHMENT
        );

        // S3Client에 전달된 실제 요청을 꺼내 bucket, key, MIME type을 검증한다.
        ArgumentCaptor<PutObjectRequest> requestCaptor =
                ArgumentCaptor.forClass(PutObjectRequest.class);

        verify(s3Client).putObject(
                requestCaptor.capture(),
                any(RequestBody.class)
        );

        PutObjectRequest request = requestCaptor.getValue();

        assertThat(request.bucket()).isEqualTo("test-bucket");
        assertThat(request.key()).isEqualTo(storedFile.storageKey());
        assertThat(request.contentType()).isEqualTo("image/png");

        assertThat(storedFile.storageKey())
                .matches("attachments/\\d{4}/\\d{2}/\\d{2}/[0-9a-f-]{36}");
        assertThat(storedFile.originalName()).isEqualTo("photo.png");
        assertThat(storedFile.contentType()).isEqualTo("image/png");
        assertThat(storedFile.sizeBytes()).isEqualTo(content.length);
    }

    @Test
    void loadReturnsS3ResponseAsResource() throws Exception {
        byte[] content = "download-content".getBytes();

        /*
         * 실제 S3 getObject가 반환하는 것과 같은 ResponseInputStream을 만든다.
         * 네트워크 없이도 streaming download 흐름을 검증할 수 있다.
         */
        ResponseInputStream<GetObjectResponse> responseInputStream =
                new ResponseInputStream<>(
                        GetObjectResponse.builder().build(),
                        AbortableInputStream.create(
                                new ByteArrayInputStream(content)
                        )
                );

        when(s3Client.getObject(any(GetObjectRequest.class)))
                .thenReturn(responseInputStream);

        Resource resource =
                fileStorage.load("2026/10/22/file-key");

        try (InputStream inputStream = resource.getInputStream()) {
            assertThat(inputStream.readAllBytes())
                    .isEqualTo(content);
        }

        verify(s3Client).getObject(
                any(GetObjectRequest.class)
        );
    }

    @Test
    void loadConvertsS3NotFoundToStorageObjectNotFound() {
        S3Exception notFound = (S3Exception) S3Exception.builder()
                .statusCode(404)
                .message("Not Found")
                .build();

        when(s3Client.getObject(any(GetObjectRequest.class)))
                .thenThrow(notFound);

        assertThatThrownBy(() ->
                fileStorage.load("missing-key")
        ).isInstanceOfSatisfying(
                CustomException.class,
                exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.STORAGE_OBJECT_NOT_FOUND)
        );
    }

    @Test
    void deleteRequestsObjectDeletion() {
        fileStorage.delete("2026/10/22/file-key");

        ArgumentCaptor<DeleteObjectRequest> requestCaptor =
                ArgumentCaptor.forClass(DeleteObjectRequest.class);

        verify(s3Client).deleteObject(requestCaptor.capture());

        DeleteObjectRequest request = requestCaptor.getValue();

        assertThat(request.bucket()).isEqualTo("test-bucket");
        assertThat(request.key())
                .isEqualTo("2026/10/22/file-key");
    }

    @Test
    void blankStorageKeyIsRejected() {
        assertThatThrownBy(() -> fileStorage.load(" "))
                .isInstanceOfSatisfying(
                        CustomException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(
                                        ErrorCode.INVALID_STORAGE_KEY
                                )
                );
    }
}
