package com.opssentinel.incident.service;

import com.opssentinel.incident.entity.Incident;
import com.opssentinel.incident.entity.IncidentSeverity;
import com.opssentinel.incident.entity.IncidentStatus;
import com.opssentinel.incident.repository.IncidentRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * 사건 조회·해결 처리 서비스. 사건 자동 생성(IncidentDetectionService)·조치 자동 기록
 * (IncidentActionService)과는 관심사가 달라 별도 서비스로 분리했다.
 */
@Service
@RequiredArgsConstructor
public class IncidentQueryService {

    private final IncidentRepository incidentRepository;

    public Page<Incident> list(IncidentStatus status, IncidentSeverity severity, Long resourceId, Pageable pageable) {
        return incidentRepository.search(status, severity, resourceId, pageable);
    }

    /** 조치이력(actions)까지 fetch join으로 즉시 로딩된 상태로 반환한다(사건 상세 조회용). */
    public Incident getById(Long id) {
        return incidentRepository.findByIdWithActions(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Incident not found: " + id));
    }

    /** status를 RESOLVED로 전이하고 resolvedAt을 기록한다. */
    @Transactional
    public Incident resolve(Long id) {
        Incident incident = incidentRepository.findByIdWithActions(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Incident not found: " + id));
        incident.setStatus(IncidentStatus.RESOLVED);
        incident.setResolvedAt(LocalDateTime.now());
        return incident;
    }
}
