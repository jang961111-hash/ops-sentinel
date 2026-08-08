package com.opssentinel.incident.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.opssentinel.incident.entity.IncidentSeverity;
import com.opssentinel.metric.entity.MetricSnapshot;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class IncidentRuleEngineTest {

    private final IncidentRuleEngine ruleEngine = new IncidentRuleEngine();

    @Test
    void 정상범위_지표는_이상탐지되지_않는다() {
        MetricSnapshot normal = MetricSnapshot.builder()
                .resourceId(1L)
                .cpuUsage(50.0)
                .memUsage(40.0)
                .errorRate(1.0)
                .queueDepth(5)
                .build();

        Optional<RuleEvaluation> result = ruleEngine.evaluate(normal);

        assertThat(result).isEmpty();
    }

    @Test
    void cpu_임계치_초과시_CPU_EXCEEDED_규칙이_트리거된다() {
        MetricSnapshot anomaly = MetricSnapshot.builder()
                .resourceId(1L)
                .cpuUsage(95.0)
                .memUsage(30.0)
                .errorRate(1.0)
                .queueDepth(5)
                .build();

        Optional<RuleEvaluation> result = ruleEngine.evaluate(anomaly);

        assertThat(result).isPresent();
        assertThat(result.get().ruleTriggered()).isEqualTo("CPU_EXCEEDED");
        assertThat(result.get().severity()).isEqualTo(IncidentSeverity.MEDIUM);
    }

    @Test
    void 여러_규칙이_동시에_트리거되면_가장_심각한_규칙_하나만_채택한다() {
        // errorRate=25(CRITICAL) vs cpuUsage=91(LOW) 동시 초과 → errorRate가 채택돼야 함
        MetricSnapshot anomaly = MetricSnapshot.builder()
                .resourceId(1L)
                .cpuUsage(91.0)
                .memUsage(30.0)
                .errorRate(25.0)
                .queueDepth(5)
                .build();

        Optional<RuleEvaluation> result = ruleEngine.evaluate(anomaly);

        assertThat(result).isPresent();
        assertThat(result.get().ruleTriggered()).isEqualTo("ERROR_RATE_EXCEEDED");
        assertThat(result.get().severity()).isEqualTo(IncidentSeverity.CRITICAL);
    }

    @Test
    void 큐_길이_임계치_초과시_QUEUE_DEPTH_EXCEEDED_규칙이_트리거된다() {
        MetricSnapshot anomaly = MetricSnapshot.builder()
                .resourceId(1L)
                .cpuUsage(10.0)
                .memUsage(10.0)
                .errorRate(1.0)
                .queueDepth(80)
                .build();

        Optional<RuleEvaluation> result = ruleEngine.evaluate(anomaly);

        assertThat(result).isPresent();
        assertThat(result.get().ruleTriggered()).isEqualTo("QUEUE_DEPTH_EXCEEDED");
        assertThat(result.get().severity()).isEqualTo(IncidentSeverity.MEDIUM);
    }
}
