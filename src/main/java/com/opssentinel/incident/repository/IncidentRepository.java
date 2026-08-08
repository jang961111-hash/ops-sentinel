package com.opssentinel.incident.repository;

import com.opssentinel.incident.entity.Incident;
import com.opssentinel.incident.entity.IncidentStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IncidentRepository extends JpaRepository<Incident, Long> {

    /**
     * 동일 resourceId에 대해 주어진 상태(보통 DETECTED/ANALYZING=OPEN) 중 하나인 사건이
     * 이미 있는지 조회한다. US-004 동시성 제어(중복 사건 방지)의 판단 기준.
     */
    Optional<Incident> findFirstByResourceIdAndStatusIn(Long resourceId, List<IncidentStatus> statuses);
}
