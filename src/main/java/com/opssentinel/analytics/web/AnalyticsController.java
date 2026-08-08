package com.opssentinel.analytics.web;

import com.opssentinel.analytics.service.AnalyticsService;
import com.opssentinel.analytics.web.dto.IncidentSummaryByResourceResponse;
import com.opssentinel.analytics.web.dto.ResourceHealthRankResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Analytics", description = "MyBatis 기반 집계 통계 API")
@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    @Operation(summary = "사건 집계 통계",
            description = "리소스별 사건 건수·심각도 분포·평균 해결시간(분)을 집계한다. from/to로 detectedAt 기준 기간 필터링 가능(둘 다 optional).")
    @GetMapping("/incident-summary")
    public List<IncidentSummaryByResourceResponse> incidentSummary(
            @Parameter(description = "집계 시작 시각(detectedAt >= from, 포함, ISO-8601)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @Parameter(description = "집계 종료 시각(detectedAt <= to, 포함, ISO-8601)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
        return analyticsService.incidentSummary(from, to);
    }

    @Operation(summary = "리소스 위험도 랭킹", description = "사건 발생 빈도(incidentCount) 내림차순으로 전체 리소스를 랭킹한다.")
    @GetMapping("/resource-health-rank")
    public List<ResourceHealthRankResponse> resourceHealthRank() {
        return analyticsService.resourceHealthRank();
    }
}
