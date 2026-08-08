package com.opssentinel.audit.aop;

import static org.assertj.core.api.Assertions.assertThat;

import com.opssentinel.audit.entity.AuditLog;
import com.opssentinel.audit.entity.ResultStatus;
import com.opssentinel.audit.repository.AuditLogRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * US-006 완료 기준 4번 검증: {@link AuditLogRecorder}의 REQUIRES_NEW 트랜잭션이
 * 바깥 트랜잭션과 물리적으로 분리되어, 바깥 트랜잭션이 롤백되어도 커밋된 채로 남는지
 * 직접 증명한다.
 *
 * <p>증명 방법: 바깥 트랜잭션(PROPAGATION_REQUIRED)을 열고 그 안에서
 * {@code AuditLogRecorder.recordSuccess}를 호출한 뒤, 바깥 트랜잭션을
 * {@code setRollbackOnly()}로 강제 롤백시킨다. REQUIRES_NEW가 실제로 물리적 트랜잭션을
 * 분리했다면 바깥 트랜잭션 롤백과 무관하게 AuditLog는 이미 커밋되어 있어야 한다. 만약
 * REQUIRES_NEW 대신 기본 전파(REQUIRED)였다면 같은 물리 트랜잭션에 참여해 바깥 롤백과
 * 함께 AuditLog INSERT도 사라졌을 것이다.
 */
@SpringBootTest
class AuditLogRecorderTest {

    @Autowired
    private AuditLogRecorder auditLogRecorder;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void REQUIRES_NEW로_저장된_감사로그는_바깥_트랜잭션이_롤백돼도_커밋된_채로_남는다() {
        String marker = "requires-new-proof-" + System.nanoTime();
        TransactionTemplate outerTransaction = new TransactionTemplate(transactionManager);

        outerTransaction.executeWithoutResult(status -> {
            auditLogRecorder.recordSuccess("SYSTEM_AGENT", "PROOF_ACTION", "ProofTarget", 1L, marker);
            // 바깥 트랜잭션 자체를 강제로 롤백시킨다 — 본 비즈니스 로직이 실패한 상황을 재현.
            status.setRollbackOnly();
        });

        // 바깥 트랜잭션은 롤백됐지만, REQUIRES_NEW로 저장된 AuditLog는 이미 별도로
        // 커밋되어 있어야 한다. 새로운(트랜잭션 밖) 조회로 확인한다.
        List<AuditLog> found = auditLogRepository.findAll().stream()
                .filter(log -> marker.equals(log.getRequestSummary()))
                .toList();

        assertThat(found).hasSize(1);
        assertThat(found.get(0).getResultStatus()).isEqualTo(ResultStatus.SUCCESS);
        assertThat(found.get(0).getAction()).isEqualTo("PROOF_ACTION");
    }
}
