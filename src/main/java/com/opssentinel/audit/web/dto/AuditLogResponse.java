package com.opssentinel.audit.web.dto;

import com.opssentinel.audit.entity.AuditLog;
import com.opssentinel.audit.entity.ResultStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

/**
 * AuditLog 엔티티를 외부에 노출하지 않기 위한 응답 DTO.
 */
@Schema(description = "감사로그 응답")
public record AuditLogResponse(
        Long id,
        String actorType,
        String action,
        String targetType,
        Long targetId,
        String requestSummary,
        ResultStatus resultStatus,
        String errorMessage,
        LocalDateTime createdAt
) {

    public static AuditLogResponse from(AuditLog auditLog) {
        return new AuditLogResponse(
                auditLog.getId(),
                auditLog.getActorType(),
                auditLog.getAction(),
                auditLog.getTargetType(),
                auditLog.getTargetId(),
                auditLog.getRequestSummary(),
                auditLog.getResultStatus(),
                auditLog.getErrorMessage(),
                auditLog.getCreatedAt()
        );
    }
}
