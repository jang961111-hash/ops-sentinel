package com.opssentinel.incident.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.opssentinel.incident.entity.Incident;
import com.opssentinel.incident.entity.IncidentStatus;
import com.opssentinel.incident.repository.IncidentRepository;
import com.opssentinel.metric.service.MetricService;
import com.opssentinel.metric.web.dto.SimulateMetricRequest;
import com.opssentinel.resource.entity.Resource;
import com.opssentinel.resource.entity.ResourceType;
import com.opssentinel.resource.repository.ResourceRepository;
import com.opssentinel.support.FakeOpenAiServer;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * 재현 테스트(a): AI 요약이 느려도 같은 리소스 동시 요청이 AI 지연만큼 기다리면 안 된다.
 *
 * <p>2026-09 재측정(metrics M5)에서 AI 응답이 3초 걸리면 같은 리소스 20건 전부가 3.05초를
 * 기다리고 처리량이 340 → 6.6 rps로 떨어졌다. AI 호출이 Resource 비관적 락을 쥔 트랜잭션
 * 안에 있어서, 락을 쥔 1건의 AI 대기를 나머지 전부가 락 대기로 함께 치르기 때문이다.
 *
 * <p>풀 크기에 따른 교착(재현 테스트 b)과 섞이지 않도록 이 테스트는 풀을 넉넉히(30) 둔다.
 * 가짜 OpenAI 서버가 {@value #AI_DELAY_MS}ms 뒤에 응답한다.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:ai-outside-lock;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
        "spring.datasource.hikari.maximum-pool-size=30",
        "openai.api-key=test-key-not-real",
        "openai.timeout-ms=5000"
})
class AiSummaryOutsideLockTest {

    private static final long AI_DELAY_MS = 1500;
    private static final int CONCURRENT_REQUESTS = 10;
    private static final FakeOpenAiServer FAKE_OPENAI = FakeOpenAiServer.start(AI_DELAY_MS);

    @DynamicPropertySource
    static void openAiBaseUrl(DynamicPropertyRegistry registry) {
        registry.add("openai.base-url", FAKE_OPENAI::baseUrl);
    }

    @AfterAll
    static void stopFakeOpenAi() {
        FAKE_OPENAI.close();
    }

    @Autowired
    private MetricService metricService;

    @Autowired
    private ResourceRepository resourceRepository;

    @Autowired
    private IncidentRepository incidentRepository;

    @Test
    void AI_요약이_느려도_같은_리소스_동시요청은_AI_지연만큼_기다리지_않는다() throws Exception {
        Resource resource = resourceRepository.save(Resource.builder()
                .name("ai-outside-lock-server")
                .type(ResourceType.SERVER)
                .build());

        ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT_REQUESTS);
        CountDownLatch ready = new CountDownLatch(CONCURRENT_REQUESTS);
        CountDownLatch start = new CountDownLatch(1);
        Queue<Long> latenciesMs = new ConcurrentLinkedQueue<>();
        AtomicInteger failures = new AtomicInteger();

        for (int i = 0; i < CONCURRENT_REQUESTS; i++) {
            executor.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    long t0 = System.nanoTime();
                    // 나머지 지표는 안전값으로 고정해 CPU_EXCEEDED 하나만 트리거한다
                    metricService.simulate(new SimulateMetricRequest(resource.getId(), 95.0, 0.0, 0.0, 0));
                    latenciesMs.add(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - t0));
                } catch (Exception e) {
                    failures.incrementAndGet();
                }
            });
        }
        ready.await(5, TimeUnit.SECONDS);
        start.countDown();
        executor.shutdown();
        assertThat(executor.awaitTermination(60, TimeUnit.SECONDS)).isTrue();

        assertThat(failures.get()).isZero();
        long maxLatency = latenciesMs.stream().mapToLong(Long::longValue).max().orElseThrow();
        assertThat(maxLatency)
                .as("가장 느린 요청(%dms)도 AI 지연(%dms)을 기다리지 않아야 한다: %s", maxLatency, AI_DELAY_MS, latenciesMs)
                .isLessThan(AI_DELAY_MS / 2);

        List<Incident> open = openIncidents(resource.getId());
        assertThat(open).hasSize(1);
        Incident incident = open.get(0);
        // 사건은 AI 응답을 기다리지 않고 폴백 문구로 먼저 생성된다(대시보드에 빈 요약이 뜨지 않게).
        // 이 조회가 느린 환경에서 늦어지면 이미 AI 요약으로 바뀌었을 수 있으므로 둘 다 허용한다 —
        // "AI를 기다리지 않는다"는 위의 지연 단언이 보장한다.
        assertThat(incident.getAiSummary()).isIn(
                incident.getSeverity() + " 등급의 CPU_EXCEEDED 이상이 감지되어 자동 조치가 실행되었습니다.",
                FakeOpenAiServer.SUMMARY);

        // 커밋 뒤 비동기로 AI 요약이 채워진다
        long deadline = System.currentTimeMillis() + AI_DELAY_MS + 5000;
        String summary = incident.getAiSummary();
        while (!FakeOpenAiServer.SUMMARY.equals(summary) && System.currentTimeMillis() < deadline) {
            Thread.sleep(100);
            summary = incidentRepository.findById(incident.getId()).orElseThrow().getAiSummary();
        }
        assertThat(summary).isEqualTo(FakeOpenAiServer.SUMMARY);
    }

    private List<Incident> openIncidents(Long resourceId) {
        return incidentRepository.findAll().stream()
                .filter(i -> i.getResourceId().equals(resourceId))
                .filter(i -> i.getStatus() == IncidentStatus.DETECTED || i.getStatus() == IncidentStatus.ANALYZING)
                .toList();
    }
}
