package com.opssentinel.audit.aop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;

import com.opssentinel.audit.entity.AuditLog;
import com.opssentinel.audit.entity.ResultStatus;
import com.opssentinel.audit.repository.AuditLogRepository;
import com.opssentinel.incident.entity.IncidentStatus;
import com.opssentinel.incident.repository.IncidentRepository;
import com.opssentinel.metric.service.MetricService;
import com.opssentinel.metric.web.dto.SimulateMetricRequest;
import com.opssentinel.resource.entity.Resource;
import com.opssentinel.resource.entity.ResourceType;
import com.opssentinel.resource.repository.ResourceRepository;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 락 트랜잭션 안의 감사 저장(INCIDENT_ACTION_DECIDE)이 DB 오류로 실패하면 그 트랜잭션은
 * rollback-only가 되고 커밋 시 UnexpectedRollbackException이 난다. 감사 실패가 사건 생성을
 * 500으로 막으면 안 된다 — 재시도로 흡수돼야 한다.
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:audit-fail-in-tx;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000")
class AuditFailureInTransactionTest {

    @SpyBean
    private AuditLogRecorder auditLogRecorder;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private MetricService metricService;

    @Autowired
    private ResourceRepository resourceRepository;

    @Autowired
    private IncidentRepository incidentRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void 트랜잭션_안_감사_저장이_한번_실패해도_사건_생성은_재시도로_성공한다() {
        AtomicBoolean failedOnce = new AtomicBoolean();
        // 스텁 설정 호출도 트랜잭션 프록시(MANDATORY)를 거치므로 트랜잭션 안에서 건다
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> doAnswer(invocation -> {
            if (failedOnce.compareAndSet(false, true)) {
                // 실제 DB 제약 위반(action NOT NULL)으로 현재 트랜잭션을 rollback-only로 만든다
                try {
                    auditLogRepository.save(AuditLog.builder().actorType("SYSTEM_AGENT")
                            .resultStatus(ResultStatus.SUCCESS).build());
                } catch (RuntimeException expected) {
                    // AuditLogRecorder.save와 같이 예외를 삼킨다
                }
                return null;
            }
            return invocation.callRealMethod();
        }).when(auditLogRecorder).recordSuccessInCurrentTransaction(anyString(), anyString(), anyString(), any(), anyString()));

        Resource resource = resourceRepository.save(Resource.builder().name("audit-fail-in-tx").type(ResourceType.SERVER).build());

        metricService.simulate(new SimulateMetricRequest(resource.getId(), 95.0, 0.0, 0.0, 0));

        assertThat(failedOnce).isTrue();
        assertThat(incidentRepository.findFirstByResourceIdAndStatusIn(resource.getId(),
                java.util.List.of(IncidentStatus.DETECTED, IncidentStatus.ANALYZING))).isPresent();
    }
}
