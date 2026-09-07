package com.project.ChatProject.email;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Locale;

@Component
public class EmailVerificationCodeGenerator {

    private static final int CODE_BOUND = 1_000_000;
    private static final String CODE_FORMAT = "%06d";

    private final SecureRandom secureRandom = new SecureRandom();

    public String generate() {
        int code = secureRandom.nextInt(CODE_BOUND);

        return String.format(
                Locale.ROOT,
                CODE_FORMAT,
                code
        );
    }
}
