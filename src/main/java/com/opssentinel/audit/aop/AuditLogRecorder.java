package com.opssentinel.audit.aop;

import com.opssentinel.audit.entity.AuditLog;
import com.opssentinel.audit.entity.ResultStatus;
import com.opssentinel.audit.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 감사로그를 본 비즈니스 트랜잭션과 완전히 독립된 트랜잭션으로 저장한다.
 *
 * <p><b>왜 REQUIRES_NEW로 분리했는가:</b> {@code AuditLogAspect}가 감싸는 비즈니스
 * 메서드(예: {@code IncidentDetectionService.detectAndCreate})가 예외를 던져 자신의
 * 트랜잭션을 롤백하더라도 "이 시도가 실패했다"는 사실은 감사 기록으로 반드시 남아야
 * 한다(PRD 3장-4번: "사건 처리 로직 자체가 실패해도 감사로그는 남는다"). 만약 이 저장이
 * 같은 트랜잭션(PROPAGATION_REQUIRED)으로 참여한다면 비즈니스 로직 롤백 시 AuditLog
 * INSERT도 함께 롤백되어 사라진다 — 그래서 REQUIRES_NEW로 물리적으로 별도의 트랜잭션을
 * 열어 즉시 커밋되게 한다.
 *
 * <p>이 메서드들을 Aspect(어드바이스) 클래스가 아니라 별도의 스프링 빈에 둔 이유:
 * {@code @Transactional}은 프록시를 통한 외부 호출에만 적용되는 AOP 기반 기능인데,
 * Aspect 클래스 자체에 걸면 어드바이스 로직 내부에서의 호출이 되어 트랜잭션 프록시를
 * 거치지 않는 self-invocation과 동일한 문제가 생긴다. 별도 빈으로 분리해 이
 * {@code AuditLogAspect} → {@code AuditLogRecorder}(다른 빈) 호출이 프록시를 정상적으로
 * 거치게 만들어야 REQUIRES_NEW가 실제로 동작한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuditLogRecorder {

    private final AuditLogRepository auditLogRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordSuccess(String actorType, String action, String targetType, Long targetId,
            String requestSummary) {
        save(actorType, action, targetType, targetId, requestSummary, ResultStatus.SUCCESS, null);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(String actorType, String action, String targetType, Long targetId,
            String requestSummary, String errorMessage) {
        save(actorType, action, targetType, targetId, requestSummary, ResultStatus.FAIL, errorMessage);
    }

    /**
     * 감사로그 저장 자체가 실패하더라도(예: DB 일시 장애) 원래 비즈니스 흐름을 막아서는
     * 안 되므로 여기서 예외를 흡수하고 로그만 남긴다.
     */
    private void save(String actorType, String action, String targetType, Long targetId, String requestSummary,
            ResultStatus resultStatus, String errorMessage) {
        try {
            AuditLog auditLog = AuditLog.builder()
                    .actorType(actorType)
                    .action(action)
                    .targetType(targetType)
                    .targetId(targetId)
                    .requestSummary(requestSummary)
                    .resultStatus(resultStatus)
                    .errorMessage(errorMessage)
                    .build();
            auditLogRepository.save(auditLog);
        } catch (Exception e) {
            log.error("AuditLog 저장 실패: actorType={}, action={}, targetType={}, targetId={}",
                    actorType, action, targetType, targetId, e);
        }
    }
}
