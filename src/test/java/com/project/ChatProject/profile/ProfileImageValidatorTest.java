package com.project.ChatProject.profile;

import com.project.ChatProject.config.profile.ProfileImageProperties;
import com.project.ChatProject.exception.CustomException;
import com.project.ChatProject.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.unit.DataSize;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProfileImageValidatorTest {

    private ProfileImageValidator validator;

    @BeforeEach
    void setUp() {
        ProfileImageProperties properties =
                new ProfileImageProperties(
                        DataSize.ofMegabytes(1),
                        10,
                        10,
                        100
                );

        validator = new ProfileImageValidator(
                properties
        );
    }

    @Test
    void validateAcceptsDownloadedPngBytes()
            throws IOException {
        byte[] content = createPng(2, 2);

        ValidatedProfileImage result =
                validator.validate(content);

        assertThat(result.contentType())
                .isEqualTo(MediaType.IMAGE_PNG_VALUE);
    }

    @Test
    void validatePreservesMultipartFileValidation()
            throws IOException {
        byte[] content = createPng(2, 2);

        MockMultipartFile file =
                new MockMultipartFile(
                        "file",
                        "profile.png",
                        MediaType.IMAGE_PNG_VALUE,
                        content
                );

        ValidatedProfileImage result =
                validator.validate(file);

        assertThat(result.contentType())
                .isEqualTo(MediaType.IMAGE_PNG_VALUE);
    }

    @Test
    void validateRejectsEmptyDownloadedContent() {
        assertValidationFailure(
                new byte[0],
                ErrorCode.PROFILE_IMAGE_EMPTY
        );
    }

    @Test
    void validateRejectsDownloadedContentOverSizeLimit() {
        ProfileImageProperties properties =
                new ProfileImageProperties(
                        DataSize.ofBytes(3),
                        10,
                        10,
                        100
                );

        ProfileImageValidator limitedValidator =
                new ProfileImageValidator(properties);

        assertThatThrownBy(() ->
                limitedValidator.validate(
                        new byte[]{1, 2, 3, 4}
                )
        ).isInstanceOfSatisfying(
                CustomException.class,
                exception -> assertThat(
                        exception.getErrorCode()
                ).isEqualTo(
                        ErrorCode.PROFILE_IMAGE_TOO_LARGE
                )
        );
    }

    @Test
    void validateRejectsNonImageDownloadedContent() {
        assertValidationFailure(
                "not-an-image".getBytes(),
                ErrorCode.PROFILE_IMAGE_TYPE_NOT_ALLOWED
        );
    }

    @Test
    void validateRejectsImageDimensionsOverLimit()
            throws IOException {
        byte[] content = createPng(11, 1);

        assertValidationFailure(
                content,
                ErrorCode.PROFILE_IMAGE_TOO_LARGE
        );
    }

    private void assertValidationFailure(
            byte[] content,
            ErrorCode expectedErrorCode
    ) {
        assertThatThrownBy(() ->
                validator.validate(content)
        ).isInstanceOfSatisfying(
                CustomException.class,
                exception -> assertThat(
                        exception.getErrorCode()
                ).isEqualTo(expectedErrorCode)
        );
    }

    private byte[] createPng(
            int width,
            int height
    ) throws IOException {
        BufferedImage image =
                new BufferedImage(
                        width,
                        height,
                        BufferedImage.TYPE_INT_RGB
                );

        try (ByteArrayOutputStream outputStream =
                     new ByteArrayOutputStream()) {
            ImageIO.write(
                    image,
                    "png",
                    outputStream
            );

            return outputStream.toByteArray();
        }
    }
}