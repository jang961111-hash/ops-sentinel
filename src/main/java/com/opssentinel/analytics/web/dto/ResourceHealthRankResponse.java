package com.opssentinel.analytics.web.dto;

import com.opssentinel.resource.entity.ResourceType;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * GET /api/analytics/resource-health-rank 응답용 DTO. 사건 발생 빈도(incidentCount) 내림차순으로
 * 정렬돼 반환되므로, 배열 순서 자체가 위험도 순위를 의미한다(별도 rank 필드 없음).
 */
@Schema(description = "리소스 위험도 랭킹 항목")
public record ResourceHealthRankResponse(
        Long resourceId,
        String resourceName,
        ResourceType resourceType,
        long incidentCount
) {
}
