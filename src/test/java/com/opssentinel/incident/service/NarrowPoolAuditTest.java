package com.opssentinel.incident.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.opssentinel.audit.entity.AuditLog;
import com.opssentinel.audit.entity.ResultStatus;
import com.opssentinel.audit.repository.AuditLogRepository;
import com.opssentinel.incident.entity.IncidentStatus;
import com.opssentinel.incident.repository.IncidentRepository;
import com.opssentinel.metric.entity.MetricSnapshot;
import com.opssentinel.metric.repository.MetricSnapshotRepository;
import com.opssentinel.resource.entity.Resource;
import com.opssentinel.resource.entity.ResourceType;
import com.opssentinel.resource.repository.ResourceRepository;
import com.opssentinel.support.FakeOpenAiServer;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 재현 테스트(b)(c): 커넥션 풀이 동시 요청 수보다 작아도 교착·감사 유실이 없어야 한다.
 *
 * <p>2026-09 재측정(metrics M5·M7)에서 확인한 메커니즘: 같은 리소스의 락 대기 요청들이 풀을
 * 모두 쥔 상태에서, 락을 쥔 스레드가 감사로그(REQUIRES_NEW)용 커넥션을 하나 더 빌리려다
 * 멈춘다. 이 교착은 H2 LOCK_TIMEOUT이 대기자를 끊어야 풀리고, 끊긴 요청은 409로 끝나며
 * 감사 기록이 성공·실패 모두 0건이다(N=150·풀60: 409 91건, FAIL 감사 0건).
 *
 * <p>실제 HTTP 경로(OSIV 인터셉터 포함)를 그대로 타도록 MockMvc로 호출한다. 풀 5·LOCK_TIMEOUT
 * 2초로 좁혀 같은 현상을 수 초 안에 재현한다.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:narrow-pool;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=2000",
        "spring.datasource.hikari.maximum-pool-size=5",
        "spring.datasource.hikari.connection-timeout=10000",
        "openai.api-key=test-key-not-real",
        "openai.timeout-ms=5000"
})
@AutoConfigureMockMvc
class NarrowPoolAuditTest {

    private static final int CONCURRENT_REQUESTS = 20;
    private static final FakeOpenAiServer FAKE_OPENAI = FakeOpenAiServer.start(300);

    @DynamicPropertySource
    static void openAiBaseUrl(DynamicPropertyRegistry registry) {
        registry.add("openai.base-url", FAKE_OPENAI::baseUrl);
    }

    @AfterAll
    static void stopFakeOpenAi() {
        FAKE_OPENAI.close();
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ResourceRepository resourceRepository;

    @Autowired
    private IncidentRepository incidentRepository;

    @Autowired
    private MetricSnapshotRepository metricSnapshotRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void 풀_5에서_같은_리소스_동시요청_20건이_모두_성공하고_감사로그가_요청수만큼_남는다() throws Exception {
        Resource resource = newResource("narrow-pool-burst");
        String marker = "95.00123";

        Map<Integer, Integer> statusCounts = new ConcurrentHashMap<>();
        ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT_REQUESTS);
        CountDownLatch ready = new CountDownLatch(CONCURRENT_REQUESTS);
        CountDownLatch start = new CountDownLatch(1);
        for (int i = 0; i < CONCURRENT_REQUESTS; i++) {
            executor.submit(() -> {
                ready.countDown();
                int status;
                try {
                    start.await();
                    status = simulate(resource.getId(), marker);
                } catch (Exception e) {
                    status = -1;
                }
                statusCounts.merge(status, 1, Integer::sum);
            });
        }
        ready.await(5, TimeUnit.SECONDS);
        long t0 = System.nanoTime();
        start.countDown();
        executor.shutdown();
        assertThat(executor.awaitTermination(60, TimeUnit.SECONDS)).isTrue();
        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - t0);

        assertThat(statusCounts).as("응답코드 분포(소요 %dms)", elapsedMs).containsExactly(Map.entry(201, CONCURRENT_REQUESTS));
        assertThat(elapsedMs).as("락 타임아웃(2초)에 걸리지 않아야 한다").isLessThan(2000);

        long openCount = incidentRepository.findAll().stream()
                .filter(i -> i.getResourceId().equals(resource.getId()))
                .filter(i -> i.getStatus() == IncidentStatus.DETECTED || i.getStatus() == IncidentStatus.ANALYZING)
                .count();
        assertThat(openCount).isEqualTo(1);

        List<AuditLog> simulateAudits = auditsWithMarker("METRIC_SIMULATE", marker);
        assertThat(simulateAudits).hasSize(CONCURRENT_REQUESTS)
                .allMatch(a -> a.getResultStatus() == ResultStatus.SUCCESS);

