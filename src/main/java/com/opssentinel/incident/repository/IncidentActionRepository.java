package com.opssentinel.incident.repository;

import com.opssentinel.incident.entity.IncidentAction;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IncidentActionRepository extends JpaRepository<IncidentAction, Long> {
}
