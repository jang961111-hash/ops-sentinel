package com.opssentinel.incident.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.opssentinel.incident.entity.ActionType;
import com.opssentinel.incident.entity.Incident;
import com.opssentinel.incident.entity.IncidentAction;
import com.opssentinel.incident.entity.IncidentStatus;
import com.opssentinel.incident.repository.IncidentActionRepository;
import com.opssentinel.incident.repository.IncidentRepository;
import com.opssentinel.metric.service.MetricService;
import com.opssentinel.metric.web.dto.SimulateMetricRequest;
import com.opssentinel.resource.entity.Resource;
import com.opssentinel.resource.entity.ResourceType;
import com.opssentinel.resource.repository.ResourceRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * US-005 검증: severity(+ruleTriggered)별로 다른 IncidentAction 조합이 자동 기록되고,
 * Incident.status가 DETECTED → ANALYZING으로 전이되는지 확인한다.
 *
 * <p>{@code /api/metrics/simulate}가 그대로 위임하는 {@link MetricService#simulate}를
 * 통해 실제 컨트롤러 호출과 동일한 경로(스냅샷 저장 → 규칙엔진 판정 → Incident 생성 →
 * 조치 결정·기록)를 그대로 재현한다.
 */
@SpringBootTest
class IncidentActionServiceTest {

    @Autowired
    private MetricService metricService;

    @Autowired
    private ResourceRepository resourceRepository;

    @Autowired
    private IncidentRepository incidentRepository;

    @Autowired
    private IncidentActionRepository incidentActionRepository;

    @Test
    void MEDIUM_사건은_MONITOR와_ALERT_조치가_기록되고_ANALYZING으로_전이된다() {
        Resource resource = resourceRepository.save(Resource.builder()
                .name("medium-test-server")
                .type(ResourceType.SERVER)
                .build());

        // cpuUsage=94 -> bandUsageSeverity 93~96 구간 -> MEDIUM
        // 나머지 필드도 명시적으로 안전값을 채운다 — null로 두면 MetricService의 랜덤 채움 로직이
        // 10% 확률로 이상치를 섞어 의도치 않게 더 심각한 규칙(예: ERROR_RATE_EXCEEDED)이 같이
        // 트리거될 수 있어(IncidentRuleEngine은 더 심각한 규칙을 채택) 테스트가 간헐적으로 실패했다.
        metricService.simulate(new SimulateMetricRequest(resource.getId(), 94.0, 0.0, 0.0, 0));

        Incident incident = findOpenIncident(resource.getId());

        assertThat(incident.getStatus()).isEqualTo(IncidentStatus.ANALYZING);
        assertThat(actionTypesOf(incident)).containsExactlyInAnyOrder(ActionType.MONITOR, ActionType.ALERT);
    }

    @Test
    void CRITICAL_사건은_ESCALATE와_ALERT_조치가_기록된다() {
        Resource resource = resourceRepository.save(Resource.builder()
                .name("critical-test-server")
                .type(ResourceType.SERVER)
                .build());

        // errorRate=25 -> CRITICAL (나머지 필드는 랜덤 이상치 혼입 방지를 위해 안전값 명시)
        metricService.simulate(new SimulateMetricRequest(resource.getId(), 0.0, 0.0, 25.0, 0));

        Incident incident = findOpenIncident(resource.getId());

        assertThat(incident.getStatus()).isEqualTo(IncidentStatus.ANALYZING);
        assertThat(actionTypesOf(incident)).containsExactlyInAnyOrder(ActionType.ESCALATE, ActionType.ALERT);
    }

    @Test
    void HIGH_리소스압박형_사건은_RESTART가_권고된다() {
        Resource resource = resourceRepository.save(Resource.builder()
                .name("high-cpu-test-server")
                .type(ResourceType.SERVER)
                .build());

        // cpuUsage=97 -> bandUsageSeverity 96~99 구간 -> HIGH, CPU_EXCEEDED는 재시작 유력 규칙
        // (나머지 필드는 랜덤 이상치 혼입 방지를 위해 안전값 명시)
        metricService.simulate(new SimulateMetricRequest(resource.getId(), 97.0, 0.0, 0.0, 0));

        Incident incident = findOpenIncident(resource.getId());

        assertThat(actionTypesOf(incident)).containsExactlyInAnyOrder(ActionType.ALERT, ActionType.RESTART);
    }

    @Test
    void HIGH_에러율_사건은_BACKUP이_권고된다() {
        Resource resource = resourceRepository.save(Resource.builder()
                .name("high-error-test-server")
                .type(ResourceType.SERVER)
                .build());

        // errorRate=15 -> HIGH, ERROR_RATE_EXCEEDED는 재시작 비적합 규칙
        // (나머지 필드는 랜덤 이상치 혼입 방지를 위해 안전값 명시)
        metricService.simulate(new SimulateMetricRequest(resource.getId(), 0.0, 0.0, 15.0, 0));

        Incident incident = findOpenIncident(resource.getId());

        assertThat(actionTypesOf(incident)).containsExactlyInAnyOrder(ActionType.ALERT, ActionType.BACKUP);
    }

    private Incident findOpenIncident(Long resourceId) {
        return incidentRepository.findAll().stream()
                .filter(incident -> incident.getResourceId().equals(resourceId))
                .findFirst()
                .orElseThrow(() -> new AssertionError("resourceId=" + resourceId + "에 대한 Incident가 생성되지 않았음"));
    }

    /** incident.getActions()는 지연로딩이라 트랜잭션 밖(테스트 메서드)에서 접근하면
     * LazyInitializationException이 나므로, 대신 IncidentActionRepository로 직접 조회한다. */
    private List<ActionType> actionTypesOf(Incident incident) {
        return incidentActionRepository.findAll().stream()
                .filter(action -> action.getIncident().getId().equals(incident.getId()))
                .map(IncidentAction::getActionType)
                .toList();
    }
}
