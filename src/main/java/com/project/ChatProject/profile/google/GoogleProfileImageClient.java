package com.project.ChatProject.profile.google;

import com.project.ChatProject.config.profile.ProfileImageProperties;
import com.project.ChatProject.exception.CustomException;
import com.project.ChatProject.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.Locale;

@Slf4j
@Component
public class GoogleProfileImageClient {

    private static final String GOOGLE_IMAGE_ROOT_DOMAIN =
            "googleusercontent.com";

    private final RestClient restClient;
    private final int maxDownloadBytes;

    public GoogleProfileImageClient(
            @Qualifier("googleProfileImageRestClient")
            RestClient restClient,
            ProfileImageProperties profileImageProperties
    ) {
        this.restClient = restClient;

        long configuredMaxSize =
                profileImageProperties.maxSize().toBytes();

        if (configuredMaxSize >= Integer.MAX_VALUE) {
            throw new IllegalArgumentException(
                    "프로필 이미지 최대 크기는 Integer 범위보다 작아야 합니다."
            );
        }

        this.maxDownloadBytes =
                Math.toIntExact(configuredMaxSize);
    }

    public DownloadedProfileImage download(
            String profileImageUrl
    ) {
        URI uri = validateAndCreateUri(
                profileImageUrl
        );

        try {
            return restClient
                    .get()
                    .uri(uri)
                    .accept(
                            MediaType.IMAGE_JPEG,
                            MediaType.IMAGE_PNG
                    )
                    .exchange(
                            (request, response) ->
                                    readResponse(response)
                    );
        } catch (CustomException exception) {
            throw exception;
        } catch (RestClientException exception) {
            log.warn(
                    "Google profile image request failed, host={}",
                    uri.getHost(),
                    exception
            );

            throw downloadFailed();
        }
    }

    private DownloadedProfileImage readResponse(
            ClientHttpResponse response
    ) throws IOException {
        if (!response.getStatusCode().is2xxSuccessful()) {
            log.warn(
                    "Google profile image returned failure status={}",
                    response.getStatusCode().value()
            );

            throw downloadFailed();
        }

        long contentLength =
                response.getHeaders().getContentLength();

        if (contentLength > maxDownloadBytes) {
            throw new CustomException(
                    ErrorCode.PROFILE_IMAGE_TOO_LARGE
            );
        }

        try (InputStream inputStream = response.getBody()) {
            /*
             * 최대 허용 크기보다 1byte 더 읽음
             *
             * Content-Length가 없거나 잘못된 경우에도 실제 응답 크기가
             * 제한을 초과했는지 판단하기 위한 처리
             */
            byte[] content = inputStream.readNBytes(
                    maxDownloadBytes + 1
            );

            if (content.length > maxDownloadBytes) {
                throw new CustomException(
                        ErrorCode.PROFILE_IMAGE_TOO_LARGE
                );
            }

            if (content.length == 0) {
                throw new CustomException(
                        ErrorCode.PROFILE_IMAGE_INVALID
                );
            }

            return new DownloadedProfileImage(
                    content
            );
        }
    }

    private URI validateAndCreateUri(
            String profileImageUrl
    ) {
        if (profileImageUrl == null
                || profileImageUrl.isBlank()) {
            throw downloadFailed();
        }

        try {
            URI uri = URI.create(profileImageUrl);

            if (!uri.isAbsolute()
                    || !"https".equalsIgnoreCase(uri.getScheme())
                    || uri.getHost() == null
                    || uri.getRawUserInfo() != null
                    || (uri.getPort() != -1 && uri.getPort() != 443)) {
                throw downloadFailed();
            }

            String host = uri.getHost()
                    .toLowerCase(Locale.ROOT);

            boolean allowedHost =
                    GOOGLE_IMAGE_ROOT_DOMAIN.equals(host)
                            || host.endsWith(
                            "." + GOOGLE_IMAGE_ROOT_DOMAIN
                    );

            if (!allowedHost) {
                throw downloadFailed();
            }

            return uri;
        } catch (IllegalArgumentException exception) {
            throw downloadFailed();
        }
    }

    private CustomException downloadFailed() {
        return new CustomException(
                ErrorCode.SOCIAL_PROFILE_IMAGE_DOWNLOAD_FAILED
        );
    }
}