        // INCIDENT_DETECT의 targetId는 (새로 만들었든 기존 것이든) 반환된 사건 id다
        Long incidentId = incidentRepository.findFirstByResourceIdAndStatusIn(resource.getId(),
                List.of(IncidentStatus.DETECTED, IncidentStatus.ANALYZING)).orElseThrow().getId();
        long detectSuccess = auditLogRepository.findAll().stream()
                .filter(a -> "INCIDENT_DETECT".equals(a.getAction()))
                .filter(a -> a.getResultStatus() == ResultStatus.SUCCESS)
                .filter(a -> incidentId.equals(a.getTargetId()))
                .count();
        assertThat(detectSuccess)
                .as("INCIDENT_DETECT SUCCESS 감사도 요청 수 이상 남아야 한다(스케줄러 발 기록이 더해질 수 있음)")
                .isGreaterThanOrEqualTo(CONCURRENT_REQUESTS);
    }

    @Test
    void 락_타임아웃으로_409가_난_요청도_FAIL_감사로그가_남는다() throws Exception {
        Resource resource = newResource("narrow-pool-conflict");
        String marker = "95.00456";

        // 다른 트랜잭션이 Resource 락을 7초 쥔다 → 요청은 LOCK_TIMEOUT(2초)×재시도 3회를 모두 잃고 409
        CountDownLatch locked = new CountDownLatch(1);
        Thread holder = new Thread(() -> new TransactionTemplate(transactionManager).executeWithoutResult(s -> {
            resourceRepository.lockById(resource.getId()).orElseThrow();
            locked.countDown();
            try {
                Thread.sleep(7000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }));
        holder.start();
        assertThat(locked.await(5, TimeUnit.SECONDS)).isTrue();

        int status = simulate(resource.getId(), marker);
        holder.join();

        assertThat(status).isEqualTo(409);
        List<AuditLog> simulateAudits = auditsWithMarker("METRIC_SIMULATE", marker);
        assertThat(simulateAudits).as("409 요청의 METRIC_SIMULATE 감사").hasSize(1);
        assertThat(simulateAudits.get(0).getResultStatus()).isEqualTo(ResultStatus.FAIL);

        List<Long> snapshotIds = snapshotIds(resource.getId());
        assertThat(snapshotIds).hasSize(1);
        List<AuditLog> detectFails = auditLogRepository.findAll().stream()
                .filter(a -> "INCIDENT_DETECT".equals(a.getAction()))
                .filter(a -> a.getResultStatus() == ResultStatus.FAIL)
                .filter(a -> snapshotIds.get(0).equals(a.getTargetId()))
                .toList();
        assertThat(detectFails).as("409 요청의 INCIDENT_DETECT FAIL 감사").hasSize(1);
        assertThat(detectFails.get(0).getErrorMessage()).contains("재시도");
    }

    @Test
    void 이미_OPEN_사건이_있으면_락을_기다리지_않고_바로_응답한다() throws Exception {
        Resource resource = newResource("narrow-pool-fast-path");
        assertThat(simulate(resource.getId(), "95.00789")).isEqualTo(201);

        // 다른 트랜잭션이 Resource 락을 3초 쥔 동안 같은 리소스로 다시 요청한다
        CountDownLatch locked = new CountDownLatch(1);
        Thread holder = new Thread(() -> new TransactionTemplate(transactionManager).executeWithoutResult(s -> {
            resourceRepository.lockById(resource.getId()).orElseThrow();
            locked.countDown();
            try {
                Thread.sleep(3000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }));
        holder.start();
        assertThat(locked.await(5, TimeUnit.SECONDS)).isTrue();

        long t0 = System.nanoTime();
        int status = simulate(resource.getId(), "95.00790");
        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - t0);
        holder.join();

        assertThat(status).isEqualTo(201);
        assertThat(elapsedMs).as("락 없는 사전 조회로 기존 사건을 바로 돌려줘야 한다").isLessThan(1000);
    }

    private Resource newResource(String name) {
        return resourceRepository.save(Resource.builder().name(name).type(ResourceType.SERVER).build());
    }

    private int simulate(Long resourceId, String cpuMarker) throws Exception {
        String body = "{\"resourceId\":%d,\"cpuUsage\":%s,\"memUsage\":10.0,\"errorRate\":0.1,\"queueDepth\":1}"
                .formatted(resourceId, cpuMarker);
        return mockMvc.perform(post("/api/metrics/simulate").contentType(MediaType.APPLICATION_JSON).content(body))
                .andReturn().getResponse().getStatus();
    }

    private List<AuditLog> auditsWithMarker(String action, String cpuMarker) {
        return auditLogRepository.findAll().stream()
                .filter(a -> action.equals(a.getAction()))
                .filter(a -> a.getRequestSummary() != null && a.getRequestSummary().contains("cpuUsage=" + cpuMarker + ","))
                .toList();
    }

    private List<Long> snapshotIds(Long resourceId) {
        return metricSnapshotRepository.findAll().stream()
                .filter(s -> s.getResourceId().equals(resourceId))
                .filter(s -> s.getCpuUsage() > 95.0 && s.getCpuUsage() < 95.01)
                .map(MetricSnapshot::getId)
                .toList();
    }
}
