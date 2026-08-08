package com.opssentinel.incident.service;

import com.opssentinel.audit.Auditable;
import com.opssentinel.common.exception.ConflictException;
import com.opssentinel.incident.entity.Incident;
import com.opssentinel.incident.entity.IncidentStatus;
import com.opssentinel.incident.repository.IncidentRepository;
import com.opssentinel.metric.entity.MetricSnapshot;
import com.opssentinel.resource.repository.ResourceRepository;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.orm.jpa.JpaSystemException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 규칙엔진(IncidentRuleEngine) 판정 결과로 Incident를 자동 생성하되, 동일 resourceId에
 * DETECTED/ANALYZING(=OPEN) 사건이 이미 있으면 새로 만들지 않는다(US-004 동시성 제어).
 *
 * <p><b>왜 비관적 락인가:</b> "OPEN 사건이 있는지 조회 → 없으면 새로 insert"는 서로 다른
 * 두 row를 두고 벌어지는 check-then-act라, Incident.version(@Version) 낙관적 락만으로는
 * 막을 수 없다 — 버전 필드는 "같은 row"를 동시에 갱신할 때만 충돌을 감지하지, 두 스레드가
 * 동시에 각자 새 Incident row를 insert하는 것은 애초에 막지 못한다. 또한 H2 2.2는 부분
 * unique 인덱스(WHERE 조건절)를 지원하지 않아(build.gradle 의존성 실측 확인 완료) DB
 * 제약(resourceId+status=OPEN)만으로 해결하기도 어렵다. 대신 이미 존재하는 Resource 행을
 * 동일 resourceId에 대한 "락 대상"으로 재사용해 SELECT ... FOR UPDATE로 조회→생성 임계구간
 * 자체를 직렬화한다 — 사건 생성은 충돌 빈도가 낮고(리소스 하나가 짧은 시간에 여러 번
 * 동시에 이상탐지를 트리거하는 경우는 드묾) 임계구간이 매우 짧아 락 대기비용이 무시할
 * 수준이라 실무적으로 합리적인 선택이다. 락 대기 타임아웃(H2 LOCK_TIMEOUT) 등 예외
 * 상황에 대비해 최대 {@value #MAX_RETRIES}회 재시도한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IncidentDetectionService {

    private static final int MAX_RETRIES = 3;
    private static final List<IncidentStatus> OPEN_STATUSES =
            List.of(IncidentStatus.DETECTED, IncidentStatus.ANALYZING);

    private final IncidentRuleEngine ruleEngine;
    private final IncidentActionService incidentActionService;
    private final IncidentRepository incidentRepository;
    private final ResourceRepository resourceRepository;
    private final PlatformTransactionManager transactionManager;

    /**
     * 지표 스냅샷을 규칙엔진으로 판정하고, 이상 감지 시 Incident를 생성(또는 이미 열린
     * 사건이 있으면 그것을 반환)한다. 정상 지표면 Optional.empty().
     */
    @Auditable(action = "INCIDENT_DETECT", targetType = "Incident")
    public Optional<Incident> detectAndCreate(MetricSnapshot snapshot) {
        return ruleEngine.evaluate(snapshot)
                .map(evaluation -> createIfAbsentWithRetry(snapshot.getResourceId(), evaluation));
    }

    private Incident createIfAbsentWithRetry(Long resourceId, RuleEvaluation evaluation) {
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                return transactionTemplate.execute(status -> createIfAbsent(resourceId, evaluation));
            } catch (PessimisticLockingFailureException | OptimisticLockingFailureException
                    | DataIntegrityViolationException | JpaSystemException | TransactionSystemException
                    | CannotCreateTransactionException e) {
                // JpaSystemException/TransactionSystemException: H2가 락 타임아웃(HYT00)을
                // 던지면 HikariCP가 그 커넥션을 커넥션 레벨 오류로 보고 폐기하는데,
                // TransactionTemplate이 롤백을 시도하는 시점엔 이미 그 커넥션이 닫혀 있어
                // "Unable to rollback against JDBC Connection"(원인: Connection is closed)
                // 으로 원래 락 타임아웃 예외가 감싸져 버린다(architect 콜드스타트 채점 실측:
                // 40건 동시요청 중 29건 500, AuditLog 0건 기록).
                // CannotCreateTransactionException: 위 두 타입만 재시도 대상에 추가하고 직접
                // 40건 동시요청으로 재검증하는 과정에서 추가로 실측된 동일 계열 증상 —
                // 커넥션이 대거 폐기되는 순간 재시도가 다음 트랜잭션을 시작하려 해도
                // "JDBC begin transaction failed: Connection is closed"로 트랜잭션 자체를
                // 열지 못하는 경우가 있다. 근본 원인(HikariCP 커넥션 폐기 도미노)이 같으므로
                // 함께 재시도 대상에 포함하지 않으면 여전히 500으로 샌다.
                log.warn("Incident 생성 충돌 감지(resourceId={}, {}/{}번째 시도) - 재시도합니다: {}",
                        resourceId, attempt, MAX_RETRIES, e.getMessage());
            }
        }
        return findExistingOpenIncidentOrConflict(resourceId);
    }

    /**
     * 재시도 루프를 다 소진한 뒤의 최종 폴백 조회. 이 조회 자체도 위와 같은 커넥션 폐기
     * 도미노 한복판에서 실행될 수 있으므로(직접 재검증 중 실측: 재시도 3회가 전부 커넥션
     * 오류로 실패한 직후 이 조회마저 JpaSystemException을 던져 결국 500으로 새는 사례가
     * 있었다) 이 조회도 같은 예외 타입에 한해 별도로 감싸 ConflictException(409)으로
     * 수렴시킨다 — 여기서는 더 재시도하지 않는다(이미 MAX_RETRIES회 시도했으므로).
     */
    private Incident findExistingOpenIncidentOrConflict(Long resourceId) {
        try {
            return incidentRepository.findFirstByResourceIdAndStatusIn(resourceId, OPEN_STATUSES)
                    .orElseThrow(() -> new ConflictException(
                            MAX_RETRIES + "회 재시도했지만 resourceId=" + resourceId + " Incident 생성/조회에 실패했습니다(동시성 충돌)"));
        } catch (PessimisticLockingFailureException | OptimisticLockingFailureException
                | DataIntegrityViolationException | JpaSystemException | TransactionSystemException
                | CannotCreateTransactionException e) {
            throw new ConflictException(MAX_RETRIES + "회 재시도했지만 resourceId=" + resourceId
                    + " Incident 생성/조회에 실패했습니다(동시성 충돌, 최종 폴백 조회도 커넥션 오류: "
                    + e.getMessage() + ")");
        }
    }

    /** 반드시 트랜잭션 안에서 호출돼야 한다 — 락은 트랜잭션 종료 시 해제된다. */
    private Incident createIfAbsent(Long resourceId, RuleEvaluation evaluation) {
        resourceRepository.lockById(resourceId)
                .orElseThrow(() -> new IllegalStateException("Resource not found: " + resourceId));

        return incidentRepository.findFirstByResourceIdAndStatusIn(resourceId, OPEN_STATUSES)
                .map(existing -> {
                    log.debug("resourceId={}에 이미 OPEN 사건(id={})이 있어 새로 생성하지 않음(rule={})",
                            resourceId, existing.getId(), evaluation.ruleTriggered());
                    return existing;
                })
                .orElseGet(() -> {
                    Incident incident = Incident.builder()
                            .resourceId(resourceId)
                            .severity(evaluation.severity())
                            .status(IncidentStatus.DETECTED)
                            .ruleTriggered(evaluation.ruleTriggered())
                            .build();
                    Incident saved = incidentRepository.save(incident);
                    log.info("Incident 생성: id={}, resourceId={}, rule={}, severity={}",
                            saved.getId(), resourceId, evaluation.ruleTriggered(), evaluation.severity());
                    // 새로 생성된 Incident에 한해서만 조치를 결정·기록한다(US-005) — 이미
                    // OPEN 사건이 있어 재사용하는 경우는 위 map 분기에서 처리돼 여기 들어오지
                    // 않으며, 그 사건은 이미 최초 감지 시점에 조치가 기록돼 있을 것이다.
                    incidentActionService.decideAndRecord(saved);
                    return saved;
                });
    }
}
