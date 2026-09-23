package com.project.ChatProject.attachment;

import com.project.ChatProject.config.storage.StorageProperties;
import com.project.ChatProject.entity.enums.ChatMessageType;
import com.project.ChatProject.exception.CustomException;
import com.project.ChatProject.exception.ErrorCode;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Component
public class AttachmentValidator {

    private static final int MAX_ORIGINAL_NAME_LENGTH = 255;

    private static final Map<String, String> IMAGE_CONTENT_TYPES = Map.of(
            "jpg", MediaType.IMAGE_JPEG_VALUE,
            "jpeg", MediaType.IMAGE_JPEG_VALUE,
            "png", MediaType.IMAGE_PNG_VALUE,
            "gif", MediaType.IMAGE_GIF_VALUE
    );

    private static final Map<String, Set<String>> FILE_CONTENT_TYPES = Map.of(
            "pdf", Set.of(MediaType.APPLICATION_PDF_VALUE),
            "txt", Set.of(MediaType.TEXT_PLAIN_VALUE),
            "zip", Set.of(
                    "application/zip",
                    "application/x-zip-compressed"
            ),
            "docx", Set.of(
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            ),
            "xlsx", Set.of(
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            ),
            "pptx", Set.of(
                    "application/vnd.openxmlformats-officedocument.presentationml.presentation"
            )
    );

    private final StorageProperties properties;

    public AttachmentValidator(StorageProperties properties) {
        this.properties = properties;
    }

    public ValidatedAttachment validate(MultipartFile file) {
        validateNotEmpty(file);

        String fileName = resolveFileName(file);
        String extension = resolveExtension(fileName);
        String contentType = normalizeContentType(file.getContentType());

        String expectedImageContentType =
                IMAGE_CONTENT_TYPES.get(extension);

        if (expectedImageContentType != null) {
            validateImage(
                    file,
                    extension,
                    contentType,
                    expectedImageContentType
            );

            return new ValidatedAttachment(
                    ChatMessageType.IMAGE,
                    expectedImageContentType
            );
        }

        Set<String> allowedFileContentTypes =
                FILE_CONTENT_TYPES.get(extension);

        if (allowedFileContentTypes != null) {
            validateFile(
                    file,
                    contentType,
                    allowedFileContentTypes
            );

            return new ValidatedAttachment(
                    ChatMessageType.FILE,
                    contentType
            );
        }

        throw new CustomException(
                ErrorCode.ATTACHMENT_TYPE_NOT_ALLOWED
        );
    }

    /**
     * 파일을 인수로 받아 null 과 빈파일인지 검사
     * @param file
     */
    private void validateNotEmpty(MultipartFile file) {
        if (file == null || file.isEmpty() || file.getSize() <= 0) {
            throw new CustomException(
                    ErrorCode.ATTACHMENT_EMPTY
            );
        }
    }

    /**
     * 경로명에서 파일 원본 이름을 반환
     * @param file
     * @return 파일 원본 이름
     */
    private String resolveFileName(MultipartFile file) {
        String originalName = file.getOriginalFilename();

        if (originalName == null || originalName.isBlank()) {
            throw new CustomException(
                    ErrorCode.ATTACHMENT_INVALID_FILENAME
            );
        }

        String normalizedName = originalName.replace('\\', '/');
        int separatorIndex = normalizedName.lastIndexOf('/');

        String fileName = separatorIndex >= 0
                ? normalizedName.substring(separatorIndex + 1)
                : normalizedName;

        if (fileName.isBlank()
                || fileName.length() > MAX_ORIGINAL_NAME_LENGTH) {
            throw new CustomException(
                    ErrorCode.ATTACHMENT_INVALID_FILENAME
            );
        }

        return fileName;
    }

    /**
     * 파일 이름에서 확장자를 반환
     * @param fileName
     * @return 파일 확장자
     */
    private String resolveExtension(String fileName) {
        int extensionIndex = fileName.lastIndexOf('.');

        if (extensionIndex <= 0
                || extensionIndex == fileName.length() - 1) {
            throw new CustomException(
                    ErrorCode.ATTACHMENT_TYPE_NOT_ALLOWED
            );
        }

        return fileName.substring(extensionIndex + 1)
                .toLowerCase(Locale.ROOT);
    }

