package com.opssentinel.common.security.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "관리자 토큰 발급 응답")
public record TokenResponse(
        @Schema(description = "발급된 JWT — 이후 요청의 Authorization: Bearer <accessToken> 헤더로 사용") String accessToken,
        @Schema(description = "토큰 타입", example = "Bearer") String tokenType,
        @Schema(description = "만료까지 남은 시간(ms)") long expiresInMs
) {

    public static TokenResponse of(String accessToken, long expiresInMs) {
        return new TokenResponse(accessToken, "Bearer", expiresInMs);
    }
}
