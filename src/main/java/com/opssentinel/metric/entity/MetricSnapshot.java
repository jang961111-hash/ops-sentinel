package com.opssentinel.metric.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

/**
 * 리소스별 CPU/메모리/에러율/큐 길이 스냅샷.
 * 스케줄러가 5~10초 간격으로 생성(시뮬레이션)한다.
 *
 * <p>resourceId는 resource 도메인 엔티티를 직접 참조하지 않고 스칼라 값으로만 보관한다
 * (도메인 간 직접 참조 최소화 — PRD 3-1 아키텍처 결정 참고).
 */
@Entity
@Table(name = "metric_snapshots")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MetricSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long resourceId;

    @Column(nullable = false)
    private Double cpuUsage;

    @Column(nullable = false)
    private Double memUsage;

    @Column(nullable = false)
    private Double errorRate;

    @Column(nullable = false)
    private Integer queueDepth;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime capturedAt;
}
