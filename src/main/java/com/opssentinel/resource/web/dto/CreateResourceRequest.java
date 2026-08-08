package com.opssentinel.resource.web.dto;

import com.opssentinel.resource.entity.ResourceType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 리소스 등록(POST /api/resources) 요청 바디.
 */
@Schema(description = "리소스 등록 요청")
public record CreateResourceRequest(

        @Schema(description = "리소스 이름", example = "order-service-db")
        @NotBlank
        String name,

        @Schema(description = "리소스 타입", example = "DATABASE")
        @NotNull
        ResourceType type
) {
}
