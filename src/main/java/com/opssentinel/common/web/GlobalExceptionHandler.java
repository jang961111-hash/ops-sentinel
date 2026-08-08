package com.opssentinel.common.web;

import com.opssentinel.common.exception.ConflictException;
import com.opssentinel.common.web.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

/**
 * 전역 예외처리기(API 명세서 "공통 사항"/"7. 에러 코드" 준수).
 *
 * <p>응답 바디는 항상 {@code {timestamp, status, error, message, path}} 형식을 유지한다.
 *
 * <p><b>주의(P1 대비 설계 원칙):</b> 향후 OpenAI 연동이 추가돼도 그 실패는 이 핸들러의
 * 500 경로로 흘려보내면 안 된다 — API 명세서 7장 원칙상 OpenAI 실패는 폴백 문장으로
 * 200 처리해야 하므로, 호출부(서비스 계층)에서 자체적으로 catch해 폴백 응답을 만들어야
 * 한다. 지금은 OpenAI 연동이 없어 이 핸들러에 반영할 내용이 없다.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** Bean Validation(@Valid) 실패 — 400. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(
            MethodArgumentNotValidException ex, HttpServletRequest request) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(this::formatFieldError)
                .collect(Collectors.joining(", "));
        return build(HttpStatus.BAD_REQUEST, message, request);
    }

    /** 경로/쿼리 파라미터 타입 불일치(예: enum에 없는 값, 숫자 자리에 문자열) — 400. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        String message = "잘못된 요청 파라미터입니다: " + ex.getName() + "=" + ex.getValue();
        return build(HttpStatus.BAD_REQUEST, message, request);
    }

    /** 그 외 잘못된 요청 파라미터 — 400. */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(
            IllegalArgumentException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage(), request);
    }

    /** 동시성 충돌(중복 사건 생성 재시도 소진 등) — 409. */
    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ErrorResponse> handleConflict(ConflictException ex, HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, ex.getMessage(), request);
    }

    /** 서비스 계층에서 직접 던진 상태코드 있는 예외(현재는 주로 404) — 그대로 반영. */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatus(
            ResponseStatusException ex, HttpServletRequest request) {
        HttpStatus status = HttpStatus.valueOf(ex.getStatusCode().value());
        return build(status, ex.getReason(), request);
    }

    /** 그 외 처리되지 않은 모든 예외 — 500. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("처리되지 않은 예외 발생: {}", ex.getMessage(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류가 발생했습니다.", request);
    }

    private String formatFieldError(FieldError fieldError) {
        return fieldError.getField() + ": " + fieldError.getDefaultMessage();
    }

    private ResponseEntity<ErrorResponse> build(HttpStatus status, String message, HttpServletRequest request) {
        return ResponseEntity.status(status).body(ErrorResponse.of(status, message, request.getRequestURI()));
    }
}
