package com.opssentinel.incident.web;

import com.opssentinel.incident.entity.Incident;
import com.opssentinel.incident.entity.IncidentSeverity;
import com.opssentinel.incident.entity.IncidentStatus;
import com.opssentinel.incident.service.IncidentQueryService;
import com.opssentinel.incident.web.dto.IncidentDetailResponse;
import com.opssentinel.incident.web.dto.IncidentSummaryResponse;
import com.opssentinel.resource.entity.Resource;
import com.opssentinel.resource.repository.ResourceRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Incident", description = "사건 조회 및 해결 처리 API")
@RestController
@RequestMapping("/api/incidents")
@RequiredArgsConstructor
public class IncidentController {

    private final IncidentQueryService incidentQueryService;
    private final ResourceRepository resourceRepository;

    @Operation(summary = "사건 목록 조회", description = "status/severity/resourceId로 필터링 가능하며, 페이지 단위로 반환한다.")
    @GetMapping
    public Page<IncidentSummaryResponse> list(
            @Parameter(description = "사건 상태 필터") @RequestParam(required = false) IncidentStatus status,
            @Parameter(description = "심각도 필터") @RequestParam(required = false) IncidentSeverity severity,
            @Parameter(description = "리소스 ID 필터") @RequestParam(required = false) Long resourceId,
            Pageable pageable) {
        Page<Incident> page = incidentQueryService.list(status, severity, resourceId, pageable);
        Map<Long, String> resourceNames = resolveResourceNames(page.getContent());
        return page.map(incident -> IncidentSummaryResponse.from(incident, resourceNames.get(incident.getResourceId())));
    }

    @Operation(summary = "사건 상세 조회", description = "조치이력(actions)과 AI요약(aiSummary)을 포함해 반환한다.")
    @GetMapping("/{id}")
    public IncidentDetailResponse get(@PathVariable Long id) {
        Incident incident = incidentQueryService.getById(id);
        return IncidentDetailResponse.from(incident, resolveResourceName(incident));
    }

    @Operation(summary = "사건 해결 처리", description = "status를 RESOLVED로 전이하고 resolvedAt을 기록한다.")
    @PatchMapping("/{id}/resolve")
    public IncidentDetailResponse resolve(@PathVariable Long id) {
        Incident incident = incidentQueryService.resolve(id);
        return IncidentDetailResponse.from(incident, resolveResourceName(incident));
    }

    private String resolveResourceName(Incident incident) {
        return resourceRepository.findById(incident.getResourceId())
                .map(Resource::getName)
                .orElse(null);
    }

    private Map<Long, String> resolveResourceNames(List<Incident> incidents) {
        List<Long> resourceIds = incidents.stream().map(Incident::getResourceId).distinct().toList();
        return resourceRepository.findAllById(resourceIds).stream()
                .collect(Collectors.toMap(Resource::getId, Resource::getName));
    }
}
