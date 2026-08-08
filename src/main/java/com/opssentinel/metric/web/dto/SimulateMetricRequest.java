package com.opssentinel.metric.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * 수동 지표 생성(POST /api/metrics/simulate) 요청 바디.
 * cpuUsage/memUsage/errorRate/queueDepth는 선택 항목이며, 미지정 시 서버가 랜덤 값을 채운다.
 * 값을 직접 지정하면 데모 시나리오처럼 임계치 초과 상황을 강제로 재현할 수 있다.
 */
@Schema(description = "수동 지표 생성 요청")
public record SimulateMetricRequest(

        @Schema(description = "대상 리소스 ID", example = "1")
        @NotNull
        Long resourceId,

        @Schema(description = "CPU 사용률(%), 미지정 시 랜덤", example = "95.0")
        Double cpuUsage,

        @Schema(description = "메모리 사용률(%), 미지정 시 랜덤", example = "40.0")
        Double memUsage,

        @Schema(description = "에러율(%), 미지정 시 랜덤", example = "8.5")
        Double errorRate,

        @Schema(description = "큐 길이, 미지정 시 랜덤", example = "10")
        Integer queueDepth
) {
}
