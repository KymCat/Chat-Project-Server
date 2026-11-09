package com.project.ChatProject.dto.request;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class UpdateNicknameRequestValidationTest {

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
    void twoCharactersAreValid() {
        UpdateNicknameRequest request =
                new UpdateNicknameRequest("가나");

        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void thirtyCharactersAreValid() {
        UpdateNicknameRequest request =
                new UpdateNicknameRequest("가".repeat(30));

        assertThat(validator.validate(request)).isEmpty();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "가", "닉네임!", "nick name"})
    void invalidNicknameIsRejected(String nickname) {
        UpdateNicknameRequest request =
                new UpdateNicknameRequest(nickname);

        assertThat(validator.validate(request)).isNotEmpty();
    }

    @Test
    void thirtyOneCharactersAreInvalid() {
        UpdateNicknameRequest request =
                new UpdateNicknameRequest("가".repeat(31));

        assertThat(validator.validate(request)).isNotEmpty();
    }
}
