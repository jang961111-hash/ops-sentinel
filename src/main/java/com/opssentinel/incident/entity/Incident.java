package com.opssentinel.incident.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

/**
 * 이상탐지로 발생한 사건(Incident).
 *
 * <p>resourceId는 resource 도메인 엔티티를 직접 참조하지 않고 스칼라 값으로만 보관한다
 * (도메인 간 직접 참조 최소화 — PRD 3-1 아키텍처 결정 참고). 반면 IncidentAction은 같은
 * incident 도메인에 속하므로 조치 이력 조회(PRD: Incident 상세 = 조치이력 포함)를 위해
 * 양방향 @OneToMany/@ManyToOne 연관관계를 맺는다.
 *
 * <p>version 필드는 낙관적 락(@Version)으로 동시 갱신 충돌을 감지한다. 다만 동일 리소스에
 * 대한 중복 사건 생성 방지(US-004)는 이 필드가 아니라 {@code IncidentDetectionService}의
 * Resource 비관적 락(SELECT ... FOR UPDATE)으로 처리한다 — 서로 다른 두 row를 두고 벌어지는
 * check-then-act는 같은 row 갱신 충돌만 감지하는 낙관적 락으로 막을 수 없기 때문이다. 자세한
 * 이유는 {@code IncidentDetectionService}의 클래스 주석 참고.
 */
@Entity
@Table(name = "incidents")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Incident {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long resourceId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private IncidentSeverity severity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private IncidentStatus status;

    @Column(nullable = false)
    private String ruleTriggered;

    @Column(length = 1000)
    private String aiSummary;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime detectedAt;

    private LocalDateTime resolvedAt;

    @Version
    private Long version;

    @Builder.Default
    @OneToMany(mappedBy = "incident", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<IncidentAction> actions = new ArrayList<>();
}
