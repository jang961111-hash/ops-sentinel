package com.opssentinel.incident.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.opssentinel.incident.entity.Incident;
import com.opssentinel.incident.entity.IncidentSeverity;
import com.opssentinel.incident.entity.IncidentStatus;
import com.opssentinel.incident.repository.IncidentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * M1 검증: {@link IncidentQueryService#resolve}가 멱등하게 동작하는지 확인한다 — 이미
 * RESOLVED인 사건을 다시 resolve해도 최초 resolvedAt(해결 시각)을 재설정하지 않아야
 * {@code AnalyticsMapper}의 평균 해결시간(MTTR) 집계가 재시도/중복 호출로 왜곡되지 않는다.
 */
@SpringBootTest
class IncidentQueryServiceTest {

    @Autowired
    private IncidentQueryService incidentQueryService;

    @Autowired
    private IncidentRepository incidentRepository;

    @Test
    void 이미_RESOLVED인_사건을_다시_resolve해도_resolvedAt이_바뀌지_않는다() throws InterruptedException {
        Incident incident = incidentRepository.save(Incident.builder()
                .resourceId(1L)
                .severity(IncidentSeverity.LOW)
                .status(IncidentStatus.DETECTED)
                .ruleTriggered("TEST_RULE")
                .build());

        Incident firstResolve = incidentQueryService.resolve(incident.getId());
        assertThat(firstResolve.getStatus()).isEqualTo(IncidentStatus.RESOLVED);
        assertThat(firstResolve.getResolvedAt()).isNotNull();

        // 두 번째 resolve 호출과 시각 차이를 명확히 두기 위해 짧게 대기한다.
        Thread.sleep(10);

        Incident secondResolve = incidentQueryService.resolve(incident.getId());
        assertThat(secondResolve.getStatus()).isEqualTo(IncidentStatus.RESOLVED);
        assertThat(secondResolve.getResolvedAt()).isEqualTo(firstResolve.getResolvedAt());
    }
}
