package com.opssentinel.common.web;

import com.opssentinel.common.exception.ConflictException;
import com.opssentinel.common.web.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 전역 예외처리기(API 명세서 "공통 사항"/"7. 에러 코드" 준수).
 *
 * <p>응답 바디는 항상 {@code {timestamp, status, error, message, path}} 형식을 유지한다.
 *
 * <p><b>왜 {@link ResponseEntityExceptionHandler}를 상속하는가:</b> Spring MVC가 컨트롤러
 * 진입 전/디스패치 단계에서 자체적으로 던지는 프레임워크 예외들 — 깨진 JSON/잘못된 enum값
 * ({@link HttpMessageNotReadableException}), 지원하지 않는 HTTP 메서드
 * ({@link HttpRequestMethodNotSupportedException}), 존재하지 않는 경로
 * ({@link NoHandlerFoundException}/{@link NoResourceFoundException}) — 는 이 어드바이스가
 * 상속 없이 {@code @ExceptionHandler(Exception.class)}만 두던 이전 구조에서는 그 catch-all로
 * 떨어져 전부 500으로 응답됐다(architect 최종검증 C3 결함). {@link ResponseEntityExceptionHandler}는
 * 이 예외들을 이미 알맞은 상태코드로 분류해 최종적으로 {@link #handleExceptionInternal}을
 * 호출하도록 배선돼 있으므로, 그 메서드 하나만 오버라이드해 우리 공통 응답 포맷으로 통일한다.
 *
 * <p><b>주의:</b> {@link ResponseEntityExceptionHandler}가 이미
 * {@code @ExceptionHandler}로 처리하는 예외 타입(예: {@link MethodArgumentNotValidException})에
 * 대해 이 클래스에서 별도로 {@code @ExceptionHandler}를 또 선언하면 같은 예외 타입이 두
 * 메서드에 중복 매핑되어 기동 시 "Ambiguous @ExceptionHandler" 오류가 난다. 그래서 검증
 * 실패(@Valid) 메시지 포맷팅은 {@link #handleExceptionInternal}에서 {@code instanceof}로
 * 분기해 처리한다.
 *
 * <p><b>주의(P1 대비 설계 원칙):</b> OpenAI 연동 실패는 이 핸들러의 500 경로로 흘려보내지
 * 않는다 — API 명세서 7장 원칙상 OpenAI 실패는 폴백 문장으로 200 처리해야 하므로, 호출부
 * (서비스 계층)에서 자체적으로 catch해 폴백 응답을 만든다.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    /** 그 외 잘못된 요청 파라미터 — 400. */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage(), request);
    }

    /** 경로/쿼리 파라미터 타입 불일치(예: enum에 없는 값, 숫자 자리에 문자열) — 400. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        String message = "잘못된 요청 파라미터입니다: " + ex.getName() + "=" + ex.getValue();
        return build(HttpStatus.BAD_REQUEST, message, request);
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

    /**
     * {@link ResponseEntityExceptionHandler}가 다루는 모든 Spring MVC 프레임워크 예외
     * (검증 실패, 깨진 요청 본문, 지원하지 않는 메서드, 존재하지 않는 경로 등)의 최종 착지점.
     * 부모가 만든 {@code body}(ProblemDetail 등)는 버리고 이 프로젝트의 공통 응답 포맷으로
     * 다시 조립한다.
     */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex, Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
        HttpStatus status = HttpStatus.valueOf(statusCode.value());
        String path = extractPath(request);
        log.warn("Spring MVC 프레임워크 예외 처리: status={}, path={}, ex={}", status, path, ex.toString());

        HttpHeaders responseHeaders = new HttpHeaders();
        responseHeaders.putAll(headers);
        // Content-Type은 우리 ErrorResponse(JSON)로 그대로 협상되도록 남겨두지 않는다
        // (부모가 application/problem+json 등으로 이미 지정했을 수 있음).
        responseHeaders.remove(HttpHeaders.CONTENT_TYPE);

        ErrorResponse errorResponse = ErrorResponse.of(status, resolveMessage(ex), path);
        return ResponseEntity.status(status).headers(responseHeaders).body(errorResponse);
    }

    private String resolveMessage(Exception ex) {
        if (ex instanceof MethodArgumentNotValidException manv) {
            return manv.getBindingResult().getFieldErrors().stream()
                    .map(this::formatFieldError)
                    .collect(Collectors.joining(", "));
        }
        if (ex instanceof HttpMessageNotReadableException) {
            return "요청 본문을 읽을 수 없습니다. JSON 형식 또는 값을 확인해주세요.";
        }
        if (ex instanceof HttpRequestMethodNotSupportedException methodNotSupported) {
            return "지원하지 않는 HTTP 메서드입니다: " + methodNotSupported.getMethod();
        }
        if (ex instanceof NoResourceFoundException || ex instanceof NoHandlerFoundException) {
            return "요청한 경로를 찾을 수 없습니다.";
        }
        return ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
    }

    private String extractPath(WebRequest request) {
        if (request instanceof ServletWebRequest servletWebRequest) {
            return servletWebRequest.getRequest().getRequestURI();
        }
        return request.getDescription(false);
    }

    private String formatFieldError(FieldError fieldError) {
        return fieldError.getField() + ": " + fieldError.getDefaultMessage();
    }

    private ResponseEntity<ErrorResponse> build(HttpStatus status, String message, HttpServletRequest request) {
        return ResponseEntity.status(status).body(ErrorResponse.of(status, message, request.getRequestURI()));
    }
}
