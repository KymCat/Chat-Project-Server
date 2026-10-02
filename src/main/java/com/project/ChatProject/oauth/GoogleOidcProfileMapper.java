package com.project.ChatProject.oauth;

import com.project.ChatProject.entity.enums.SocialProvider;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Component;

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
                oidcUser.getPicture()
        );
    }
}