    /**
     * contentType 정규화
     * @param contentType
     * @return MIME TYPE type/subtype 반환 ex) image/png
     */
    private String normalizeContentType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            throw new CustomException(
                    ErrorCode.ATTACHMENT_TYPE_NOT_ALLOWED
            );
        }

        try {
            MediaType mediaType =
                    MediaType.parseMediaType(contentType);

            return (mediaType.getType() + "/" + mediaType.getSubtype())
                    .toLowerCase(Locale.ROOT);
        } catch (InvalidMediaTypeException exception) {
            throw new CustomException(
                    ErrorCode.ATTACHMENT_TYPE_NOT_ALLOWED
            );
        }
    }

    /**
     * 이미지 파일의 contentType과 크기 검증
     * @param file
     * @param extension
     * @param contentType
     * @param expectedContentType
     */
    private void validateImage(
            MultipartFile file,
            String extension,
            String contentType,
            String expectedContentType
    ) {
        if (!expectedContentType.equals(contentType)) {
            throw new CustomException(
                    ErrorCode.ATTACHMENT_TYPE_NOT_ALLOWED
            );
        }

        if (file.getSize()
                > properties.maxImageSize().toBytes()) {
            throw new CustomException(
                    ErrorCode.ATTACHMENT_TOO_LARGE
            );
        }

        validateImageContent(file, extension);
    }


    /**
     * 업로드된 이미지의 확장자와 실제 파일 내용이 일치하는지 검증
     * @param file
     * @param extension
     */
    private void validateImageContent(
            MultipartFile file,
            String extension
    )
    {
        String expectedFormat = switch (extension) {
            case "jpg", "jpeg" -> "JPEG";
            case "png" -> "PNG";
            case "gif" -> "GIF";
            default -> throw new CustomException(
                    ErrorCode.ATTACHMENT_TYPE_NOT_ALLOWED
            );
        };

        try (
                InputStream inputStream = file.getInputStream();
                ImageInputStream imageInputStream =
                        ImageIO.createImageInputStream(inputStream)
        ) {
            if (imageInputStream == null) {
                throw new CustomException(
                        ErrorCode.ATTACHMENT_INVALID_IMAGE
                );
            }

            Iterator<ImageReader> readers =
                    ImageIO.getImageReaders(imageInputStream);

            if (!readers.hasNext()) {
                throw new CustomException(
                        ErrorCode.ATTACHMENT_INVALID_IMAGE
                );
            }

            ImageReader reader = readers.next();

            try {
                reader.setInput(
                        imageInputStream,
                        true,
                        true
                );

                String actualFormat = reader.getFormatName()
                        .toUpperCase(Locale.ROOT);

                int width = reader.getWidth(0);
                int height = reader.getHeight(0);

                if (!expectedFormat.equals(actualFormat)
                        || width <= 0
                        || height <= 0) {
                    throw new CustomException(
                            ErrorCode.ATTACHMENT_INVALID_IMAGE
                    );
                }
            } finally {
                reader.dispose();
            }
        } catch (IOException exception) {
            throw new CustomException(
                    ErrorCode.ATTACHMENT_INVALID_IMAGE
            );
        }
    }

    /**
     * 해당 파일의 contentType과 크기가 서버에서 허용하는지 검증
     * @param file
     * @param contentType
     * @param allowedContentTypes
     */
    private void validateFile(
            MultipartFile file,
            String contentType,
            Set<String> allowedContentTypes
    ) {
        if (!allowedContentTypes.contains(contentType)) {
            throw new CustomException(
                    ErrorCode.ATTACHMENT_TYPE_NOT_ALLOWED
            );
        }

        if (file.getSize()
                > properties.maxFileSize().toBytes()) {
            throw new CustomException(
                    ErrorCode.ATTACHMENT_TOO_LARGE
            );
        }
    }
}
