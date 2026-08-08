package com.opssentinel.incident.service;

import com.opssentinel.incident.entity.IncidentSeverity;

/**
 * 규칙엔진(IncidentRuleEngine)이 지표 스냅샷 하나를 평가한 결과.
 *
 * <p>임계치를 넘지 않았으면 이 값 자체가 만들어지지 않는다(IncidentRuleEngine#evaluate가
 * Optional.empty()를 반환). 여러 규칙이 동시에 트리거되면 그중 severity가 가장 높은 것
 * 하나만 채택해 Incident.ruleTriggered에 기록한다.
 */
public record RuleEvaluation(String ruleTriggered, IncidentSeverity severity) {
}
