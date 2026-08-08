package com.opssentinel.resource.web.dto;

import com.opssentinel.resource.entity.Resource;
import com.opssentinel.resource.entity.ResourceType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

/**
 * Resource 엔티티를 외부에 노출하지 않기 위한 응답 DTO.
 */
@Schema(description = "리소스 응답")
public record ResourceResponse(
        Long id,
        String name,
        ResourceType type,
        LocalDateTime createdAt
) {

    public static ResourceResponse from(Resource resource) {
        return new ResourceResponse(
                resource.getId(),
                resource.getName(),
                resource.getType(),
                resource.getCreatedAt()
        );
    }
}
