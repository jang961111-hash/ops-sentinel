package com.opssentinel.audit.aop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.opssentinel.audit.entity.AuditLog;
import com.opssentinel.audit.entity.ResultStatus;
import com.opssentinel.audit.repository.AuditLogRepository;
import com.opssentinel.incident.entity.Incident;
import com.opssentinel.incident.entity.IncidentSeverity;
import com.opssentinel.incident.entity.IncidentStatus;
import com.opssentinel.incident.repository.IncidentRepository;
import com.opssentinel.incident.service.IncidentActionService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 트랜잭션 안에서 호출된 {@code @Auditable}의 기록 방식 검증.
 *
 * <p>성공 기록은 추가 커넥션 없이 같은 트랜잭션에 들어가므로 비즈니스 롤백과 함께 사라진다
 * (사건·조치 행과 원자적). 실패 기록은 롤백이 끝난 뒤 별도 트랜잭션으로 남는다.
 */
@SpringBootTest
class AuditInTransactionTest {

    @Autowired
    private IncidentActionService incidentActionService;

    @Autowired
    private IncidentRepository incidentRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void 트랜잭션_안_성공_감사는_비즈니스_롤백과_함께_사라진다() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        Long incidentId = tx.execute(status -> {
            Incident incident = incidentRepository.save(Incident.builder()
                    .resourceId(-100L).severity(IncidentSeverity.LOW)
                    .status(IncidentStatus.DETECTED).ruleTriggered("CPU_EXCEEDED").build());
            incidentActionService.decideAndRecord(incident);
            status.setRollbackOnly();
            return incident.getId();
        });

        assertThat(decideAudits(incidentId)).isEmpty();
        assertThat(incidentRepository.findById(incidentId)).isEmpty();
    }

    @Test
    void 트랜잭션_안_실패_감사는_롤백_후_FAIL로_남는다() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        Long[] incidentId = new Long[1];
        assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
            // severity가 없으면 조치 결정(switch)에서 예외가 난다
            Incident incident = incidentRepository.save(Incident.builder()
                    .resourceId(-101L).severity(IncidentSeverity.LOW)
                    .status(IncidentStatus.DETECTED).ruleTriggered("CPU_EXCEEDED").build());
            incidentId[0] = incident.getId();
            incident.setSeverity(null);
            incidentActionService.decideAndRecord(incident);
        })).isInstanceOf(NullPointerException.class);

        List<AuditLog> audits = decideAudits(incidentId[0]);
        assertThat(audits).hasSize(1);
        assertThat(audits.get(0).getResultStatus()).isEqualTo(ResultStatus.FAIL);
    }

    private List<AuditLog> decideAudits(Long incidentId) {
        return auditLogRepository.findAll().stream()
                .filter(a -> "INCIDENT_ACTION_DECIDE".equals(a.getAction()))
                .filter(a -> incidentId.equals(a.getTargetId()))
                .toList();
    }
}
