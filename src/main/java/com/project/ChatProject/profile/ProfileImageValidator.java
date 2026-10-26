package com.project.ChatProject.profile;

import com.project.ChatProject.config.profile.ProfileImageProperties;
import com.project.ChatProject.exception.CustomException;
import com.project.ChatProject.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.apache.tika.Tika;
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

@Component
@RequiredArgsConstructor
public class ProfileImageValidator {

    private static final Map<String, String> ALLOWED_IMAGE_TYPES =
            Map.of(
                    MediaType.IMAGE_JPEG_VALUE, "JPEG",
                    MediaType.IMAGE_PNG_VALUE, "PNG"
            );

    private final ProfileImageProperties properties;

    /*
     * Tika는 요청의 Content-Type이나 확장자가 아니라
     * 실제 파일 byte를 기반으로 MIME type을 탐지
     */
    private final Tika tika = new Tika();

    public ValidatedProfileImage validate(MultipartFile file) {
        validateNotEmpty(file);
        validateFileSize(file);

        String detectedContentType = detectContentType(file);
        String expectedFormat =
                ALLOWED_IMAGE_TYPES.get(detectedContentType);

        if (expectedFormat == null) {
            throw new CustomException(
                    ErrorCode.PROFILE_IMAGE_TYPE_NOT_ALLOWED
            );
        }

        validateImageContent(file, expectedFormat);

        return new ValidatedProfileImage(
                detectedContentType
        );
    }

    private void validateNotEmpty(MultipartFile file) {
        if (file == null || file.isEmpty() || file.getSize() <= 0) {
            throw new CustomException(
                    ErrorCode.PROFILE_IMAGE_EMPTY
            );
        }
    }

    private void validateFileSize(MultipartFile file) {
        if (file.getSize() > properties.maxSize().toBytes()) {
            throw new CustomException(
                    ErrorCode.PROFILE_IMAGE_TOO_LARGE
            );
        }
    }

    private String detectContentType(MultipartFile file) {
        try (InputStream inputStream = file.getInputStream()) {
            return tika.detect(inputStream)
                    .toLowerCase(Locale.ROOT);
        } catch (IOException exception) {
            throw new CustomException(
                    ErrorCode.PROFILE_IMAGE_INVALID
            );
        }
    }

    /**
     * ImageIO로 실제 이미지 구조와 크기를 검사한다.
     *
     * 파일 앞부분만 JPEG나 PNG처럼 위조한 경우에도
     * ImageReader가 정상적인 이미지로 해석하지 못하면 거부한다.
     */
    private void validateImageContent(
            MultipartFile file,
            String expectedFormat
    ) {
        try (
                InputStream inputStream = file.getInputStream();
                ImageInputStream imageInputStream =
                        ImageIO.createImageInputStream(inputStream)
        ) {
            if (imageInputStream == null) {
                throw invalidImage();
            }

            Iterator<ImageReader> readers =
                    ImageIO.getImageReaders(imageInputStream);

            if (!readers.hasNext()) {
                throw invalidImage();
            }

            ImageReader reader = readers.next();

            try {
                reader.setInput(imageInputStream, true, true);

                String actualFormat = reader.getFormatName()
                        .toUpperCase(Locale.ROOT);

                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                long totalPixels = (long) width * height;

                if (!expectedFormat.equals(actualFormat)
                        || width <= 0
                        || height <= 0) {
                    throw invalidImage();
                }

                if (width > properties.maxWidth()
                        || height > properties.maxHeight()
                        || totalPixels > properties.maxPixels()) {
                    throw new CustomException(
                            ErrorCode.PROFILE_IMAGE_TOO_LARGE
                    );
                }
            } finally {
                reader.dispose();
            }
        } catch (IOException exception) {
            throw invalidImage();
        }
    }

    private CustomException invalidImage() {
        return new CustomException(
                ErrorCode.PROFILE_IMAGE_INVALID
        );
    }
}