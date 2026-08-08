package com.opssentinel.audit.aop;

import com.opssentinel.audit.Auditable;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

/**
 * {@link Auditable}이 붙은 public 메서드 호출을 가로채 성공/실패 여부를 AuditLog에
 * 100% 기록한다(PRD 3장-4번).
 *
 * <p><b>왜 public 메서드 경계에만 거는가:</b> Spring AOP는 CGLIB/JDK 동적 프록시
 * 기반이라, 프록시를 거치지 않는 호출(private 메서드, 같은 빈 내부의 self-invocation)은
 * 가로채지 못한다. 그래서 {@link Auditable}은 항상 스프링 빈의 public 메서드(외부에서
 * 프록시를 통해 호출되는 진입점)에만 붙이도록 정의했고, 이 Aspect도 그 전제를 그대로
 * 따른다 — 실제로 대상이 된 세 메서드(MetricService.simulate,
 * IncidentDetectionService.detectAndCreate, IncidentActionService.decideAndRecord)는
 * 모두 서로 다른 빈에서 주입받은 참조를 통해 호출되므로 self-invocation 문제가 없다.
 *
 * <p>이 클래스 자체에는 {@code @Transactional}을 걸지 않는다 — 감사로그 저장은 별도
 * 빈인 {@link AuditLogRecorder}의 REQUIRES_NEW 트랜잭션 메서드에 위임한다.
 */
@Aspect
@Component
@RequiredArgsConstructor
public class AuditLogAspect {

    private static final int SUMMARY_MAX_LENGTH = 1000;

    private final AuditLogRecorder auditLogRecorder;

    @Around("@annotation(auditable)")
    public Object audit(ProceedingJoinPoint joinPoint, Auditable auditable) throws Throwable {
        Object[] args = joinPoint.getArgs();
        String requestSummary = summarize(args);

        try {
            Object result = joinPoint.proceed();
            Long targetId = extractTargetId(result, args);
            auditLogRecorder.recordSuccess(
                    auditable.actorType(), auditable.action(), auditable.targetType(), targetId, requestSummary);
            return result;
        } catch (Throwable ex) {
            Long targetId = extractTargetId(null, args);
            auditLogRecorder.recordFailure(auditable.actorType(), auditable.action(), auditable.targetType(),
                    targetId, requestSummary, ex.getMessage());
            // 감사로그 기록이 원래 흐름을 막으면 안 되므로 원래 예외를 그대로 다시 던진다.
            throw ex;
        }
    }

    private String summarize(Object[] args) {
        if (args == null || args.length == 0) {
            return "";
        }
        String summary = Arrays.toString(args);
        return summary.length() > SUMMARY_MAX_LENGTH ? summary.substring(0, SUMMARY_MAX_LENGTH) : summary;
    }

    /**
     * targetId 추출: 먼저 반환값(Optional로 감싸져 있으면 벗겨서)에서 getId()를
     * 시도하고, 실패하면(예외 발생 시 반환값 없음, void 메서드, id 없는 타입) 메서드
     * 인자 중 getId()를 가진 첫 대상에서 시도한다. 예: void를 반환하는
     * IncidentActionService.decideAndRecord(Incident)는 인자로 받은 Incident에서
     * targetId를 뽑아야 한다.
     */
    private Long extractTargetId(Object result, Object[] args) {
        Long id = tryGetId(unwrapOptional(result));
        if (id != null) {
            return id;
        }
        if (args != null) {
            for (Object arg : args) {
                id = tryGetId(arg);
                if (id != null) {
                    return id;
                }
            }
        }
        return null;
    }

    private Object unwrapOptional(Object value) {
        return value instanceof Optional<?> optional ? optional.orElse(null) : value;
    }

    private Long tryGetId(Object target) {
        if (target == null) {
            return null;
        }
        try {
            Method getId = target.getClass().getMethod("getId");
            Object id = getId.invoke(target);
            return id instanceof Long longId ? longId : null;
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }
}
