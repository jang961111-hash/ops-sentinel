package com.opssentinel.incident.web.dto;

import com.opssentinel.incident.entity.Incident;
import com.opssentinel.incident.entity.IncidentSeverity;
import com.opssentinel.incident.entity.IncidentStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

/**
 * 사건 목록(GET /api/incidents) 응답용 DTO. resourceName은 Resource 도메인을 별도 조회해 채운다
 * (Incident 엔티티는 resourceId만 스칼라로 보관한다).
 */
@Schema(description = "사건 목록 응답")
public record IncidentSummaryResponse(
        Long id,
        Long resourceId,
        String resourceName,
        IncidentSeverity severity,
        IncidentStatus status,
        String ruleTriggered,
        LocalDateTime detectedAt,
        LocalDateTime resolvedAt
) {

    public static IncidentSummaryResponse from(Incident incident, String resourceName) {
        return new IncidentSummaryResponse(
                incident.getId(),
                incident.getResourceId(),
                resourceName,
                incident.getSeverity(),
                incident.getStatus(),
                incident.getRuleTriggered(),
                incident.getDetectedAt(),
                incident.getResolvedAt()
        );
    }
}
