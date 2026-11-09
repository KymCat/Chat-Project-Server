package com.project.ChatProject.reauth;

import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

class ReauthenticationTokenGeneratorTest {

    private final ReauthenticationTokenGenerator generator =
            new ReauthenticationTokenGenerator();

    @Test
    void generateReturnsUrlSafeTokenWithThirtyTwoRandomBytes() {
        String token = generator.generate();

        byte[] decoded = Base64.getUrlDecoder().decode(token);

        assertThat(decoded).hasSize(32);
        assertThat(token).doesNotContain("=", "+", "/");
    }

    @Test
    void generateReturnsDifferentTokenEachTime() {
        String first = generator.generate();
        String second = generator.generate();

        assertThat(first).isNotEqualTo(second);
    }
}
