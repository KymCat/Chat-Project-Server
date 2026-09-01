package com.project.ChatProject.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum ErrorCode {
    DUPLICATE_MEMBER_EMAIL      (HttpStatus.CONFLICT, "MEMBER-001","이미 존재하는 이메일입니다."),

    // 공통 Exception
    INTERNAL_SERVER_ERROR   (HttpStatus.INTERNAL_SERVER_ERROR, "COMMON-001", "서버 내부 오류가 발생했습니다"),

    // Validation Exception
    INVALID_REQUEST_VALUE           (HttpStatus.BAD_REQUEST, "VALID-001", "잘못된 요청 파라미터입니다."),
    INVALID_REQUEST_TYPE_MISMATCH   (HttpStatus.BAD_REQUEST, "VALID-002", "잘못된 요청 파라미터 타입입니다."),
    INVALID_REQUEST_PARAM_MISSING   (HttpStatus.BAD_REQUEST, "VALID-003", "누락된 요청 파라미터가 있습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
