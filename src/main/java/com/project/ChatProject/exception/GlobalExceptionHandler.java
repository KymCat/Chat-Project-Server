package com.project.ChatProject.exception;

import com.project.ChatProject.dto.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(CustomException.class)
    public ResponseEntity<ApiResponse<Void>> handleCustomException(
            CustomException e,
            HttpServletRequest request)
    {

        ErrorCode errorCode = e.getErrorCode();
        HttpStatus status = errorCode.getStatus();
        String code = errorCode.getCode();
        String msg = errorCode.getMessage();

        log.warn("비지니스 예외, code={}, path={}",
                errorCode.getCode(), request.getRequestURI());

        return ResponseEntity
                .status(status)
                .body(ApiResponse.failure(code, msg));
    }

    // DB 제약조건 위반 예외
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handlerDataIntegrityViolationException(
            DataIntegrityViolationException e,
            HttpServletRequest request
    )
    {
        log.error(
                "Database integrity violation, path={}",
                request.getRequestURI(),
                e
        );

        ErrorCode errorCode = ErrorCode.INTERNAL_SERVER_ERROR;

        return ResponseEntity
                .status(errorCode.getStatus())
                .body(ApiResponse.failure(
                        errorCode.getCode(),
                        errorCode.getMessage()
                ));
    }

    // DTO 유효성 검증 예외 (@Valid)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodArgumentNotValidException(
            MethodArgumentNotValidException e,
            HttpServletRequest request)
    {
        log.warn("DTO 검증 예외 path = {}", request.getRequestURI());

        String code = ErrorCode.INVALID_REQUEST_VALUE.getCode();
        String msg = e.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("\n"));

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.failure(code, msg));
    }

    // Controller 개별 파라미터 검증 예외 (@Validated)
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolationException(
            ConstraintViolationException e)
    {
        for (ConstraintViolation<?> violation : e.getConstraintViolations()) {
            String parameterPath = violation.getPropertyPath().toString();
            String validationMsg = violation.getMessage();

            String logs = String.format("'%s' 값이 유효하지 않습니다. %s",
                    parameterPath, validationMsg);
            log.warn(logs);
        }

        String code = ErrorCode.INVALID_REQUEST_VALUE.getCode();
        String msg = ErrorCode.INVALID_REQUEST_VALUE.getMessage();

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.failure(code, msg));
    }
}
