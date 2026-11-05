package com.project.ChatProject.dto.request;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UpdateStatusMessageValidationTest {

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
    void nullStatusMessageIsValid() {
        UpdateStatusMessage request = new UpdateStatusMessage(null);

        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void fiftyKoreanCharactersAreValid() {
        UpdateStatusMessage request =
                new UpdateStatusMessage("가".repeat(50));

        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void fiftyOneKoreanCharactersAreInvalid() {
        UpdateStatusMessage request =
                new UpdateStatusMessage("가".repeat(51));

        assertThat(validator.validate(request)).hasSize(1);
    }
}
