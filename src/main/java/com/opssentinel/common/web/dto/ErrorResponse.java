package com.opssentinel.common.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import org.springframework.http.HttpStatus;

/**
 * 공통 에러 응답 바디(API 명세서 "공통 사항" 준수).
 * 필드명은 {@code timestamp, status, error, message, path} 순서·이름을 그대로 따른다.
 */
@Schema(description = "공통 에러 응답")
public record ErrorResponse(
        @Schema(description = "에러 발생 시각") LocalDateTime timestamp,
        @Schema(description = "HTTP 상태 코드", example = "404") int status,
        @Schema(description = "HTTP 상태 사유 문구", example = "Not Found") String error,
        @Schema(description = "에러 메시지") String message,
        @Schema(description = "요청 경로", example = "/api/metrics/999/latest") String path
) {

    public static ErrorResponse of(HttpStatus status, String message, String path) {
        return new ErrorResponse(LocalDateTime.now(), status.value(), status.getReasonPhrase(), message, path);
    }
}
