package com.opssentinel.analytics.service;

import com.opssentinel.analytics.AnalyticsMapper;
import com.opssentinel.analytics.web.dto.IncidentSummaryByResourceResponse;
import com.opssentinel.analytics.web.dto.ResourceHealthRankResponse;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Analytics 집계 서비스. 집계 자체는 AnalyticsMapper(MyBatis, 순수 SQL)가 전담하고, 이 서비스는
 * 파라미터를 그대로 전달하는 얇은 위임 계층이다(집계 로직을 JPA/Java 쪽으로 가져오지 않기 위함).
 */
@Service
@RequiredArgsConstructor
public class AnalyticsService {

    private final AnalyticsMapper analyticsMapper;

    public List<IncidentSummaryByResourceResponse> incidentSummary(LocalDateTime from, LocalDateTime to) {
        return analyticsMapper.selectIncidentSummary(from, to);
    }

    public List<ResourceHealthRankResponse> resourceHealthRank() {
        return analyticsMapper.selectResourceHealthRank();
    }
}
