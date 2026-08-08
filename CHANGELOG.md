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
