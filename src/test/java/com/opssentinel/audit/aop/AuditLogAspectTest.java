package com.opssentinel.audit.aop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.opssentinel.audit.entity.AuditLog;
import com.opssentinel.audit.entity.ResultStatus;
import com.opssentinel.audit.repository.AuditLogRepository;
import com.opssentinel.metric.entity.MetricSnapshot;
import com.opssentinel.metric.service.MetricService;
import com.opssentinel.metric.web.dto.SimulateMetricRequest;
import com.opssentinel.resource.entity.Resource;
import com.opssentinel.resource.entity.ResourceType;
import com.opssentinel.resource.repository.ResourceRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.server.ResponseStatusException;

/**
 * US-006 검증: {@code @Auditable}이 붙은 {@link MetricService#simulate}(그리고 그것이
 * 내부에서 위임하는 IncidentDetectionService/IncidentActionService)를 실제로 호출해
 * AOP가 성공/실패 여부와 함께 AuditLog를 100% 기록하는지 확인한다.
 *
 * <p>PRD 3장-4번 요구사항: "사건 처리 로직 자체가 실패해도 감사로그는 남는다" — 존재하지
 * 않는 resourceId로 호출해 원래 예외(404)가 정상적으로 클라이언트까지 전파되면서도
 * AuditLog에는 FAIL + errorMessage가 남는지가 이 테스트의 핵심이다.
 */
@SpringBootTest
class AuditLogAspectTest {

    @Autowired
    private MetricService metricService;

    @Autowired
    private ResourceRepository resourceRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Test
    void 정상_시뮬레이션_호출은_SUCCESS_AuditLog를_남긴다() {
        Resource resource = resourceRepository.save(Resource.builder()
                .name("audit-success-server")
                .type(ResourceType.SERVER)
                .build());

        MetricSnapshot saved = metricService.simulate(new SimulateMetricRequest(resource.getId(), 10.0, 10.0, 0.0, 1));

        List<AuditLog> logs = auditLogRepository.findAll().stream()
                .filter(log -> "METRIC_SIMULATE".equals(log.getAction()))
                .filter(log -> saved.getId().equals(log.getTargetId()))
                .toList();

        assertThat(logs).hasSize(1);
        AuditLog auditLog = logs.get(0);
        assertThat(auditLog.getResultStatus()).isEqualTo(ResultStatus.SUCCESS);
        assertThat(auditLog.getActorType()).isEqualTo("SYSTEM_AGENT");
        assertThat(auditLog.getTargetType()).isEqualTo("MetricSnapshot");
        assertThat(auditLog.getErrorMessage()).isNull();
        assertThat(auditLog.getRequestSummary()).contains(resource.getId().toString());
    }

    @Test
    void 존재하지_않는_리소스로_호출하면_원래_예외는_전파되고_FAIL_AuditLog가_남는다() {
        Long missingResourceId = -1L;
        int before = auditLogRepository.findAll().size();

        assertThatThrownBy(() -> metricService.simulate(
                new SimulateMetricRequest(missingResourceId, null, null, null, null)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Resource not found");

        List<AuditLog> logs = auditLogRepository.findAll();
        assertThat(logs).hasSizeGreaterThan(before);

        AuditLog failLog = logs.stream()
                .filter(log -> "METRIC_SIMULATE".equals(log.getAction()))
                .filter(log -> log.getResultStatus() == ResultStatus.FAIL)
                .filter(log -> log.getRequestSummary() != null && log.getRequestSummary().contains(missingResourceId.toString()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("FAIL AuditLog가 기록되지 않았음"));

        assertThat(failLog.getErrorMessage()).contains("Resource not found");
    }
}
