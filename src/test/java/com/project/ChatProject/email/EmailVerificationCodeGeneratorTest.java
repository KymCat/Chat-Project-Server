package com.project.ChatProject.email;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EmailVerificationCodeGeneratorTest {

    private final EmailVerificationCodeGenerator codeGenerator =
            new EmailVerificationCodeGenerator();

    @Test
    void generateReturnsSixDigitCode() {
        for (int index = 0; index < 100; index++) {
            assertThat(codeGenerator.generate())
                    .matches("^\\d{6}$");
        }
    }
}
