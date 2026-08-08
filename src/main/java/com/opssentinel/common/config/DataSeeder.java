package com.opssentinel.common.config;

import com.opssentinel.metric.entity.MetricSnapshot;
import com.opssentinel.metric.repository.MetricSnapshotRepository;
import com.opssentinel.resource.entity.Resource;
import com.opssentinel.resource.entity.ResourceType;
import com.opssentinel.resource.repository.ResourceRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * 기동 시 데모용 초기 데이터를 적재한다(US-012).
 *
 * <p>실제 서비스처럼 보이는 이름의 리소스 8개(4개 타입 골고루)와, 각 리소스당 정상범위
 * 지표 스냅샷을 1~2건씩 미리 넣어둬서 {@code ./gradlew bootRun} 직후 수동 조작 없이도
 * {@code GET /api/resources} 등으로 바로 확인 가능한 상태를 만든다.
 *
 * <p>재기동 시 중복 삽입을 막기 위해 Resource 건수를 먼저 확인하고, 이미 데이터가
 * 있으면(H2는 인메모리라 실제로는 항상 비어 있지만, 향후 파일 DB 전환을 대비해)
 * 그대로 스킵한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

    private final ResourceRepository resourceRepository;
    private final MetricSnapshotRepository metricSnapshotRepository;

    @Override
    public void run(String... args) {
        if (resourceRepository.count() > 0) {
            log.info("Seed data already present ({} resources) — skip seeding.", resourceRepository.count());
            return;
        }

        List<Resource> seeded = resourceRepository.saveAll(List.of(
                Resource.builder().name("order-service-web").type(ResourceType.SERVER).build(),
                Resource.builder().name("order-service-db").type(ResourceType.DATABASE).build(),
                Resource.builder().name("payment-service-web").type(ResourceType.SERVER).build(),
                Resource.builder().name("payment-service-db").type(ResourceType.DATABASE).build(),
                Resource.builder().name("payment-queue").type(ResourceType.QUEUE).build(),
                Resource.builder().name("notification-queue").type(ResourceType.QUEUE).build(),
                Resource.builder().name("user-api").type(ResourceType.API).build(),
                Resource.builder().name("auth-api").type(ResourceType.API).build()
        ));

        for (Resource resource : seeded) {
            metricSnapshotRepository.save(normalSnapshot(resource.getId()));
            metricSnapshotRepository.save(normalSnapshot(resource.getId()));
        }

        log.info("Seeded {} resources with initial metric snapshots.", seeded.size());
    }

    /** 규칙엔진 임계치(errorRate 5%/cpu·mem 90%/queueDepth 50)를 넉넉히 밑도는 정상범위 값. */
    private MetricSnapshot normalSnapshot(Long resourceId) {
        return MetricSnapshot.builder()
                .resourceId(resourceId)
                .cpuUsage(35.0)
                .memUsage(40.0)
                .errorRate(0.5)
                .queueDepth(5)
                .build();
    }
}
