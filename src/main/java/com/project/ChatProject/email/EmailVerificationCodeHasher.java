package com.project.ChatProject.email;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Base64;

@Component
public class EmailVerificationCodeHasher {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final int MINIMUM_SECRET_LENGTH = 32;

    private static final Base64.Encoder HASH_ENCODER =
            Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder HASH_DECODER =
            Base64.getUrlDecoder();

    private final SecretKeySpec secretKey;

    public EmailVerificationCodeHasher(
            @Value(("email-verification.hash-secret"))
            String encodedSecret
    )
    {
        byte[] secretBytes = Base64.getDecoder().decode(encodedSecret);
        if (secretBytes.length < MINIMUM_SECRET_LENGTH) {
            throw new IllegalStateException(
                    "Email verification hash secret must be at least 32 bytes"
            );
        }

        this.secretKey = new SecretKeySpec(
                secretBytes,
                HMAC_ALGORITHM
        );
    }

    public String hash(String code) {
        byte[] hash = createHash(code);
        return HASH_ENCODER.encodeToString(hash);
    }

    public boolean matches(
            String code,
            String expectedHash
    )
    {
        try {
            byte[] actualHash = createHash(code);
            byte[] savedHash = HASH_DECODER.decode(expectedHash);

            return MessageDigest.isEqual(
                    actualHash,
                    savedHash
            );
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private byte[] createHash(String code) {
        try {
            Mac mac= Mac.getInstance(HMAC_ALGORITHM);
            mac.init(secretKey);

            return mac.doFinal(
                    code.getBytes(StandardCharsets.UTF_8)
            );
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(
                    "Failed to hash email verification code", e
            );
        }
    }
}
