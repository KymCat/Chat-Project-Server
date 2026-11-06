package com.project.ChatProject.profile.google;

import com.project.ChatProject.config.profile.ProfileImageProperties;
import com.project.ChatProject.exception.CustomException;
import com.project.ChatProject.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.util.unit.DataSize;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class GoogleProfileImageClientTest {

    private static final String GOOGLE_PROFILE_IMAGE_URL =
            "https://lh3.googleusercontent.com/a/profile-image";
    private static final int MAX_DOWNLOAD_BYTES = 5;

    private MockRestServiceServer mockServer;
    private GoogleProfileImageClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        mockServer = MockRestServiceServer
                .bindTo(builder)
                .build();

        ProfileImageProperties properties =
                new ProfileImageProperties(
                        DataSize.ofBytes(MAX_DOWNLOAD_BYTES),
                        4096,
                        4096,
                        16_777_216L
                );

        client = new GoogleProfileImageClient(
                builder.build(),
                properties
        );
    }

    @Test
    void downloadReturnsGoogleProfileImageContent() {
        byte[] content = {1, 2, 3};

        mockServer.expect(
                        once(),
                        requestTo(GOOGLE_PROFILE_IMAGE_URL)
                )
                .andExpect(method(HttpMethod.GET))
                .andRespond(
                        withSuccess(
                                content,
                                MediaType.IMAGE_JPEG
                        )
                );

        DownloadedProfileImage result =
                client.download(GOOGLE_PROFILE_IMAGE_URL);

        assertThat(result.content()).containsExactly(content);
        assertThat(result.sizeBytes()).isEqualTo(content.length);
        mockServer.verify();
    }

    @Test
    void downloadRejectsNonGoogleDomain() {
        assertDownloadFailure(
                "https://example.com/profile-image",
                ErrorCode.SOCIAL_PROFILE_IMAGE_DOWNLOAD_FAILED
        );
    }

    @Test
    void downloadRejectsHttpUrl() {
        assertDownloadFailure(
                "http://lh3.googleusercontent.com/a/profile-image",
                ErrorCode.SOCIAL_PROFILE_IMAGE_DOWNLOAD_FAILED
        );
    }

    @Test
    void downloadConvertsUpstream429ToDownloadFailure() {
        mockServer.expect(
                        once(),
                        requestTo(GOOGLE_PROFILE_IMAGE_URL)
                )
                .andRespond(
                        withStatus(HttpStatus.TOO_MANY_REQUESTS)
                );

        assertDownloadFailure(
                GOOGLE_PROFILE_IMAGE_URL,
                ErrorCode.SOCIAL_PROFILE_IMAGE_DOWNLOAD_FAILED
        );
        mockServer.verify();
    }

    @Test
    void downloadRejectsDeclaredContentLengthOverLimit() {
        mockServer.expect(
                        once(),
                        requestTo(GOOGLE_PROFILE_IMAGE_URL)
                )
                .andRespond(
                        withSuccess(
                                new byte[]{1},
                                MediaType.IMAGE_JPEG
                        ).header(
                                HttpHeaders.CONTENT_LENGTH,
                                String.valueOf(MAX_DOWNLOAD_BYTES + 1)
                        )
                );

        assertDownloadFailure(
                GOOGLE_PROFILE_IMAGE_URL,
                ErrorCode.PROFILE_IMAGE_TOO_LARGE
        );
        mockServer.verify();
    }

    @Test
    void downloadRejectsActualContentOverLimitWithoutContentLength() {
        byte[] content = {1, 2, 3, 4, 5, 6};

        mockServer.expect(
                        once(),
                        requestTo(GOOGLE_PROFILE_IMAGE_URL)
                )
                .andRespond(
                        withSuccess(
                                content,
                                MediaType.IMAGE_JPEG
                        )
                );

        assertDownloadFailure(
                GOOGLE_PROFILE_IMAGE_URL,
                ErrorCode.PROFILE_IMAGE_TOO_LARGE
        );
        mockServer.verify();
    }

    @Test
    void downloadRejectsEmptyResponseBody() {
        mockServer.expect(
                        once(),
                        requestTo(GOOGLE_PROFILE_IMAGE_URL)
                )
                .andRespond(
                        withSuccess(
                                new byte[0],
                                MediaType.IMAGE_JPEG
                        )
                );

        assertDownloadFailure(
                GOOGLE_PROFILE_IMAGE_URL,
                ErrorCode.PROFILE_IMAGE_INVALID
        );
        mockServer.verify();
    }

    private void assertDownloadFailure(
            String profileImageUrl,
            ErrorCode expectedErrorCode
    ) {
        assertThatThrownBy(() ->
                client.download(profileImageUrl)
        ).isInstanceOfSatisfying(
                CustomException.class,
                exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(expectedErrorCode)
        );
    }
}
