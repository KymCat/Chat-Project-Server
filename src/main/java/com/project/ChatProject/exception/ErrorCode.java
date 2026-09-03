package com.project.ChatProject.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum ErrorCode {
    // Member Exception
    DUPLICATE_MEMBER_EMAIL  (HttpStatus.CONFLICT, "MEMBER-001","이미 존재하는 이메일입니다."),
    MEMBER_BLOCKED          (HttpStatus.FORBIDDEN, "MEMBER-002", "이용이 제한된 계정입니다"),
    MEMBER_WITHDRAWN        (HttpStatus.FORBIDDEN, "MEMBER-003", "탈퇴한 회원입니다."),
    MEMBER_NOT_FOUND         (HttpStatus.NOT_FOUND, "MEMBER-004", "존재하지 않은 유저입니다."),

    // Auth Exception
    INVALID_CREDENTIALS     (HttpStatus.UNAUTHORIZED, "AUTH-001", "이메일 또는 비밀번호가 올바르지 않습니다."),

    // 공통 Exception
    INTERNAL_SERVER_ERROR   (HttpStatus.INTERNAL_SERVER_ERROR, "COMMON-001", "서버 내부 오류가 발생했습니다"),
    NO_SUCH_ALGORITHM       (HttpStatus.INTERNAL_SERVER_ERROR, "COMMON-002", "해당 알고리즘을 사용할 수 없습니다."),

    // Validation Exception
    INVALID_REQUEST_VALUE           (HttpStatus.BAD_REQUEST, "VALID-001", "잘못된 요청 파라미터입니다."),
    INVALID_REQUEST_TYPE_MISMATCH   (HttpStatus.BAD_REQUEST, "VALID-002", "잘못된 요청 파라미터 타입입니다."),
    INVALID_REQUEST_PARAM_MISSING   (HttpStatus.BAD_REQUEST, "VALID-003", "누락된 요청 파라미터가 있습니다."),

    // JWT Exception
    INVALID_ACCESS_TOKEN        (HttpStatus.UNAUTHORIZED, "JWT-001", "유효하지 않은 AccessToken입니다."),
    INVALID_REFRESH_TOKEN       (HttpStatus.UNAUTHORIZED, "JWT-002", "유효하지 않은 RefreshToken입니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
