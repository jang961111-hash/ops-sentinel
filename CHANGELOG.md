# Changelog

이 프로젝트의 모든 주요 변경사항을 이 파일에 기록한다.
포맷은 [Keep a Changelog](https://keepachangelog.com/ko/1.0.0/)를 따르고,
버전 규칙은 [Semantic Versioning](https://semver.org/lang/ko/)을 따른다.

## [Unreleased]

### Added
- 프로젝트 초기 세팅 (기획 문서, Git 컨벤션, 이슈/PR 템플릿)
- Gradle 기반 Spring Boot 3.3(Java 21) 프로젝트 초기화 — resource/metric/incident/audit/analytics/common 6개 도메인 패키지, H2 인메모리 datasource, MyBatis mapper-locations 기본값 (#1)
- 5개 JPA 엔티티(Resource/MetricSnapshot/Incident/IncidentAction/AuditLog) + Repository 인터페이스 — Incident에 낙관적 락(@Version), Incident-IncidentAction 양방향 연관관계 (#2)
- 지표 시뮬레이터 — `@Scheduled(fixedRate=7000)`로 등록된 전체 리소스에 랜덤 지표 생성, 수동 트리거 API(`POST /api/metrics/simulate`)와 조회 API(`GET /api/metrics/{resourceId}/latest`, `/history`) 추가. 랜덤 값은 대체로 정상 범위이되 10% 확률로 임계치 초과 이상치를 섞어 이상탐지 규칙엔진(US-004)이 자연스럽게 트리거되게 함 (#3)
- 이상탐지 규칙엔진(`IncidentRuleEngine`, if-else 기반)과 `IncidentDetectionService` 추가 — errorRate/cpuUsage/memUsage/queueDepth 임계치 초과 시 Incident 자동 생성, 지표 시뮬레이터 수동/자동 트리거 양쪽에서 호출. 동시성 제어는 동일 resourceId의 Resource 행에 비관적 락(`SELECT ... FOR UPDATE`)을 걸어 "OPEN 사건 조회 → 없으면 생성" 임계구간을 직렬화하는 방식을 채택(H2가 부분 unique 인덱스를 지원하지 않고, Incident.version 낙관적 락은 서로 다른 두 row의 동시 insert를 막지 못해 제외), 락 타임아웃 등 예외 대비 최대 3회 재시도 (#4)
