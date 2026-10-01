package com.project.ChatProject.oauth;

import com.project.ChatProject.entity.enums.SocialProvider;

public record SocialUserProfile(
        /*
            Google          MyRecord
            sub             -> providerUserId
            email           -> email
            email_verified  -> emailVerified
            name            -> displayName
            picture         -> profileImageUrl
         */
        SocialProvider provider,
        String providerUserId,
        String email,
        boolean emailVerified,
        String displayName,
        String profileImageUrl
) {
}
