package com.opssentinel.analytics;

import com.opssentinel.analytics.web.dto.IncidentSummaryByResourceResponse;
import com.opssentinel.analytics.web.dto.ResourceHealthRankResponse;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 집계 전용 MyBatis 매퍼. "JPA=CRUD, MyBatis=집계" 역할 분리 원칙(PRD 3-1)에 따라
 * incidents/resources를 JOIN·GROUP BY하는 통계 쿼리는 JPA 대신 여기(순수 SQL, AnalyticsMapper.xml)로만
 * 구현한다.
 */
@Mapper
public interface AnalyticsMapper {

    /**
     * 리소스별 사건 건수·심각도 분포·평균 해결시간(분)을 집계한다.
     * from/to는 detectedAt 기준 기간 필터이며 둘 다 optional(null 허용, 미지정 시 전체 기간).
     */
    List<IncidentSummaryByResourceResponse> selectIncidentSummary(
            @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    /** 사건 발생 빈도(incidentCount) 내림차순으로 리소스 위험도를 랭킹한다(사건이 없는 리소스도 0건으로 포함). */
    List<ResourceHealthRankResponse> selectResourceHealthRank();
}
