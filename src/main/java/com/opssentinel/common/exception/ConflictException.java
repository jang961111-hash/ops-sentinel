package com.opssentinel.common.exception;

/**
 * 동시성 충돌(예: 중복 사건 생성 재시도 소진)을 나타내는 도메인 예외.
 * {@link com.opssentinel.common.web.GlobalExceptionHandler}가 HTTP 409로 매핑한다.
 */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
