package com.opssentinel.incident.service;

import com.opssentinel.incident.entity.IncidentSeverity;
import com.opssentinel.metric.entity.MetricSnapshot;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * 지표 스냅샷 1건을 받아 임계치 초과 여부와 심각도를 판정하는 단순 if-else 규칙엔진.
 *
 * <p>PRD가 "과하게 설계하지 말 것"을 명시해 전략패턴 등 확장 구조 대신 메서드 단위
 * if-else로 직접 구현한다. 판정 기준(US-003과 동일):
 * <ul>
 *   <li>errorRate >= 5 → ERROR_RATE_EXCEEDED</li>
 *   <li>cpuUsage >= 90 → CPU_EXCEEDED</li>
 *   <li>memUsage >= 90 → MEM_EXCEEDED</li>
 *   <li>queueDepth >= 50 → QUEUE_DEPTH_EXCEEDED</li>
 * </ul>
 *
 * <p>여러 규칙이 동시에 트리거되면, Incident.ruleTriggered가 단일 문자열 컬럼이라
 * 콤마 나열 대신 severity가 가장 심각한 규칙 하나만 대표로 채택한다(감사로그를
 * "무엇 때문에 사건이 열렸는지" 한 줄로 명료하게 유지하기 위함). severity가 같으면
 * errorRate > cpuUsage > memUsage > queueDepth 순으로 우선한다.
 */
@Component
public class IncidentRuleEngine {

    private static final double ERROR_RATE_THRESHOLD = 5.0;
    private static final double CPU_THRESHOLD = 90.0;
    private static final double MEM_THRESHOLD = 90.0;
    private static final int QUEUE_DEPTH_THRESHOLD = 50;

    public Optional<RuleEvaluation> evaluate(MetricSnapshot snapshot) {
        RuleEvaluation worst = null;
        worst = pickMoreSevere(worst, evaluateErrorRate(snapshot.getErrorRate()));
        worst = pickMoreSevere(worst, evaluateCpu(snapshot.getCpuUsage()));
        worst = pickMoreSevere(worst, evaluateMem(snapshot.getMemUsage()));
        worst = pickMoreSevere(worst, evaluateQueueDepth(snapshot.getQueueDepth()));
        return Optional.ofNullable(worst);
    }

    /** 에러율 5~10=MEDIUM, 10~20=HIGH, 20+=CRITICAL. 5% 자체가 이미 유의미한 장애 신호라 LOW 구간은 두지 않는다. */
    private RuleEvaluation evaluateErrorRate(double errorRate) {
        if (errorRate < ERROR_RATE_THRESHOLD) {
            return null;
        }
        IncidentSeverity severity;
        if (errorRate >= 20.0) {
            severity = IncidentSeverity.CRITICAL;
        } else if (errorRate >= 10.0) {
            severity = IncidentSeverity.HIGH;
        } else {
            severity = IncidentSeverity.MEDIUM;
        }
        return new RuleEvaluation("ERROR_RATE_EXCEEDED", severity);
    }

    private RuleEvaluation evaluateCpu(double cpuUsage) {
        if (cpuUsage < CPU_THRESHOLD) {
            return null;
        }
        return new RuleEvaluation("CPU_EXCEEDED", bandUsageSeverity(cpuUsage));
    }

    private RuleEvaluation evaluateMem(double memUsage) {
        if (memUsage < MEM_THRESHOLD) {
            return null;
        }
        return new RuleEvaluation("MEM_EXCEEDED", bandUsageSeverity(memUsage));
    }

    /** CPU/메모리는 0~100% 스케일로 고정돼 있어, 90~100% 구간을 4등분해 심각도를 매긴다. */
    private IncidentSeverity bandUsageSeverity(double usagePercent) {
        if (usagePercent >= 99.0) {
            return IncidentSeverity.CRITICAL;
        } else if (usagePercent >= 96.0) {
            return IncidentSeverity.HIGH;
        } else if (usagePercent >= 93.0) {
            return IncidentSeverity.MEDIUM;
        }
        return IncidentSeverity.LOW;
    }

    /** 큐 길이는 시뮬레이터 이상치 범위(50~200)에 맞춰 구간을 나눈다. */
    private RuleEvaluation evaluateQueueDepth(int queueDepth) {
        if (queueDepth < QUEUE_DEPTH_THRESHOLD) {
            return null;
        }
        IncidentSeverity severity;
        if (queueDepth >= 200) {
            severity = IncidentSeverity.CRITICAL;
        } else if (queueDepth >= 125) {
            severity = IncidentSeverity.HIGH;
        } else if (queueDepth >= 75) {
            severity = IncidentSeverity.MEDIUM;
        } else {
            severity = IncidentSeverity.LOW;
        }
        return new RuleEvaluation("QUEUE_DEPTH_EXCEEDED", severity);
    }

    private RuleEvaluation pickMoreSevere(RuleEvaluation current, RuleEvaluation candidate) {
        if (candidate == null) {
            return current;
        }
        if (current == null) {
            return candidate;
        }
        return candidate.severity().ordinal() > current.severity().ordinal() ? candidate : current;
    }
}
