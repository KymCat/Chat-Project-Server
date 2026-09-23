package com.project.ChatProject.attachment;

import com.project.ChatProject.config.storage.StorageProperties;
import com.project.ChatProject.entity.enums.ChatMessageType;
import com.project.ChatProject.exception.CustomException;
import com.project.ChatProject.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.unit.DataSize;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AttachmentValidatorTest {

    private static final DataSize MAX_IMAGE_SIZE = DataSize.ofMegabytes(10);
    private static final DataSize MAX_FILE_SIZE = DataSize.ofMegabytes(50);

    private AttachmentValidator validator;

    @BeforeEach
    void setUp() {
        StorageProperties properties = new StorageProperties(
                Path.of("uploads"),
                MAX_IMAGE_SIZE,
                MAX_FILE_SIZE
        );
        validator = new AttachmentValidator(properties);
    }

    @ParameterizedTest
    @CsvSource({
            "jpg, image/jpeg, JPEG",
            "jpeg, image/jpeg, JPEG",
            "png, image/png, PNG",
            "gif, image/gif, GIF"
    })
    void validateClassifiesSupportedImage(
            String extension,
            String contentType,
            String imageFormat
    ) throws IOException {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "image." + extension,
                contentType,
                createImageBytes(imageFormat)
        );

        ValidatedAttachment result = validator.validate(file);

        assertThat(result.messageType()).isEqualTo(ChatMessageType.IMAGE);
        assertThat(result.contentType()).isEqualTo(contentType);
    }

    @ParameterizedTest
    @CsvSource({
            "document.pdf, application/pdf",
            "note.txt, text/plain",
            "archive.zip, application/zip",
            "archive.zip, application/x-zip-compressed",
            "document.docx, application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "sheet.xlsx, application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "slides.pptx, application/vnd.openxmlformats-officedocument.presentationml.presentation"
    })
    void validateClassifiesSupportedFile(String fileName, String contentType) {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                fileName,
                contentType,
                "content".getBytes()
        );

        ValidatedAttachment result = validator.validate(file);

        assertThat(result.messageType()).isEqualTo(ChatMessageType.FILE);
        assertThat(result.contentType()).isEqualTo(contentType);
    }

    @Test
    void validateNormalizesExtensionAndContentType() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "NOTE.TXT",
                "Text/Plain;charset=UTF-8",
                "content".getBytes()
        );

        ValidatedAttachment result = validator.validate(file);

        assertThat(result.messageType()).isEqualTo(ChatMessageType.FILE);
        assertThat(result.contentType()).isEqualTo("text/plain");
    }

    @Test
    void validateAcceptsFilenameContainingClientPath() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "C:\\fakepath\\note.txt",
                "text/plain",
                "content".getBytes()
        );

        ValidatedAttachment result = validator.validate(file);

        assertThat(result.messageType()).isEqualTo(ChatMessageType.FILE);
    }

    @Test
    void validateRejectsNullFile() {
        assertErrorCode(
                () -> validator.validate(null),
                ErrorCode.ATTACHMENT_EMPTY
        );
    }

    @Test
    void validateRejectsEmptyFile() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "empty.txt",
                "text/plain",
                new byte[0]
        );

        assertErrorCode(
                () -> validator.validate(file),
                ErrorCode.ATTACHMENT_EMPTY
        );
    }

    @ParameterizedTest
    @CsvSource(value = {
            "NULL",
            "'   '",
            "C:\\fakepath\\"
    }, nullValues = "NULL")
    void validateRejectsInvalidFilename(String originalFilename) {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                originalFilename,
                "text/plain",
                "content".getBytes()
        );

        assertErrorCode(
                () -> validator.validate(file),
                ErrorCode.ATTACHMENT_INVALID_FILENAME
        );
    }

    @Test
    void validateRejectsFilenameLongerThan255Characters() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "a".repeat(252) + ".txt",
                "text/plain",
                "content".getBytes()
        );

        assertErrorCode(
                () -> validator.validate(file),
                ErrorCode.ATTACHMENT_INVALID_FILENAME
        );
    }

    @ParameterizedTest
    @CsvSource({
            "filename, text/plain",
            ".txt, text/plain",
            "filename., text/plain",
            "script.exe, application/octet-stream"
    })
    void validateRejectsMissingOrUnsupportedExtension(
            String fileName,
            String contentType
    ) {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                fileName,
                contentType,
                "content".getBytes()
        );

        assertErrorCode(
                () -> validator.validate(file),
                ErrorCode.ATTACHMENT_TYPE_NOT_ALLOWED
        );
    }

    @ParameterizedTest
    @CsvSource(value = {
            "NULL",
            "'   '",
            "not-a-media-type"
    }, nullValues = "NULL")
    void validateRejectsInvalidContentType(String contentType) {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "note.txt",
                contentType,
                "content".getBytes()
        );

        assertErrorCode(
                () -> validator.validate(file),
                ErrorCode.ATTACHMENT_TYPE_NOT_ALLOWED
        );
    }

    @Test
    void validateRejectsContentTypeThatDoesNotMatchExtension() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "image.png",
                "image/jpeg",
                "content".getBytes()
        );

        assertErrorCode(
                () -> validator.validate(file),
                ErrorCode.ATTACHMENT_TYPE_NOT_ALLOWED
        );
    }

    @Test
    void validateRejectsOversizedImage() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "image.png",
                "image/png",
                new byte[(int) MAX_IMAGE_SIZE.toBytes() + 1]
        );

        assertErrorCode(
                () -> validator.validate(file),
                ErrorCode.ATTACHMENT_TOO_LARGE
        );
    }

    @Test
    void validateRejectsOversizedFile() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "document.pdf",
                "application/pdf",
                new byte[(int) MAX_FILE_SIZE.toBytes() + 1]
        );

        assertErrorCode(
                () -> validator.validate(file),
                ErrorCode.ATTACHMENT_TOO_LARGE
        );
    }

    @Test
    void validateRejectsNonImageContentWithImageMetadata() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "image.png",
                "image/png",
                "not-an-image".getBytes()
        );

        assertErrorCode(
                () -> validator.validate(file),
                ErrorCode.ATTACHMENT_INVALID_IMAGE
        );
    }

    @Test
    void validateRejectsImageWhoseActualFormatDoesNotMatchExtension() throws IOException {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "image.jpg",
                "image/jpeg",
                createImageBytes("PNG")
        );

        assertErrorCode(
                () -> validator.validate(file),
                ErrorCode.ATTACHMENT_INVALID_IMAGE
        );
    }

    private byte[] createImageBytes(String format) throws IOException {
        BufferedImage image = new BufferedImage(
                2,
                2,
                BufferedImage.TYPE_INT_RGB
        );

        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            boolean written = ImageIO.write(image, format, outputStream);
            assertThat(written).isTrue();
            return outputStream.toByteArray();
        }
    }

    private void assertErrorCode(
            ThrowingCallable callable,
            ErrorCode expectedErrorCode
    ) {
        assertThatThrownBy(callable::call)
                .isInstanceOfSatisfying(
                        CustomException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(expectedErrorCode)
                );
    }

    @FunctionalInterface
    private interface ThrowingCallable {
        void call() throws Exception;
    }
}
