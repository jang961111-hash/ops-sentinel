package com.opssentinel.metric.service;

import com.opssentinel.incident.service.IncidentDetectionService;
import com.opssentinel.metric.entity.MetricSnapshot;
import com.opssentinel.metric.repository.MetricSnapshotRepository;
import com.opssentinel.metric.web.dto.SimulateMetricRequest;
import com.opssentinel.resource.entity.Resource;
import com.opssentinel.resource.repository.ResourceRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Random;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * 지표 스냅샷 시뮬레이터.
 *
 * <p>스케줄러가 등록된 모든 리소스에 대해 주기적으로 랜덤 지표를 생성·저장하고,
 * 수동 트리거 API({@code POST /api/metrics/simulate})도 같은 랜덤 생성 로직을 재사용한다.
 *
 * <p>랜덤 값은 대체로 정상 범위에서 생성하되, {@link #ANOMALY_PROBABILITY} 확률로
 * 임계치를 넘는 이상치를 섞는다 — 이후 이상탐지 규칙엔진(US-004)이 별도 조작 없이도
 * 자연스럽게 트리거되도록 하기 위함이다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MetricService {

    /** 지표 하나당 이상치가 섞일 확률(10%). 너무 낮으면 데모 중 규칙엔진이 안 터지고, 너무 높으면 사건이 남발된다. */
    private static final double ANOMALY_PROBABILITY = 0.1;

    private static final double CPU_NORMAL_MAX = 70.0;
    private static final double CPU_ANOMALY_MIN = 90.0;
    private static final double CPU_ANOMALY_RANGE = 10.0;

    private static final double MEM_NORMAL_MAX = 70.0;
    private static final double MEM_ANOMALY_MIN = 90.0;
    private static final double MEM_ANOMALY_RANGE = 10.0;

    private static final double ERROR_NORMAL_MAX = 3.0;
    private static final double ERROR_ANOMALY_MIN = 5.0;
    private static final double ERROR_ANOMALY_RANGE = 15.0;

    private static final int QUEUE_NORMAL_MAX = 20;
    private static final int QUEUE_ANOMALY_MIN = 50;
    private static final int QUEUE_ANOMALY_RANGE = 150;

    private final MetricSnapshotRepository metricSnapshotRepository;
    private final ResourceRepository resourceRepository;
    private final IncidentDetectionService incidentDetectionService;
    private final Random random = new Random();

    /**
     * 등록된 모든 리소스에 대해 랜덤 지표 스냅샷을 생성·저장한다.
     * fixedRate=7000(7초)로 잡은 이유: PRD 요구사항(5~10초 간격)의 중간값이면서,
     * 데모 시연 중 너무 오래 기다리지 않고도 스냅샷이 여러 건 쌓이는 걸 보여줄 수 있어서다.
     */
    @Scheduled(fixedRate = 7000)
    public void generateForAllResources() {
        List<Resource> resources = resourceRepository.findAll();
        for (Resource resource : resources) {
            MetricSnapshot snapshot = metricSnapshotRepository.save(randomSnapshot(resource.getId()));
            log.debug("Generated metric snapshot: resourceId={}, cpu={}, mem={}, error={}, queue={}",
                    snapshot.getResourceId(), snapshot.getCpuUsage(), snapshot.getMemUsage(),
                    snapshot.getErrorRate(), snapshot.getQueueDepth());
            incidentDetectionService.detectAndCreate(snapshot);
        }
    }

    /**
     * 수동 지표 생성. 요청에 값이 지정된 필드는 그대로 사용하고(데모용 강제 임계치 초과 주입),
     * 미지정 필드는 랜덤 생성 로직으로 채운다.
     */
    public MetricSnapshot simulate(SimulateMetricRequest request) {
        Long resourceId = request.resourceId();
        if (!resourceRepository.existsById(resourceId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Resource not found: " + resourceId);
        }

        MetricSnapshot snapshot = MetricSnapshot.builder()
                .resourceId(resourceId)
                .cpuUsage(request.cpuUsage() != null ? request.cpuUsage() : randomCpuUsage())
                .memUsage(request.memUsage() != null ? request.memUsage() : randomMemUsage())
                .errorRate(request.errorRate() != null ? request.errorRate() : randomErrorRate())
                .queueDepth(request.queueDepth() != null ? request.queueDepth() : randomQueueDepth())
                .build();
        MetricSnapshot saved = metricSnapshotRepository.save(snapshot);
        incidentDetectionService.detectAndCreate(saved);
        return saved;
    }

    public MetricSnapshot getLatest(Long resourceId) {
        return metricSnapshotRepository.findFirstByResourceIdOrderByCapturedAtDesc(resourceId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "No metric snapshot found for resource: " + resourceId));
    }

    public List<MetricSnapshot> getHistory(Long resourceId, LocalDateTime from, LocalDateTime to) {
        return metricSnapshotRepository.findHistory(resourceId, from, to);
    }

    private MetricSnapshot randomSnapshot(Long resourceId) {
        return MetricSnapshot.builder()
                .resourceId(resourceId)
                .cpuUsage(randomCpuUsage())
                .memUsage(randomMemUsage())
                .errorRate(randomErrorRate())
                .queueDepth(randomQueueDepth())
                .build();
    }

    private double randomCpuUsage() {
        if (isAnomaly()) {
            return round(CPU_ANOMALY_MIN + random.nextDouble() * CPU_ANOMALY_RANGE);
        }
        return round(random.nextDouble() * CPU_NORMAL_MAX);
    }

    private double randomMemUsage() {
        if (isAnomaly()) {
            return round(MEM_ANOMALY_MIN + random.nextDouble() * MEM_ANOMALY_RANGE);
        }
        return round(random.nextDouble() * MEM_NORMAL_MAX);
    }

    private double randomErrorRate() {
        if (isAnomaly()) {
            return round(ERROR_ANOMALY_MIN + random.nextDouble() * ERROR_ANOMALY_RANGE);
        }
        return round(random.nextDouble() * ERROR_NORMAL_MAX);
    }

    private int randomQueueDepth() {
        if (isAnomaly()) {
            return QUEUE_ANOMALY_MIN + random.nextInt(QUEUE_ANOMALY_RANGE);
        }
        return random.nextInt(QUEUE_NORMAL_MAX + 1);
    }

    private boolean isAnomaly() {
        return random.nextDouble() < ANOMALY_PROBABILITY;
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
