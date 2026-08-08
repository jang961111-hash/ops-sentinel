package com.opssentinel.audit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 이 애노테이션이 붙은 메서드 호출은 {@code AuditLogAspect}가 가로채 성공/실패 여부와
 * 함께 별도 트랜잭션으로 AuditLog에 100% 기록한다(PRD 3장-4번).
 *
 * <p><b>반드시 스프링 빈의 public 메서드에만 붙여야 한다.</b> Spring AOP는 프록시
 * 기반이라 private 메서드나 같은 빈 내부의 self-invocation(예: 같은 클래스 안에서
 * {@code this.otherMethod()} 호출)은 프록시를 거치지 않으므로 가로채지 못한다. private
 * 메서드에 이 애노테이션을 붙여도 조용히 무시될 뿐 아무 효과가 없다.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Auditable {

    /** 감사 주체. 기본값은 시스템이 자동으로 수행하는 처리를 의미하는 "SYSTEM_AGENT". */
    String actorType() default "SYSTEM_AGENT";

    /** 수행한 행위를 나타내는 식별자 (예: "METRIC_SIMULATE", "INCIDENT_DETECT"). */
    String action();

    /** 감사 대상 엔티티 타입 (예: "MetricSnapshot", "Incident"). */
    String targetType();
}
