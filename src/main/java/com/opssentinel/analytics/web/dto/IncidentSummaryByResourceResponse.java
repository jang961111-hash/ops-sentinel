package com.opssentinel.analytics.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * GET /api/analytics/incident-summary 응답용 DTO. 리소스 1건당 1행이며, MyBatis 집계 쿼리
 * (AnalyticsMapper.xml)가 GROUP BY로 만든 결과를 그대로 매핑한다.
 */
@Schema(description = "리소스별 사건 집계(건수, 심각도 분포, 평균 해결시간)")
public record IncidentSummaryByResourceResponse(
        Long resourceId,
        String resourceName,
        long incidentCount,
        long lowCount,
        long mediumCount,
        long highCount,
        long criticalCount,
        @Schema(description = "평균 해결시간(분). resolvedAt이 채워진 사건만 대상이며, 해당 리소스에 해결된 사건이 하나도 없으면 null")
        Double avgResolutionMinutes
) {
}
