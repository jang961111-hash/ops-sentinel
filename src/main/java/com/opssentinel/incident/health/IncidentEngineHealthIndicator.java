package com.opssentinel.incident.health;

import com.opssentinel.incident.entity.IncidentSeverity;
import com.opssentinel.incident.entity.IncidentStatus;
import com.opssentinel.incident.repository.IncidentRepository;
import java.time.Duration;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Actuator 커스텀 헬스 인디케이터(US-010). Spring Boot 컨벤션에 따라 클래스명
 * {@code IncidentEngineHealthIndicator}의 "HealthIndicator" 접미사가 빠지고 앞글자가
 * 소문자화된 {@code incidentEngine}이 빈 이름/엔드포인트명으로 자동 등록되어
 * {@code GET /actuator/health/incidentEngine}으로 노출된다.
 *
 * <p>최근 5분 이내 감지되고 아직 해결(RESOLVED)되지 않은 CRITICAL Incident가 1건이라도
 * 있으면 규칙엔진이 심각한 장애를 처리하지 못하고 있다고 보고 DOWN, 없으면 UP으로
 * 판정한다.
 */
@Component
@RequiredArgsConstructor
public class IncidentEngineHealthIndicator implements HealthIndicator {

    private static final Duration RECENT_WINDOW = Duration.ofMinutes(5);

    private final IncidentRepository incidentRepository;

    @Override
    public Health health() {
        long unresolvedCriticalCount = incidentRepository.countBySeverityAndStatusNotAndDetectedAtAfter(
                IncidentSeverity.CRITICAL, IncidentStatus.RESOLVED, LocalDateTime.now().minus(RECENT_WINDOW));

        Health.Builder builder = unresolvedCriticalCount > 0 ? Health.down() : Health.up();
        return builder.withDetail("unresolvedCriticalCount", unresolvedCriticalCount).build();
    }
}
