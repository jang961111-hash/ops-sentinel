package com.opssentinel.metric.web.dto;

import com.opssentinel.metric.entity.MetricSnapshot;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

/**
 * MetricSnapshot 엔티티를 외부에 노출하지 않기 위한 응답 DTO.
 */
@Schema(description = "지표 스냅샷 응답")
public record MetricSnapshotResponse(
        Long id,
        Long resourceId,
        Double cpuUsage,
        Double memUsage,
        Double errorRate,
        Integer queueDepth,
        LocalDateTime capturedAt
) {

    public static MetricSnapshotResponse from(MetricSnapshot snapshot) {
        return new MetricSnapshotResponse(
                snapshot.getId(),
                snapshot.getResourceId(),
                snapshot.getCpuUsage(),
                snapshot.getMemUsage(),
                snapshot.getErrorRate(),
                snapshot.getQueueDepth(),
                snapshot.getCapturedAt()
        );
    }
}
