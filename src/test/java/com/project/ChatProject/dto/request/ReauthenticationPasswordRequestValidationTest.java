package com.project.ChatProject.dto.request;

import com.project.ChatProject.dto.enums.ReauthenticationPurpose;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReauthenticationPasswordRequestValidationTest {

    private ValidatorFactory validatorFactory;
    private Validator validator;

    @BeforeEach
    void setUp() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterEach
    void tearDown() {
        validatorFactory.close();
    }

    @Test
    void validRequestHasNoViolations() {
        ReauthenticationPasswordRequest request =
                new ReauthenticationPasswordRequest(
                        "password123!",
                        ReauthenticationPurpose.LOGOUT_ALL
                );

        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void shortPasswordIsInvalid() {
        ReauthenticationPasswordRequest request =
                new ReauthenticationPasswordRequest(
                        "pass123",
                        ReauthenticationPurpose.LOGOUT_ALL
                );

        assertThat(validator.validate(request)).isNotEmpty();
    }

    @Test
    void longPasswordIsInvalid() {
        ReauthenticationPasswordRequest request =
                new ReauthenticationPasswordRequest(
                        "a".repeat(31),
                        ReauthenticationPurpose.LOGOUT_ALL
                );

        assertThat(validator.validate(request)).isNotEmpty();
    }

    @Test
    void missingPurposeIsInvalid() {
        ReauthenticationPasswordRequest request =
                new ReauthenticationPasswordRequest(
                        "password123!",
                        null
                );

        assertThat(validator.validate(request)).isNotEmpty();
    }
}
