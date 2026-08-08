package com.opssentinel.incident.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.opssentinel.incident.entity.IncidentSeverity;
import com.opssentinel.metric.entity.MetricSnapshot;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * US-015 보강: 기존 테스트는 CPU_EXCEEDED(MEDIUM 1건), ERROR_RATE_EXCEEDED(CRITICAL,
 * 다른 규칙과의 우선순위 테스트에 종속), QUEUE_DEPTH_EXCEEDED(MEDIUM 1건)만 다뤄 4개
 * 규칙 x 4개 심각도 구간 매트릭스에 빈 칸이 많았다(MEM_EXCEEDED는 아예 테스트가 없었음).
 * 아래 파라미터화 테스트로 각 규칙의 심각도 구간 경계값을 전부 채운다.
 */
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

    // 나머지 3개 구간(LOW/HIGH/CRITICAL)이 비어있었다 — 90~100%를 4등분하는
    // bandUsageSeverity 경계값(93/96/99)을 전부 확인한다.
    @ParameterizedTest(name = "cpuUsage={0} → {1}")
    @CsvSource({
            "90.0, LOW",
            "96.0, HIGH",
            "99.0, CRITICAL"
    })
    void cpu_사용률_구간별로_나머지_심각도가_올바르게_매겨진다(double cpuUsage, IncidentSeverity expected) {
        MetricSnapshot anomaly = MetricSnapshot.builder()
                .resourceId(1L)
                .cpuUsage(cpuUsage)
                .memUsage(10.0)
                .errorRate(1.0)
                .queueDepth(5)
                .build();

        Optional<RuleEvaluation> result = ruleEngine.evaluate(anomaly);

        assertThat(result).isPresent();
        assertThat(result.get().ruleTriggered()).isEqualTo("CPU_EXCEEDED");
        assertThat(result.get().severity()).isEqualTo(expected);
    }

    // MEM_EXCEEDED는 기존 테스트가 전혀 없었다 — CPU와 동일한 90~100% 4등분 기준이므로
    // 4개 구간을 한 번에 확인한다.
    @ParameterizedTest(name = "memUsage={0} → {1}")
    @CsvSource({
            "90.0, LOW",
            "93.0, MEDIUM",
            "96.0, HIGH",
            "99.0, CRITICAL"
    })
    void mem_사용률_구간별로_심각도가_올바르게_매겨진다(double memUsage, IncidentSeverity expected) {
        MetricSnapshot anomaly = MetricSnapshot.builder()
                .resourceId(1L)
                .cpuUsage(10.0)
                .memUsage(memUsage)
                .errorRate(1.0)
                .queueDepth(5)
                .build();

        Optional<RuleEvaluation> result = ruleEngine.evaluate(anomaly);

        assertThat(result).isPresent();
        assertThat(result.get().ruleTriggered()).isEqualTo("MEM_EXCEEDED");
        assertThat(result.get().severity()).isEqualTo(expected);
    }

    // 나머지 3개 구간(LOW/HIGH/CRITICAL)이 비어있었다 — 시뮬레이터 이상치 범위(50~200)에
    // 맞춘 경계값(75/125/200)을 전부 확인한다.
    @ParameterizedTest(name = "queueDepth={0} → {1}")
    @CsvSource({
            "50, LOW",
            "125, HIGH",
            "200, CRITICAL"
    })
    void 큐_길이_구간별로_나머지_심각도가_올바르게_매겨진다(int queueDepth, IncidentSeverity expected) {
        MetricSnapshot anomaly = MetricSnapshot.builder()
                .resourceId(1L)
                .cpuUsage(10.0)
                .memUsage(10.0)
                .errorRate(1.0)
                .queueDepth(queueDepth)
                .build();

        Optional<RuleEvaluation> result = ruleEngine.evaluate(anomaly);

        assertThat(result).isPresent();
        assertThat(result.get().ruleTriggered()).isEqualTo("QUEUE_DEPTH_EXCEEDED");
        assertThat(result.get().severity()).isEqualTo(expected);
    }

    // 기존에는 CRITICAL(25.0)만, 그것도 다른 규칙과의 우선순위 테스트에 곁다리로
    // 확인됐다 — ERROR_RATE_EXCEEDED 단독으로 MEDIUM/HIGH/CRITICAL 3개 구간을 확인한다
    // (LOW 구간은 없음: 5% 미만은 미탐지, 5% 이상은 이미 MEDIUM부터 시작).
    @ParameterizedTest(name = "errorRate={0} → {1}")
    @CsvSource({
            "5.0, MEDIUM",
            "10.0, HIGH",
            "20.0, CRITICAL"
    })
    void 에러율_구간별로_심각도가_올바르게_매겨진다(double errorRate, IncidentSeverity expected) {
        MetricSnapshot anomaly = MetricSnapshot.builder()
                .resourceId(1L)
                .cpuUsage(10.0)
                .memUsage(10.0)
                .errorRate(errorRate)
                .queueDepth(5)
                .build();

        Optional<RuleEvaluation> result = ruleEngine.evaluate(anomaly);

        assertThat(result).isPresent();
        assertThat(result.get().ruleTriggered()).isEqualTo("ERROR_RATE_EXCEEDED");
        assertThat(result.get().severity()).isEqualTo(expected);
    }
}
