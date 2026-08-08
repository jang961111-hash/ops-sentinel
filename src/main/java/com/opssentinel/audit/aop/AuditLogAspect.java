package com.opssentinel.audit.aop;

import com.opssentinel.audit.Auditable;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
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
            recordSafely(() -> auditLogRecorder.recordSuccess(
                    auditable.actorType(), auditable.action(), auditable.targetType(), targetId, requestSummary));
            return result;
        } catch (Throwable ex) {
            Long targetId = extractTargetId(null, args);
            recordSafely(() -> auditLogRecorder.recordFailure(auditable.actorType(), auditable.action(),
                    auditable.targetType(), targetId, requestSummary, ex.getMessage()));
            // 감사로그 기록이 원래 흐름을 막으면 안 되므로 원래 예외를 그대로 다시 던진다.
            throw ex;
        }
    }

    /**
     * 감사로그 기록 자체의 실패(예: REQUIRES_NEW 트랜잭션이 커넥션풀 고갈 등으로 트랜잭션
     * 시작 단계에서부터 예외를 던지는 경우)가 원래 비즈니스 예외/정상 흐름을 절대 덮어쓰지
     * 못하도록 여기서 한 번 더 흡수한다.
     *
     * <p>{@link AuditLogRecorder#save}도 자체적으로 try-catch를 두고 있지만, 그건
     * REQUIRES_NEW 트랜잭션이 이미 시작된 "이후"의 실패만 잡는다. 트랜잭션 프록시가
     * 새 커넥션을 얻는 과정 자체(트랜잭션 시작 단계)에서 던져지는 예외는 메서드 본문에
     * 진입하기도 전에 발생하므로 그 try-catch로는 잡히지 않는다 — 그 예외가 여기까지
     * 전파되면 원래 비즈니스 예외의 {@code throw ex}에 도달하지 못하고 감사로그 저장
     * 실패 예외가 이를 덮어써버린다. 이 메서드가 그 마지막 방어선이다.
     */
    private void recordSafely(Runnable recordAction) {
        try {
            recordAction.run();
        } catch (Exception e) {
            log.error("AuditLog 기록 자체가 실패했습니다(원래 처리 결과에는 영향 없음): {}", e.getMessage(), e);
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
     * targetId 추출: 반환값이 {@link Optional}이면 그 값 자체를 "타겟이 무엇인지에 대한
     * 확정적인 답"으로 존중한다 — Optional.empty()는 "이 호출로 특정된 타겟이 없다"는
     * 의미있는 신호이므로, 이 경우 메서드 인자로 폴백하지 않고 targetId=null로 남긴다.
     * (예: {@code IncidentDetectionService.detectAndCreate}가 정상 지표를 판정해
     * {@code Optional.empty()}를 반환했는데, 인자로 넘어온 {@code MetricSnapshot}의 id를
     * targetId로 잘못 기록하면 감사로그가 존재하지 않는 Incident를 가리키게 된다.)
     *
     * <p>반환값이 Optional이 아니면(예: void 메서드는 result=null, 혹은 id 없는 타입) 메서드
     * 인자 중 getId()를 가진 첫 대상에서 시도한다. 예: void를 반환하는
     * IncidentActionService.decideAndRecord(Incident)는 인자로 받은 Incident에서
     * targetId를 뽑아야 한다. 예외가 발생해 반환값 자체가 없는 실패 경로(result=null 전달)도
     * 이 경로를 그대로 타 인자 폴백을 시도한다 — Optional.empty()처럼 "값이 없다"는 확정
     * 정보가 아니라 "애초에 반환되지 못했다"는 것뿐이라 인자 폴백이 여전히 최선이다.
     */
    private Long extractTargetId(Object result, Object[] args) {
        if (result instanceof Optional<?> optional) {
            return optional.map(this::tryGetId).orElse(null);
        }
        Long id = tryGetId(result);
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
