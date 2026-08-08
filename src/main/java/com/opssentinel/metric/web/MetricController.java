package com.opssentinel.metric.web;

import com.opssentinel.metric.service.MetricService;
import com.opssentinel.metric.web.dto.MetricSnapshotResponse;
import com.opssentinel.metric.web.dto.SimulateMetricRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Metric", description = "지표 시뮬레이터 및 조회 API")
@RestController
@RequestMapping("/api/metrics")
@RequiredArgsConstructor
public class MetricController {

    private final MetricService metricService;

    @Operation(summary = "수동 지표 생성", description = "데모용 수동 트리거. 미지정 필드는 랜덤 값으로 채워진다.")
    @PostMapping("/simulate")
    public ResponseEntity<MetricSnapshotResponse> simulate(@Valid @RequestBody SimulateMetricRequest request) {
        MetricSnapshotResponse response = MetricSnapshotResponse.from(metricService.simulate(request));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "최신 지표 조회")
    @GetMapping("/{resourceId}/latest")
    public MetricSnapshotResponse latest(@PathVariable Long resourceId) {
        return MetricSnapshotResponse.from(metricService.getLatest(resourceId));
    }

    @Operation(summary = "지표 이력 조회", description = "from/to 미지정 시 전체 이력을 반환한다.")
    @GetMapping("/{resourceId}/history")
    public List<MetricSnapshotResponse> history(
            @PathVariable Long resourceId,
            @Parameter(description = "조회 시작 시각(ISO-8601), 미지정 시 전체")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @Parameter(description = "조회 종료 시각(ISO-8601), 미지정 시 전체")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
        return metricService.getHistory(resourceId, from, to).stream()
                .map(MetricSnapshotResponse::from)
                .toList();
    }
}
