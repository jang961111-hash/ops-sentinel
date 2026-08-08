package com.opssentinel.metric.repository;

import com.opssentinel.metric.entity.MetricSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MetricSnapshotRepository extends JpaRepository<MetricSnapshot, Long> {
}
