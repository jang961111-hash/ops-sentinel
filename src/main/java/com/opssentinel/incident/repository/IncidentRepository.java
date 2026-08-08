package com.opssentinel.incident.repository;

import com.opssentinel.incident.entity.Incident;
import com.opssentinel.incident.entity.IncidentSeverity;
import com.opssentinel.incident.entity.IncidentStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface IncidentRepository extends JpaRepository<Incident, Long> {

    /**
     * 동일 resourceId에 대해 주어진 상태(보통 DETECTED/ANALYZING=OPEN) 중 하나인 사건이
     * 이미 있는지 조회한다. US-004 동시성 제어(중복 사건 방지)의 판단 기준.
     */
    Optional<Incident> findFirstByResourceIdAndStatusIn(Long resourceId, List<IncidentStatus> statuses);

    /** status/severity/resourceId 모두 선택 항목 필터로 사건 목록을 페이지 조회한다(GET /api/incidents). */
    @Query("SELECT i FROM Incident i WHERE "
            + "(:status IS NULL OR i.status = :status) "
            + "AND (:severity IS NULL OR i.severity = :severity) "
            + "AND (:resourceId IS NULL OR i.resourceId = :resourceId)")
    Page<Incident> search(
            @Param("status") IncidentStatus status,
            @Param("severity") IncidentSeverity severity,
            @Param("resourceId") Long resourceId,
            Pageable pageable);

    /**
     * 사건 상세 조회용 — 조치이력(actions)을 한 번의 쿼리로 즉시 로딩한다.
     * 트랜잭션 경계 밖에서도 LazyInitializationException 없이 actions에 접근할 수 있어야
     * 해서(컨트롤러가 DTO로 변환) fetch join을 사용한다.
     */
    @Query("SELECT i FROM Incident i LEFT JOIN FETCH i.actions WHERE i.id = :id")
    Optional<Incident> findByIdWithActions(@Param("id") Long id);

    /**
     * US-010 Actuator 헬스체크(IncidentEngineHealthIndicator)용 — 주어진 시각 이후 감지되고
     * 아직 특정 상태(RESOLVED)가 아닌 특정 심각도(CRITICAL) 사건 건수를 센다. 존재 여부
     * 판정(DOWN/UP)과 상세정보(건수)를 한 번의 쿼리로 모두 구할 수 있어 count 하나로 충분하다.
     */
    long countBySeverityAndStatusNotAndDetectedAtAfter(
            IncidentSeverity severity, IncidentStatus status, LocalDateTime detectedAtAfter);
}
