package com.project.ChatProject.oauth;

import com.project.ChatProject.entity.enums.SocialProvider;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GoogleOidcProfileMapperTest {

    private final GoogleOidcProfileMapper mapper =
            new GoogleOidcProfileMapper();

    @Test
    void mapsGoogleOidcClaimsToSocialUserProfile() {
        OidcUser oidcUser = mock(OidcUser.class);
        when(oidcUser.getSubject()).thenReturn("google-user-id");
        when(oidcUser.getEmail()).thenReturn("user@example.com");
        when(oidcUser.getEmailVerified()).thenReturn(true);
        when(oidcUser.getFullName()).thenReturn("Google User");
        when(oidcUser.getPicture())
                .thenReturn("https://example.com/profile.png");

        SocialUserProfile profile = mapper.map(oidcUser);

        assertThat(profile.provider()).isEqualTo(SocialProvider.GOOGLE);
        assertThat(profile.providerUserId()).isEqualTo("google-user-id");
        assertThat(profile.email()).isEqualTo("user@example.com");
        assertThat(profile.emailVerified()).isTrue();
        assertThat(profile.displayName()).isEqualTo("Google User");
        assertThat(profile.profileImageUrl())
                .isEqualTo("https://example.com/profile.png");
    }

    @Test
    void mapsNullableOptionalClaimsAndEmailVerificationToSafeValues() {
        OidcUser oidcUser = mock(OidcUser.class);
        when(oidcUser.getSubject()).thenReturn("google-user-id");
        when(oidcUser.getEmail()).thenReturn("user@example.com");
        when(oidcUser.getEmailVerified()).thenReturn(null);
        when(oidcUser.getFullName()).thenReturn(null);
        when(oidcUser.getPicture()).thenReturn(null);

        SocialUserProfile profile = mapper.map(oidcUser);

        assertThat(profile.emailVerified()).isFalse();
        assertThat(profile.displayName()).isNull();
        assertThat(profile.profileImageUrl()).isNull();
    }
}
