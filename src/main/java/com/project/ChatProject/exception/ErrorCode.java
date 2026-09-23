package com.project.ChatProject.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum ErrorCode {
    // Member Exception
    DUPLICATE_MEMBER_EMAIL(
            HttpStatus.CONFLICT,
            "MEMBER-001",
            "이미 존재하는 이메일입니다."
    ),
    MEMBER_BLOCKED(
            HttpStatus.FORBIDDEN,
            "MEMBER-002",
            "이용이 제한된 계정입니다"
    ),
    MEMBER_WITHDRAWN(
            HttpStatus.FORBIDDEN,
            "MEMBER-003",
            "탈퇴한 회원입니다."
    ),
    MEMBER_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "MEMBER-004",
            "존재하지 않은 유저입니다."
    ),

    // ChatRoom Exception
    CHAT_ROOM_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "CHAT_ROOM_001",
            "존재하지 않은 채팅방입니다."
    ),
    CHAT_ROOM_ALREADY_JOINED(
            HttpStatus.CONFLICT,
            "CHAT_ROOM_002",
            "이미 참여한 채팅방입니다."
    ),
    CHAT_ROOM_DELETED(
            HttpStatus.GONE,
            "CHAT_ROOM_003",
            "이미 삭제된 채팅방입니다."
    ),
    INVALID_CHAT_ROOM_TYPE(
            HttpStatus.BAD_REQUEST,
            "CHAT_ROOM_004",
            "잘못된 채팅방 유형입니다."
    ),
    CHAT_ROOM_ACCESS_DENIED(
            HttpStatus.FORBIDDEN,
            "CHAT_ROOM_005",
            "현재 참여중인 채팅방이 아닙니다."
    ),
    CHAT_ROOM_OWNER_TRANSFER_REQUIRED(
            HttpStatus.CONFLICT,
            "CHAT_ROOM_006",
            "방장은 소유권을 위임한 후 채팅방을 나갈 수 있습니다."
    ),
    CHAT_ROOM_OWNER_REQUIRED(
            HttpStatus.FORBIDDEN,
            "CHAT_ROOM_007",
            "방장 권한이 필요합니다."
    ),
    INVALID_OWNER_TRANSFER_TARGET(
            HttpStatus.BAD_REQUEST,
            "CHAT_ROOM_008",
            "방장 권한을 위임할 수 없는 회원입니다."
    ),

    // Chat Message Exception
    CHAT_MESSAGE_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "CHAT_MESSAGE_001",
            "존재하지 않은 채팅 메세지입니다."
    ),
    CHAT_MESSAGE_DELETE_FORBIDDEN(
            HttpStatus.FORBIDDEN,
            "CHAT_MESSAGE_002",
            "자신이 작성한 메세지만 삭제 가능합니다."
    ),
    CHAT_MESSAGE_TYPE_DELETE_NOT_ALLOWED(
            HttpStatus.FORBIDDEN,
            "CHAT_MESSAGE_003",
            "시스템 메세지는 삭제 불가능합니다."
    ),
    CHAT_MESSAGE_EDIT_FORBIDDEN(
            HttpStatus.FORBIDDEN,
            "CHAT_MESSAGE_004",
            "자신이 작성한 메세지만 수정 가능합니다."
    ),
    CHAT_MESSAGE_TYPE_EDIT_NOT_ALLOWED(
            HttpStatus.FORBIDDEN,
            "CHAT_MESSAGE_005",
            "해당 메세지는 수정 불가능합니다."
    ),

    // Auth Exception
    INVALID_CREDENTIALS(
            HttpStatus.UNAUTHORIZED,
            "AUTH-001",
            "이메일 또는 비밀번호가 올바르지 않습니다."
    ),

    // Email Verification Exception
    EMAIL_ALREADY_VERIFIED(
            HttpStatus.CONFLICT,
            "EMAIL-001",
            "이미 인증된 이메일입니다."
    ),
    EMAIL_VERIFICATION_NOT_FOUND(
            HttpStatus.BAD_REQUEST,
            "EMAIL-002",
            "인증 요청이 없거나 인증 시간이 만료되었습니다."
    ),
    INVALID_EMAIL_VERIFICATION_CODE(
            HttpStatus.BAD_REQUEST,
            "EMAIL-003",
            "이메일 인증 코드가 올바르지 않습니다."
    ),
    EMAIL_VERIFICATION_REQUEST_TOO_FREQUENT(
            HttpStatus.TOO_MANY_REQUESTS,
            "EMAIL-004",
            "잠시 후 인증 메일을 다시 요청해주세요."
    ),
    EMAIL_SEND_FAILED(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "EMAIL-005",
            "인증 메일을 발송하지 못했습니다."
    ),
    EMAIL_VERIFICATION_REQUIRED(
            HttpStatus.FORBIDDEN,
            "EMAIL-006",
            "이메일 인증이 필요합니다."
    ),

    // Attachment Exception
    ATTACHMENT_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "ATTACHMENT-001",
            "존재하지 않는 첨부파일입니다."
    ),
    ATTACHMENT_STORAGE_FAILED(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "ATTACHMENT-002",
            "첨부파일 저장 처리에 실패했습니다."
    ),
    INVALID_ATTACHMENT_STORAGE_KEY(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "ATTACHMENT-003",
            "잘못된 첨부파일 저장 경로입니다."
    ),
    ATTACHMENT_EMPTY(
            HttpStatus.BAD_REQUEST,
            "ATTACHMENT-004",
            "빈 파일은 첨부할 수 없습니다."
    ),
    ATTACHMENT_TOO_LARGE(
            HttpStatus.PAYLOAD_TOO_LARGE,
            "ATTACHMENT-005",
            "첨부파일 허용 크기를 초과했습니다."
    ),
    ATTACHMENT_TYPE_NOT_ALLOWED(
            HttpStatus.UNSUPPORTED_MEDIA_TYPE,
            "ATTACHMENT-006",
            "허용되지 않는 첨부파일 형식입니다."
    ),
    ATTACHMENT_INVALID_IMAGE(
            HttpStatus.BAD_REQUEST,
            "ATTACHMENT-007",
            "유효한 이미지 파일이 아닙니다."
    ),
    ATTACHMENT_INVALID_FILENAME(
            HttpStatus.BAD_REQUEST,
            "ATTACHMENT-008",
            "유효하지 않은 첨부파일 이름입니다."
    ),

    // Common Exception
    INTERNAL_SERVER_ERROR(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "COMMON-001",
            "서버 내부 오류가 발생했습니다"
    ),
    NO_SUCH_ALGORITHM(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "COMMON-002",
            "해당 알고리즘을 사용할 수 없습니다."
    ),

    // Validation Exception
    INVALID_REQUEST_VALUE(
            HttpStatus.BAD_REQUEST,
            "VALID-001",
            "잘못된 요청 파라미터입니다."
    ),
    INVALID_REQUEST_TYPE_MISMATCH(
            HttpStatus.BAD_REQUEST,
            "VALID-002",
            "잘못된 요청 파라미터 타입입니다."
    ),
    INVALID_REQUEST_PARAM_MISSING(
            HttpStatus.BAD_REQUEST,
            "VALID-003",
            "누락된 요청 파라미터가 있습니다."
    ),

    // JWT Exception
    INVALID_ACCESS_TOKEN(
            HttpStatus.UNAUTHORIZED,
            "JWT-001",
            "유효하지 않은 AccessToken입니다."
    ),
    INVALID_REFRESH_TOKEN(
            HttpStatus.UNAUTHORIZED,
            "JWT-002",
            "유효하지 않은 RefreshToken입니다."
    );

    private final HttpStatus status;
    private final String code;
    private final String message;
}
