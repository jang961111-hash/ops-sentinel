# Changelog

이 프로젝트의 모든 주요 변경사항을 이 파일에 기록한다.
포맷은 [Keep a Changelog](https://keepachangelog.com/ko/1.0.0/)를 따르고,
버전 규칙은 [Semantic Versioning](https://semver.org/lang/ko/)을 따른다.

## [Unreleased]

## [1.0.1] - 2026-08-09

독립 architect(Opus) 최종검증에서 REJECTED 판정을 받은 실증된 중대 결함 5건(C1~C5)을 전부
실제 재현 → 수정 → 재검증했다. 완성도가 아니라 "판단 근거를 사람이 사후 검증할 수 있는
감사 가능한 백엔드"라는 핵심 가치명제를 정면으로 무너뜨리는 결함들이었다. 권장 사항 2건
(M1, M2)도 함께 반영했다.

### Fixed
- **(C1) 감사로그가 존재하지 않는 Incident를 가리키던 문제** — `AuditLogAspect.extractTargetId`가 대상 메서드의 반환값에서 id를 못 찾으면 메서드 인자 중 아무 `getId()`나 가져다 썼다. `IncidentDetectionService.detectAndCreate()`가 정상 지표(약 90% 확률)에서 `Optional.empty()`를 반환해도 이 폴백이 발동해, 인자로 넘어온 `MetricSnapshot`의 id가 `targetType="Incident"` 감사로그의 targetId로 잘못 기록됐다(실측: 서버 갓 기동 후 정상 지표로 시뮬레이션 1회 호출 시 존재하지 않는 Incident id를 가리키는 감사로그 발생). 반환 타입이 `Optional`이면 그 값을 그대로 신뢰해 `Optional.empty()`를 "타겟 없음"으로 존중하고, 이 경우 인자 폴백을 하지 않도록 수정. (`AuditLogAspect.java`)
- **(C2) 부하 상황에서 실패 감사로그가 통째로 소실되던 문제** — `AuditLogRecorder.recordFailure()`(REQUIRES_NEW, 별도 DB 커넥션 필요)가 커넥션풀 고갈 등으로 트랜잭션을 여는 단계에서부터 자체 실패하면, 그 예외가 원래 비즈니스 예외를 대체해버려 `catch` 블록의 `throw ex`(원래 예외 재던지기)에 도달하지 못했다. 실측: HikariCP 풀을 3으로 인위적으로 좁힌 뒤 40개 동시요청을 보내자 클라이언트 500 실패가 40건 발생했는데, 이 중 FAIL 감사로그는 단 4건만 남았다(약 90% 유실). `AuditLogAspect`에서 `recordSuccess`/`recordFailure` 호출 자체를 try-catch로 한 번 더 감싸, 감사기록 자체의 실패는 로그로만 남기고 원래 예외/정상 흐름을 절대 덮어쓰지 않도록 수정 — 동일 조건 재현 시 37건의 새 실패 중 37건 모두 FAIL 감사로그로 남는 것을 확인. (`AuditLogAspect.java`)
- **(C3) 클라이언트 오류 대부분이 500으로 응답되던 문제** — `GlobalExceptionHandler`가 `ResponseEntityExceptionHandler`를 상속하지 않아, Spring MVC가 컨트롤러 진입 전에 자체적으로 던지는 프레임워크 예외(`HttpMessageNotReadableException`—깨진 JSON/잘못된 enum, `HttpRequestMethodNotSupportedException`—405, `NoResourceFoundException`—404)가 최상위 `Exception` catch-all로 떨어져 전부 500이 됐다. 실측(수정 전 → 후): 정의되지 않은 enum 값으로 리소스 등록 500→400, 깨진 JSON 본문 500→400, `PATCH /api/incidents/10`(미지원 메서드) 500→405, `GET /totally/unknown/path` 500→404. `ResponseEntityExceptionHandler`를 상속하고 `handleExceptionInternal`을 오버라이드해 이 프레임워크 예외들도 기존과 동일한 `{timestamp,status,error,message,path}` 공통 포맷으로 응답하도록 수정. (`GlobalExceptionHandler.java`)
- **(C4) Docker/PostgreSQL에서 MyBatis 집계가 깨지던 문제** — `AnalyticsMapper.xml`의 `DATEDIFF('MINUTE', detected_at, resolved_at)`가 H2 전용 함수라 PostgreSQL에서 `function datediff(...) does not exist` 500 에러가 발생했다(Docker Compose로 실제 PostgreSQL을 띄워 재현 확인). `EXTRACT(EPOCH FROM (resolved_at - detected_at)) / 60.0`(초 단위 차이를 분으로 환산하는 SQL 표준 표현식)으로 교체 — PostgreSQL은 물론, 이 프로젝트가 실제로 쓰는 H2 2.2.224에서도 직접 JDBC로 실행해 지원 여부를 확인했다(H2 2.x가 `EXTRACT(EPOCH FROM ...)`를 정상 지원함을 확인). 부가로 정수 단위로 뭉개지던 MTTR 값이 소수점까지 정확하게 나오게 됐다(예: 58초 해결 건이 이전엔 반올림된 정수 분이었지만 이제 `0.9667`분처럼 정확히 표시). (`AnalyticsMapper.xml`)
- **(C5) README가 완성된 기능을 미착수로 기술하던 문제** — PR #28(US-011) 이후 갱신되지 않아 OpenAI 연동(#30)·JWT(#31)·테스트보강(#32)·Docker Compose(#33)·대시보드(#34)가 전부 완료됐음에도 "(예정)", "P1 미착수", "Sprint 0~3(P0) 완료"라고 stale하게 기술돼 있었다. 실제 코드 상태(P0+P1+P2 전부 완료)에 맞게 전면 갱신 — OpenAI 연동이 실제로 라이브 동작 중임을 명시, API 목록 표에 JWT 인증이 필요한 두 엔드포인트(`GET /api/audit-logs`, `PATCH /incidents/{id}/resolve`)를 🔒로 표시, 진행상태 섹션을 "P0~P2 전체 완료(v1.0.1)"로 갱신, "한계 및 정직하게 밝힐 부분"에 이번 검증에서 나온 실제 남은 한계(JWT secret 미고정 시 재기동마다 토큰 무효화, `/h2-console` 무인증 노출, 관리자 계정 단일 구조)를 정직하게 추가. (`README.md`)

### Changed
- **(M1, 권장)** `IncidentQueryService.resolve`가 멱등하게 동작하도록 수정 — 이미 `RESOLVED`인 사건을 다시 resolve해도 `resolvedAt`(최초 해결 시각)을 재설정하지 않는다. 재설정을 허용하면 재시도/중복 클릭 등으로 `AnalyticsMapper`의 평균 해결시간(MTTR) 집계가 실제보다 늦은 시각으로 왜곡될 수 있었다. 사람이 관리자 엔드포인트로 직접 트리거하는 조치이므로 `@Auditable(actorType = "OPERATOR", ...)`을 추가해 감사로그에도 남도록 함. 회귀 방지용 `IncidentQueryServiceTest` 신규 추가. (`IncidentQueryService.java`)
- **(M2, 권장)** `JwtAuthenticationFilter`가 보호 대상 라우트를 매칭할 때 `HTTP GET`만 정확히 비교해, `HEAD` 요청이 같은 컨트롤러 메서드를 실제로 실행하면서도 인증 없이 통과되던 문제(`GET /api/audit-logs`는 401인데 `HEAD /api/audit-logs`는 인증 우회로 200) 수정 — `HEAD`는 보호 대상 판정 시 `GET`과 동일하게 취급하도록 함. (`JwtAuthenticationFilter.java`)

## [1.0.0] - 2026-08-09

P1(시간 되면) + P2(시간 남으면만) 전체 완료 — 마스터프롬프트 7장 빌드순서(P0~P2) 전 항목 완성, 전체 17개 이슈 모두 CLOSED(`v7-polish`, `v1.0.0`).

### Added
- `AiSummaryService` 추가 — OpenAI Chat Completions API(`gpt-4o-mini`, Spring 6 `RestClient` + `SimpleClientHttpRequestFactory` 타임아웃 3초)로 Incident 판단근거 자연어 요약(aiSummary)을 생성해 `IncidentActionService.decideAndRecord` 조치 결정 직후 채워 저장. `OPENAI_API_KEY` 미설정 시 네트워크 호출 자체를 건너뛰고, 호출 실패/타임아웃 시에도 예외를 흡수해 기본 템플릿 문장으로 대체하므로 연동 장애가 Incident 생성 흐름을 막지 않음(US-013) (#13)
- JWT 최소 구현으로 관리자 전용 엔드포인트(`GET /api/audit-logs`, `PATCH /api/incidents/{id}/resolve`) 보호 — `POST /api/auth/token`(고정 admin 계정 검증, `admin.password` 프로퍼티)으로 토큰 발급, `JwtTokenProvider`(jjwt, `jwt.secret` 미설정 시 랜덤 키 생성)로 서명·검증, `JwtAuthenticationFilter`(`OncePerRequestFilter`)가 위 두 엔드포인트만 정확히 매칭해 `Authorization: Bearer` 헤더를 강제하고 실패 시 공통 에러 포맷(`{timestamp,status,error,message,path}`)으로 401 응답. Spring Security 풀스택 대신 jjwt+커스텀 필터를 택한 이유는 회원/역할 체계가 없는 이 프로젝트 스케일에 풀스택 도입이 과설계이기 때문(US-014) (#14)
- Swagger UI에 JWT Bearer 토큰 입력 UI(Authorize 버튼) 노출 — `OpenApiConfig`로 `bearerAuth` 시큐리티 스키마 등록, 보호 대상 두 엔드포인트에만 `@SecurityRequirement` 적용 (#14)
- 테스트 코드 보강(US-015) — `IncidentRuleEngineTest`에 파라미터화 테스트 13건 추가해 CPU/MEM/QUEUE_DEPTH/ERROR_RATE 4개 규칙의 LOW/MEDIUM/HIGH/CRITICAL 심각도 구간 경계값을 전부 커버(기존엔 MEM_EXCEEDED 테스트 자체가 없었음). 지금까지 테스트가 없었던 `JwtTokenProviderTest`(발급/검증/만료/시크릿 불일치/형식오류 5건), `AiSummaryServiceTest`(OpenAI 미설정 시 기본 템플릿 폴백 2건) 신규 추가. 기존 `IncidentDetectionConcurrencyTest`(동시요청 10건 재현)는 검토 결과 요구사항을 이미 충족해 그대로 유지 (#15)
- Docker Compose(app+postgres) 원커맨드 기동 추가 — `Dockerfile`(Gradle 빌드 스테이지 → JRE 21 alpine 런타임 스테이지 멀티스테이지 빌드), `docker-compose.yml`(app+postgres 2개 서비스, postgres healthcheck 통과 후 app 기동), PostgreSQL 접속정보는 `application-docker.yml`이라는 별도 프로필로 분리해 기본 프로필(H2, `IncidentDetectionConcurrencyTest`용으로 튜닝된 LOCK_TIMEOUT/HikariCP 설정)에 영향 없음. `.env.example` 추가로 DB_PASSWORD/OPENAI_API_KEY/JWT_SECRET/ADMIN_PASSWORD 등을 커밋 없이 로컬 `.env`로만 주입 (#16)
- 정적 대시보드 페이지(`src/main/resources/static/dashboard.html`, `http://localhost:8080/dashboard.html`) 추가 — Swagger UI 대신 데모/시연용으로 바로 볼 수 있는 vanilla JS 단일 HTML(프레임워크/빌드도구 없음). `GET /api/incidents`(status/severity 필터, 페이지네이션)로 최근 사건 목록을 severity 배지(LOW=회색/MEDIUM=노랑/HIGH=주황/CRITICAL=빨강)와 함께 표시하고, 행 클릭 시 `GET /api/incidents/{id}`로 조치이력(actions)+aiSummary를 상세 패널에 펼쳐 보여준다. 10초 자동 폴링(토글 가능)과 수동 새로고침을 지원하고, 상단에 `GET /api/analytics/resource-health-rank`로 리소스 위험도 랭킹을 함께 노출. Spring Boot 정적 리소스 서빙을 그대로 활용해 별도 설정이 필요 없고, 참조하는 API 4종 모두 공개 엔드포인트라 CORS/인증 이슈 없음(US-017) (#17)

### Changed
- `./gradlew test`가 셸의 `OPENAI_API_KEY`를 테스트 JVM에 전달하지 않도록 `build.gradle`에 고정 — 기존 통합테스트가 매 빌드마다 실제 OpenAI를 호출해 과금·플레이키(flaky)해지는 것을 방지하고 폴백 경로를 항상 검증하게 함 (#13)

## [0.1.0] - 2026-08-09

P0(MVP) 전체 완료 — 언제든 제출 가능한 최소 완결 버전(`v-submittable`).

### Added
- 프로젝트 초기 세팅 (기획 문서, Git 컨벤션, 이슈/PR 템플릿)
- Gradle 기반 Spring Boot 3.3(Java 21) 프로젝트 초기화 — resource/metric/incident/audit/analytics/common 6개 도메인 패키지, H2 인메모리 datasource, MyBatis mapper-locations 기본값 (#1)
- 5개 JPA 엔티티(Resource/MetricSnapshot/Incident/IncidentAction/AuditLog) + Repository 인터페이스 — Incident에 낙관적 락(@Version), Incident-IncidentAction 양방향 연관관계 (#2)
- 지표 시뮬레이터 — `@Scheduled(fixedRate=7000)`로 등록된 전체 리소스에 랜덤 지표 생성, 수동 트리거 API(`POST /api/metrics/simulate`)와 조회 API(`GET /api/metrics/{resourceId}/latest`, `/history`) 추가. 랜덤 값은 대체로 정상 범위이되 10% 확률로 임계치 초과 이상치를 섞어 이상탐지 규칙엔진(US-004)이 자연스럽게 트리거되게 함 (#3)
- 이상탐지 규칙엔진(`IncidentRuleEngine`, if-else 기반)과 `IncidentDetectionService` 추가 — errorRate/cpuUsage/memUsage/queueDepth 임계치 초과 시 Incident 자동 생성, 지표 시뮬레이터 수동/자동 트리거 양쪽에서 호출. 동시성 제어는 동일 resourceId의 Resource 행에 비관적 락(`SELECT ... FOR UPDATE`)을 걸어 "OPEN 사건 조회 → 없으면 생성" 임계구간을 직렬화하는 방식을 채택(H2가 부분 unique 인덱스를 지원하지 않고, Incident.version 낙관적 락은 서로 다른 두 row의 동시 insert를 막지 못해 제외), 락 타임아웃 등 예외 대비 최대 3회 재시도 (#4)
- `IncidentActionService` 추가 — Incident 신규 생성 시 severity(+ruleTriggered)에 따라 조치(MONITOR/ALERT/RESTART/BACKUP/ESCALATE 조합)를 자동 결정·기록하고 status를 DETECTED→ANALYZING으로 전이. HIGH 등급은 ruleTriggered가 CPU/MEM/큐 적체 등 리소스 압박형이면 RESTART, 그 외(에러율 급증 등)는 BACKUP을 권고하도록 분기 (#5)
- AOP 감사로그(`@Auditable` + `AuditLogAspect`) 추가 — `MetricService.simulate`/`IncidentDetectionService.detectAndCreate`/`IncidentActionService.decideAndRecord`의 public 메서드 경계를 가로채 성공/실패 여부를 `AuditLogRecorder`(REQUIRES_NEW 별도 트랜잭션)로 100% 기록하고, 실패 시에도 원래 예외를 그대로 다시 던져 클라이언트 흐름을 막지 않음 (#6)
- 전역 예외처리(`GlobalExceptionHandler` + `@RestControllerAdvice`) 추가 — API 명세서 7장 에러 코드 표대로 400(검증 실패/타입 불일치/`IllegalArgumentException`)·404(`ResponseStatusException`)·409(신설 `ConflictException`)·500(그 외 `Exception`)을 `{timestamp, status, error, message, path}` 공통 포맷으로 매핑. `IncidentDetectionService`의 재시도 소진 예외를 `IllegalStateException`에서 `ConflictException`으로 교체해 409로 정확히 분류되도록 함 (#7)
- Resource/Incident/Audit Log Controller 신규 구현 + 전체 Swagger 문서화 완성 — `POST/GET /api/resources`, `GET /api/incidents`(status/severity/resourceId 필터), `GET /api/incidents/{id}`(resourceId→resourceName 조회 후 조치이력·aiSummary 포함), `PATCH /api/incidents/{id}/resolve`, `GET /api/audit-logs`(actorType/resultStatus/targetType 필터) 추가. 모든 목록 조회는 Spring Data `Page`/`Pageable`로 일관 처리하고, 엔티티 대신 도메인별 `web.dto` record로 응답해 API 전체 시나리오(리소스 등록→지표 이상치 주입→사건 확인→조치이력 상세→해결→감사로그 확인)를 재현 가능하게 함 (#8)
- `AnalyticsMapper`(MyBatis) 기반 집계 엔드포인트 2종 추가 — `GET /api/analytics/incident-summary`(리소스별 사건 건수·심각도 분포·평균 해결시간(분) 집계, `from`/`to`로 detectedAt 기간 필터 optional), `GET /api/analytics/resource-health-rank`(사건 발생 빈도 내림차순 리소스 위험도 랭킹, 사건 없는 리소스도 0건으로 포함). JOIN·GROUP BY·CASE 조건부 SUM·AVG 등 다중 테이블 집계는 JPA 대신 순수 SQL(`AnalyticsMapper.xml`)로만 구현해 "JPA=CRUD, MyBatis=집계" 역할 분리 원칙(PRD 3-1)을 지킴 (#9)
- Actuator 커스텀 헬스 인디케이터(`IncidentEngineHealthIndicator`) 추가 — `GET /actuator/health/incidentEngine`, 최근 5분 내 감지되고 미해결(RESOLVED 아님)인 CRITICAL Incident가 1건이라도 있으면 DOWN, 없으면 UP(상세에 건수 포함). `management.endpoint.health.show-details: always` 설정으로 하위 컴포넌트 상세가 노출되도록 함 (#10)
- README.md 신규 작성 — SK AX×대신증권 사례 배경, 모듈러 모놀리식 ADR, 핵심 시나리오, 규칙엔진(판단)/LLM(설명) 역할 분리 원칙, 동시성 제어·감사로그 원리, 실행 방법, API 목록, 현재 진행 상태(P0 완료=v-submittable), 한계 및 정직하게 밝힐 부분을 포함해 PDF 제출 시 그대로 활용 가능한 수준으로 정리 (#11)
- 기동 시 자동 시드 데이터(`DataSeeder`, `CommandLineRunner`) 추가 — 4개 타입(SERVER/DATABASE/QUEUE/API) 골고루 섞인 리소스 8개(order-service-web/db, payment-service-web/db, payment-queue, notification-queue, user-api, auth-api)와 리소스당 정상범위 지표 스냅샷 2건을 기동 즉시 적재. `./gradlew bootRun` 직후 수동 조작 없이 `GET /api/resources`로 바로 확인 가능. Resource 건수를 먼저 조회해 이미 데이터가 있으면 스킵하는 방식으로 재기동 시 중복 삽입을 방지 (#12)

### Changed
- `MetricService.simulate`에 REQUIRES_NEW 감사로그 트랜잭션이 비관적 락 보유 스레드에서 커넥션을 추가로 요구한다는 점이 동시성 테스트로 드러나 HikariCP `maximum-pool-size`를 기본값(10)에서 30으로 상향 (#6)

### Fixed
- Swagger UI 접근 시 `/swagger-ui/index.html` → `/swagger-ui/swagger-ui/index.html`로 불필요하게 이중 리다이렉트되던 문제 수정 — `springdoc.swagger-ui.path`가 springdoc 기본 서빙 경로와 동일한 값으로 잘못 설정되어 있던 것이 원인. 해당 설정을 제거해 PRD 8장이 요구하는 URL이 리다이렉트 없이 바로 노출되도록 함 (US-012 self-QA 중 발견)
- `IncidentActionServiceTest`의 간헐적 실패(flaky test) 수정 — 테스트가 4개 지표 필드 중 1개만 지정하고 나머지를 null로 두어 `MetricService`의 랜덤 채움 로직(10% 확률 이상치 혼입)에 의존했는데, 드물게 의도치 않은 이상치가 섞여 더 심각한 규칙이 함께 트리거되며 기대와 다른 severity/action이 기록되는 경우가 있었다. 4개 필드를 모두 명시적으로 안전값으로 채우도록 수정해 결정론적으로 만듦(US-012 self-QA 중 반복 빌드 검증으로 발견 — main 기준에도 이미 존재하던 사전 결함이며, DataSeeder 도입과는 무관)
