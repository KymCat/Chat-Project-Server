package com.project.ChatProject.jwt.refresh;

import com.project.ChatProject.exception.CustomException;
import com.project.ChatProject.exception.ErrorCode;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Component
public class RefreshTokenHasher {
    private static final String HASH_ALGORITHM = "SHA-256";

    // refreshToken sha-256 hashing
    public String hash(String refreshToken) {
        try {
            MessageDigest messageDigest =
                    MessageDigest.getInstance(HASH_ALGORITHM);

            byte[] hashedBytes = messageDigest.digest(
                    refreshToken.getBytes(StandardCharsets.UTF_8)
            );

            return HexFormat.of().formatHex(hashedBytes);

        } catch (NoSuchAlgorithmException e) {
            throw new CustomException(ErrorCode.NO_SUCH_ALGORITHM);
        }
    }
}
