package com.opssentinel.incident.web.dto;

import com.opssentinel.incident.entity.Incident;
import com.opssentinel.incident.entity.IncidentSeverity;
import com.opssentinel.incident.entity.IncidentStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 사건 상세(GET /api/incidents/{id}) 응답 DTO. API 명세서 3장 응답 예시의 필드 구성을 그대로
 * 따른다 — resourceName은 Resource 도메인을 별도 조회해 채우고, aiSummary는 OpenAI 연동
 * (AiSummaryService) 결과를 그대로 반환한다. OpenAI 호출이 비활성/실패/타임아웃이면
 * Incident.aiSummary에 폴백 문장이 채워져 있으므로 이 필드가 null이 되는 경우는 거의 없다.
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
