package com.project.ChatProject.email;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

class EmailVerificationCodeHasherTest {

    private EmailVerificationCodeHasher codeHasher;

    @BeforeEach
    void setUp() {
        String encodedSecret = Base64.getEncoder()
                .encodeToString(new byte[32]);
        codeHasher = new EmailVerificationCodeHasher(encodedSecret);
    }

    @Test
    void hashIsDeterministicAndDoesNotExposeCode() {
        String firstHash = codeHasher.hash("123456");
        String secondHash = codeHasher.hash("123456");

        assertThat(firstHash).isEqualTo(secondHash);
        assertThat(firstHash).isNotEqualTo("123456");
    }

    @Test
    void matchesAcceptsCorrectCodeAndRejectsWrongCode() {
        String savedHash = codeHasher.hash("123456");

        assertThat(codeHasher.matches("123456", savedHash)).isTrue();
        assertThat(codeHasher.matches("654321", savedHash)).isFalse();
    }

    @Test
    void matchesRejectsMalformedSavedHash() {
        assertThat(codeHasher.matches("123456", "invalid hash"))
                .isFalse();
    }
}
