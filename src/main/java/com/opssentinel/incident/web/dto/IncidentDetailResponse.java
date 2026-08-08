package com.opssentinel.incident.web.dto;

import com.opssentinel.incident.entity.Incident;
import com.opssentinel.incident.entity.IncidentSeverity;
import com.opssentinel.incident.entity.IncidentStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 사건 상세(GET /api/incidents/{id}) 응답 DTO. API 명세서 3장 응답 예시의 필드 구성을 그대로
 * 따른다 — resourceName은 Resource 도메인을 별도 조회해 채우고, aiSummary는 아직 AI 연동이
 * 없어 Incident.aiSummary가 null이면 그대로 null을 반환한다.
 */
@Schema(description = "사건 상세 응답(조치이력·AI요약 포함)")
public record IncidentDetailResponse(
        Long id,
        Long resourceId,
        String resourceName,
        IncidentSeverity severity,
        IncidentStatus status,
        String ruleTriggered,
        String aiSummary,
        LocalDateTime detectedAt,
        List<IncidentActionResponse> actions
) {

    public static IncidentDetailResponse from(Incident incident, String resourceName) {
        return new IncidentDetailResponse(
                incident.getId(),
                incident.getResourceId(),
                resourceName,
                incident.getSeverity(),
                incident.getStatus(),
                incident.getRuleTriggered(),
                incident.getAiSummary(),
                incident.getDetectedAt(),
                incident.getActions().stream().map(IncidentActionResponse::from).toList()
        );
    }
}
