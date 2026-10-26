package com.project.ChatProject.oauth;

import com.project.ChatProject.entity.enums.SocialProvider;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Locale;

@Component
public class GoogleOidcProfileMapper {

    public SocialUserProfile map(OidcUser oidcUser) {
        return new SocialUserProfile(
                SocialProvider.GOOGLE,
                oidcUser.getSubject(),
                oidcUser.getEmail(),
                Boolean.TRUE.equals(
                        oidcUser.getEmailVerified()
                ),
                oidcUser.getFullName(),
                resolveProfileImageUrl(
                        oidcUser.getPicture()
                )
        );
    }

    /**
     * Google에서 받은 프로필 이미지 URL이 redirect에 사용할 수 있는
     * 안전한 HTTPS 절대 URL인지 확인합
     *
     * 프로필 사진 URL이 잘못됐더라도 로그인 자체는 실패시키지 않고
     * 프로필 사진만 없는 상태로 처리합
     */
    private String resolveProfileImageUrl(String profileImageUrl) {
        if (profileImageUrl == null || profileImageUrl.isBlank()) {
            return null;
        }

        try {
            URI uri = URI.create(profileImageUrl);

            // HTTPS 절대 URL이며 host가 존재하는지 먼저 확인합니다.
            if (!uri.isAbsolute()
                    || !"https".equalsIgnoreCase(uri.getScheme())
                    || uri.getHost() == null) {
                return null;
            }

            // host가 null이 아님을 확인한 뒤 소문자로 변환합니다.
            String host = uri.getHost()
                    .toLowerCase(Locale.ROOT);

            // googleusercontent.com과 그 하위 domain만 허용합니다.
            boolean googleImageHost =
                    host.equals("googleusercontent.com")
                            || host.endsWith(".googleusercontent.com");

            if (!googleImageHost) {
                return null;
            }

            return uri.toASCIIString();
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
