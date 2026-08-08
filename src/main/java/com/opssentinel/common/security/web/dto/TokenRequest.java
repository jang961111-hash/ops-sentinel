package com.opssentinel.common.security.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "관리자 토큰 발급 요청")
public record TokenRequest(
        @Schema(description = "관리자 사용자명", example = "admin") @NotBlank String username,
        @Schema(description = "관리자 비밀번호") @NotBlank String password
) {
}
