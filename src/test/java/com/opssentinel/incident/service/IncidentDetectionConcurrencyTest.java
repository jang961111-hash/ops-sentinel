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
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * US-004 동시성 제어 검증: 동일 resourceId로 동시에 여러 요청이 몰려도 OPEN 상태(DETECTED/ANALYZING)
 * Incident는 정확히 1건만 생성돼야 한다.
 */
@SpringBootTest
class IncidentDetectionConcurrencyTest {

    private static final int CONCURRENT_REQUESTS = 10;

    @Autowired
    private MetricService metricService;

    @Autowired
    private ResourceRepository resourceRepository;

    @Autowired
    private IncidentRepository incidentRepository;

    @Test
    void 동일_리소스에_동시요청_10건이_와도_Incident는_1건만_생성된다() throws InterruptedException {
        Resource resource = resourceRepository.save(Resource.builder()
                .name("concurrency-test-server")
                .type(ResourceType.SERVER)
                .build());

        ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT_REQUESTS);
        CountDownLatch readyLatch = new CountDownLatch(CONCURRENT_REQUESTS);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(CONCURRENT_REQUESTS);
        AtomicInteger failureCount = new AtomicInteger();

        for (int i = 0; i < CONCURRENT_REQUESTS; i++) {
            executor.submit(() -> {
                try {
                    readyLatch.countDown();
                    startLatch.await();
                    // cpuUsage=95는 CPU_EXCEEDED(HIGH)를 확정적으로 트리거하는 값
                    SimulateMetricRequest request = new SimulateMetricRequest(resource.getId(), 95.0, null, null, null);
                    metricService.simulate(request);
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        readyLatch.await(5, TimeUnit.SECONDS);
        startLatch.countDown();
        boolean completed = doneLatch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(completed).as("모든 스레드가 타임아웃 없이 완료돼야 함").isTrue();
        assertThat(failureCount.get()).as("재시도로 흡수돼 예외 없이 끝나야 함").isZero();

        List<Incident> openIncidents = incidentRepository.findAll().stream()
                .filter(incident -> incident.getResourceId().equals(resource.getId()))
                .filter(incident -> incident.getStatus() == IncidentStatus.DETECTED
                        || incident.getStatus() == IncidentStatus.ANALYZING)
                .toList();

        assertThat(openIncidents).as("동시요청 " + CONCURRENT_REQUESTS + "건에도 OPEN Incident는 1건만 존재해야 함")
                .hasSize(1);
    }
}
