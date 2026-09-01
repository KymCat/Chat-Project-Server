package com.project.ChatProject.dto.request;


import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SignupRequest(

        @Size(
                max = 2048,
                message = "프로필 URL 최대 길이는 2048자 입니다."
        )
        String profileImageUrl,

        @NotBlank(message = "이메일은 필수입니다.")
        @Email(message = "올바른 이메일 형식이 아닙니다.")
        @Size(
                max = 254,
                message = "이메일은 254자 이하여야 합니다."
        )
        String email,

        @NotBlank(message = "비밀번호는 필수입니다.")
        @Size(
                min = 8,
                max = 30,
                message = "비밀번호는 8자 이상 30자 이하여야 합니다."
        )
        @Pattern(
                regexp = "^(?=.*[a-zA-Z])(?=.*\\d)[a-zA-Z\\d!@#$%^&*()_+=-]+$",
                message = "비밀번호는 영문, 숫자, 특수문자를 포함한 8~20자여야 합니다."
        )
        String password,

        @NotBlank(message = "닉네임은 필수입니다.")
        @Size(
                min = 2,
                max = 30,
                message = "닉네임은 2자 이상 30자 이하여야 합니다."
        )
        @Pattern(
                regexp = "^[a-zA-Z0-9가-힣]+$",
                message = "닉네임은 한글, 영어 소문자, 숫자만 가능합니다."
        )
        String nickname
) { }
