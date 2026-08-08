# Ops Sentinel

> 가상 인프라 지표를 감시하다가 이상을 감지하면 스스로 사건을 생성·심각도 판정·조치기록·(예정) AI 요약까지 수행하고, 모든 과정을 감사 가능하게 남기는 백엔드 API

SKALA 4기 백엔드 최종 실습 제출물(개인 과제) · Spring Boot 3.3 / Java 21 / Gradle

---

## 목차
1. [배경](#1-배경)
2. [아키텍처](#2-아키텍처)
3. [핵심 시나리오](#3-핵심-시나리오)
4. [판단(규칙엔진) vs 설명(LLM) 역할 분리](#4-판단규칙엔진-vs-설명llm-역할-분리)
5. [실행 방법](#5-실행-방법)
6. [API 목록](#6-api-목록)
7. [동시성 제어](#7-동시성-제어)
8. [감사로그(AOP)](#8-감사로그aop)
9. [현재 진행 상태](#9-현재-진행-상태)
10. [한계 및 정직하게 밝힐 부분](#10-한계-및-정직하게-밝힐-부분)

---

## 1. 배경

2026년 4월, SK AX는 대신증권과 7년 규모의 계약을 맺고 1단계로 모니터링 에이전트·백업 에이전트·장애/상황관리 에이전트를 금융 인프라 운영에 투입했다. 핵심은 사람이 로그를 뒤져 사후에 원인을 찾는 방식이 아니라, 시스템이 이상 징후를 선제적으로 탐지·분석·판단하고 조치까지 수행하는 에이전틱 AIOps 구조로 운영 패러다임을 전환한 것이다. **Ops Sentinel은 이 사례가 제시하는 "지표 감시 → 이상탐지 → 판단 → 조치 → 감사"라는 개념적 파이프라인을, 개인 백엔드 과제로 소화 가능한 스케일로 축소 구현한 프로젝트다.** 실제 물리 인프라 대신 시뮬레이션 지표를 사용하고, 판단은 학습 모델이 아닌 규칙 기반 엔진이 맡으며, LLM은 그 판단을 사람이 읽을 수 있는 자연어로 설명하는 보조 역할에 한정한다. 화려한 기능 나열보다 "왜 이런 판단을 내렸는지 사람이 사후에 검증할 수 있는가"라는 감사 가능성(auditability)에 설계의 무게중심을 두었다.

이 배경 설명과 설계 결정의 근거는 학술 논문·업계 표준 문서·언론 보도로 교차검증했다(자세한 출처와 비판적 평가는 `docs/07_배경조사_근거자료집.md` 참고).

| 우리 프로젝트의 설계 | 근거 출처 |
|---|---|
| 지표(CPU/메모리/에러율/큐길이) 수집 구조 | Google SRE Book, Four Golden Signals |
| 이상탐지 → 근본원인 → 자동조치 파이프라인 | 베이징대 AIOps 서베이 논문(arXiv:2406.11213) |
| 규칙기반 판단 + LLM은 설명만 담당 | FINOS AI 거버넌스 프레임워크, Tier 2 감사 요구사항 |
| OpenAI 실패 시 폴백 처리 | Zalando Engineering Blog, AI 사후분석 2년 운영 사례의 한계 인정 |
| 전체 기획 동기 | SK AX × 대신증권 7년 계약(2026.4) |

## 2. 아키텍처

### 2-1. 왜 모듈러 모놀리식인가 (ADR 요약)

| 옵션 | 채택 여부 | 근거 |
|---|---|---|
| **모듈러 모놀리식**(도메인별 패키지 분리, 단일 배포단위) | ✅ 채택 | 혼자·제한된 시간 안에 완성해야 하는 조건에서 서비스 간 통신·분산 트랜잭션 문제 없이도 "구조가 잘 나뉘어 있다"는 신호를 줄 수 있는 현실적인 선택 |
| MSA(서비스 완전 분리) | ❌ 기각 | 서비스 간 통신·분산 트랜잭션·배포 파이프라인까지 혼자 감당하기엔 리스크 대비 이득이 작음 |
| 단순 레이어드 모놀리식(도메인 구분 없이 Controller/Service/Repository만) | ❌ 기각 | 도메인 경계가 드러나지 않아 구조적 차별점이 없음 |

### 2-2. 패키지 구조

도메인별로 패키지를 나누고, 각 도메인 내부는 다시 `web(Controller) / service / repository / entity` 계층으로 분리했다. 도메인 간에는 직접 참조를 최소화해, 필요해지면 패키지 단위로 서비스를 쪼개기 쉬운 형태를 유지한다.

```
src/main/java/com/opssentinel
├── resource   # 인프라 리소스(서버/DB/큐/API) 등록·조회
├── metric     # 지표 시뮬레이션·조회
├── incident   # 이상탐지 규칙엔진, 사건 생성·조회·해결, 커스텀 헬스 인디케이터
├── audit      # AOP 감사로그(@Auditable, Aspect, Recorder)
├── analytics  # MyBatis 기반 집계 API
└── common     # 전역 예외처리, 공통 응답 DTO
```

### 2-3. JPA(CRUD) + MyBatis(집계) 역할 분리

- 단건 CRUD, 상태 전이, 락이 필요한 조회(`SELECT ... FOR UPDATE`)는 **Spring Data JPA**로 처리한다. `Resource`, `MetricSnapshot`, `Incident`, `IncidentAction`, `AuditLog` 5개 엔티티가 대상이다.
- 여러 테이블을 JOIN하고 `GROUP BY`, `CASE`, `AVG` 등으로 집계해야 하는 통계성 조회(`/api/analytics/*`)는 **MyBatis**(`AnalyticsMapper.xml`)로 순수 SQL을 직접 작성한다. JPA로도 구현할 수는 있지만, 다중 테이블 집계를 억지로 JPQL/Criteria로 표현하면 가독성이 떨어지고 실행 계획을 예측하기 어려워지므로 역할을 명확히 나눴다.

## 3. 핵심 시나리오

```
운영자                 스케줄러/API              규칙엔진               Incident/Action           AOP 감사로그
  |                        |                       |                        |                        |
  | 리소스 등록 -----------> POST /api/resources    |                        |                        |
  |                        |                       |                        |                        |
  |                 (7초 주기) 지표 시뮬레이션 생성   |                        |                        |
  |                 or POST /api/metrics/simulate ->|                        |                        |
  |                        |                       |                        |                        |
  |                        |  임계치 초과 판단 ----->| Resource 행 비관적 락  |                        |
  |                        |                       | -> OPEN 사건 존재? ---> | 없으면 Incident 생성    |
  |                        |                       |                        | -> 조치(Action) 자동기록|
  |                        |                       |                        |                        |
  |                        |                       |          모든 단계  --------------------------->| AuditLog 기록
  |                        |                       |          (성공/실패 무관, REQUIRES_NEW)          | (SUCCESS/FAIL)
  |                        |                       |                        |                        |
  | GET /api/incidents/{id} 조회 (조치이력 포함) <----------------------------                        |
  | PATCH /resolve 처리 ------------------------------------------------->|                        |
```

1. 운영자가 `POST /api/resources`로 서버/DB/큐/API 타입 리소스를 등록한다.
2. `@Scheduled(fixedRate=7000)` 스케줄러가 등록된 리소스 전체에 랜덤 지표(CPU/메모리/에러율/큐길이)를 생성한다. 약 10% 확률로 임계치를 넘는 이상치를 섞어 규칙엔진이 자연스럽게 트리거되도록 했다. `POST /api/metrics/simulate`로 수동 트리거도 가능하다(데모용).
3. `IncidentRuleEngine`이 지표를 검사해 임계치 초과 여부와 심각도를 판정하고, `IncidentDetectionService`가 Resource 행에 비관적 락을 건 뒤 중복 여부를 확인해 `Incident`를 생성한다.
4. `IncidentActionService`가 심각도와 트리거된 규칙에 따라 조치(MONITOR/ALERT/RESTART/BACKUP/ESCALATE 조합)를 자동 결정·기록하고 상태를 `DETECTED → ANALYZING`으로 전이한다.
5. (예정, P1) OpenAI API로 "왜 이 조치를 했는지" 1~2문장 자연어 요약을 생성해 `aiSummary`에 저장한다.
6. 위 모든 단계는 `@Auditable` + AOP `@Around`가 가로채 `AuditLog`에 성공/실패 여부와 함께 100% 기록한다.
7. 운영자는 `GET /api/incidents`, `GET /api/incidents/{id}`, `GET /api/analytics/*`로 사건과 통계를 조회하고, 처리가 끝난 사건은 `PATCH /api/incidents/{id}/resolve`로 종료 처리한다.

## 4. 판단(규칙엔진) vs 설명(LLM) 역할 분리

이 프로젝트에서 **"판단"은 전적으로 규칙 기반 엔진(`IncidentRuleEngine`)이 담당한다.** 지표가 임계치(예: 에러율 5% 이상, CPU 90% 이상)를 넘었는지, 심각도를 LOW/MEDIUM/HIGH/CRITICAL 중 무엇으로 매길지, 어떤 조치(MONITOR/ALERT/RESTART/BACKUP/ESCALATE)를 취할지는 모두 if-else 기반 규칙으로 결정되며 결정론적이고 재현 가능하다.

LLM(OpenAI API, P1 예정)은 이 판단 자체를 바꾸지 않는다. 규칙엔진이 이미 내린 결정을 사람이 읽기 쉬운 자연어 1~2문장으로 사후 설명하는 역할만 맡으며, API 호출이 실패하거나 타임아웃(3초)이 발생해도 미리 정의한 폴백 문장으로 대체되어 전체 흐름을 막지 않는다.

이렇게 역할을 나눈 이유는 "이게 진짜 AI냐"는 과장 논란을 피하기 위해서다. 판단 로직을 LLM에 맡기면 설명력과 재현성이 떨어지고 테스트도 어려워진다. FINOS(Fintech Open Source Foundation)의 AI 거버넌스 프레임워크가 제시하는 감사 요구사항 중 "Tier 2: 명시적 추론이 도구 호출 전에 생성·기록되어야 하며 자연어 설명을 포함해야 한다"는 원칙과도 맞닿아 있다. 즉 규칙엔진의 결정(Decision)이 먼저이고, LLM의 설명(Explanation)은 그 뒤를 따르는 부가 정보라는 순서를 지킨다.

> **현재 구현 상태**: 규칙엔진 기반 판단·조치기록까지는 구현 완료. OpenAI 연동(`aiSummary` 자동 생성)은 P1 항목으로 아직 미착수 상태이며, 현재 `Incident.aiSummary`는 비어 있거나 향후 연동 전까지는 값이 채워지지 않는다.

## 5. 실행 방법

### 요구 사항
- JDK 21
- 별도 DB 설치 불필요 — H2 인메모리 DB를 기본으로 사용

### 빌드 및 실행

```bash
# 빌드(테스트 포함)
./gradlew build

# 서버 실행 (기본 포트 8080)
./gradlew bootRun
```

### H2 콘솔 접속

1. 서버 실행 후 브라우저에서 `http://localhost:8080/h2-console` 접속
2. JDBC URL: `jdbc:h2:mem:opssentinel` / Username: `sa` / Password: (공백)

### (선택) Docker Compose로 한 번에 실행 — app + PostgreSQL

Gradle 로컬 실행 대신, PostgreSQL까지 포함해 컨테이너로 한 번에 띄우고 싶다면:

```bash
# 1) 환경변수 파일 준비 (DB_PASSWORD 등 값 채우기)
cp .env.example .env

# 2) app(Dockerfile 빌드) + postgres 함께 기동
docker compose up -d --build

# 3) 종료
docker compose down
```

- app 컨테이너는 `SPRING_PROFILES_ACTIVE=docker`로 뜨며 `application-docker.yml`(PostgreSQL 접속 설정)을 사용한다. 기본 프로필(H2)과는 완전히 분리되어 있어 `./gradlew test` 등 기존 H2 기반 테스트에는 영향이 없다.
- postgres 컨테이너의 healthcheck를 통과한 뒤에야 app이 기동된다(`depends_on: condition: service_healthy`).
- 데이터는 `postgres-data` 볼륨에 영속화되어 `docker compose down` 후에도 유지된다(볼륨까지 지우려면 `docker compose down -v`).
- 기동 후 접속 경로는 아래 Gradle 실행과 동일하다(`http://localhost:8080/...`).

### Swagger UI

`http://localhost:8080/swagger-ui/index.html` — 전체 API를 문서화된 형태로 확인하고 직접 호출해볼 수 있다.

### 헬스체크

`http://localhost:8080/actuator/health` — 기본 헬스체크. 하위 컴포넌트 상세는 `show-details: always` 설정으로 함께 노출된다(예: `/actuator/health` 응답 안의 `incidentEngine` 컴포넌트).

## 6. API 목록

### Resource

| Method | Path | 설명 |
|---|---|---|
| POST | `/api/resources` | 리소스 등록(`{name, type}`, type: `SERVER/DATABASE/QUEUE/API`) |
| GET | `/api/resources` | 리소스 목록 조회 (필터: `type`, 페이징) |
| GET | `/api/resources/{id}` | 리소스 단건 조회 |

### Metric

| Method | Path | 설명 |
|---|---|---|
| POST | `/api/metrics/simulate` | 수동 지표 생성(데모용, 필드 미지정 시 랜덤 값) |
| GET | `/api/metrics/{resourceId}/latest` | 최신 지표 조회 |
| GET | `/api/metrics/{resourceId}/history` | 지표 이력 조회(`from`, `to`) |

### Incident

| Method | Path | 설명 |
|---|---|---|
| GET | `/api/incidents` | 사건 목록 조회 (필터: `status`, `severity`, `resourceId`, 페이징) |
| GET | `/api/incidents/{id}` | 사건 상세 조회(조치 이력 + AI 요약 포함) |
| PATCH | `/api/incidents/{id}/resolve` | 사건 해결 처리 |

### Analytics (MyBatis 집계)

| Method | Path | 설명 |
|---|---|---|
| GET | `/api/analytics/incident-summary` | 리소스별 사건 건수·심각도 분포·평균 해결시간(분) 집계 (`from`/`to` 기간 필터 optional) |
| GET | `/api/analytics/resource-health-rank` | 사건 발생 빈도 기준 리소스 위험도 랭킹(사건 없는 리소스도 0건으로 포함) |

### Audit Log

| Method | Path | 설명 |
|---|---|---|
| GET | `/api/audit-logs` | 전체 감사로그 조회 (필터: `actorType`, `resultStatus`, `targetType`, 페이징) |

### Actuator

| Path | 설명 |
|---|---|
| `/actuator/health` | 기본 헬스체크 |
| `/actuator/health/incidentEngine` | 커스텀: 최근 5분 내 감지되고 미해결(RESOLVED 아님)인 CRITICAL 사건이 있으면 DOWN |

### 공통 에러 응답

| 코드 | 상황 |
|---|---|
| 400 | 잘못된 요청 파라미터/검증 실패 |
| 404 | 존재하지 않는 리소스/사건 |
| 409 | 동시성 충돌(중복 사건 생성 시도 등) |
| 500 | 서버 내부 오류 |

## 7. 동시성 제어

**"동일 리소스에 중복 사건이 생성되지 않는다"**는 이 프로젝트의 핵심 동시성 제어 지점이다. 동일 `resourceId`에 여러 요청이 거의 동시에 임계치 초과 지표를 만들어내면, 각 요청 스레드가 동시에 "OPEN 사건이 있는지 조회 → 없으면 새로 생성"하는 임계구간에 진입할 수 있고, 이 경우 조회 시점에는 둘 다 "OPEN 사건 없음"으로 판단해 중복 Incident가 생성될 수 있다.

`IncidentDetectionService`는 이 임계구간을 **동일 `resourceId`의 `Resource` 행에 대한 비관적 락(`SELECT ... FOR UPDATE`)**으로 직렬화해서 막는다. 락을 획득한 스레드만 "OPEN 사건 조회 → 없으면 생성"을 수행하고, 나머지 스레드는 락이 풀릴 때까지 대기했다가 순차적으로 같은 검사를 반복하므로 중복 생성이 발생하지 않는다. 락 타임아웃 등 예외 상황에 대비해 최대 3회까지 재시도하며, 재시도가 모두 소진되면 `ConflictException`(409)으로 응답한다.

애초 계획은 `Incident.version` 필드를 이용한 낙관적 락이었지만, 다음 이유로 비관적 락으로 전환했다.
- H2가 부분 unique 인덱스(`resourceId + status=OPEN`)를 지원하지 않아 DB 제약만으로는 중복을 막을 수 없었다.
- `Incident.version`에 거는 낙관적 락은 이미 존재하는 같은 row를 다시 쓸 때만 충돌을 감지할 뿐, **서로 다른 두 개의 새 row가 동시에 insert되는 상황 자체는 막지 못한다** — 애초에 검사 시점에 "OPEN 사건이 없다"고 두 스레드가 동시에 판단하기 때문이다.

이 때문에 검사와 생성을 감싸는 임계구간 자체를 Resource 행 락으로 직렬화하는 방식을 채택했다.

## 8. 감사로그(AOP)

모든 처리 결과(성공/실패 무관)를 사람이 사후에 검증할 수 있도록 `AuditLog`에 남긴다. 이를 위해 도메인 서비스 코드 안에 로깅 코드를 직접 흩뿌리지 않고, `@Auditable` 커스텀 애노테이션 + AOP `@Around` 어드바이스(`AuditLogAspect`)로 관심사를 분리했다.

- `@Auditable`이 붙은 메서드(`MetricService.simulate`, `IncidentDetectionService.detectAndCreate`, `IncidentActionService.decideAndRecord`)의 호출을 `AuditLogAspect`가 가로챈다.
- 대상 메서드가 정상 반환하면 `AuditLogRecorder.recordSuccess(...)`를, 예외를 던지면 `recordFailure(...)`를 호출해 `AuditLog`를 저장한다. 이때 원래 예외는 그대로 다시 던져(rethrow) 호출자(컨트롤러 등)의 정상적인 에러 처리 흐름을 막지 않는다.
- `AuditLogRecorder`는 `@Transactional(propagation = REQUIRES_NEW)`로 **별도의 새 트랜잭션**에서 동작한다. 대상 메서드가 속한 원래 트랜잭션이 실패해서 롤백되더라도, 감사로그 저장은 이미 커밋된 별도 트랜잭션이므로 롤백되지 않고 남는다 — "처리 로직 자체가 실패해도 감사로그는 남아야 한다"는 요구사항을 만족시키는 핵심 장치다.

부가로, 이 REQUIRES_NEW 트랜잭션이 비관적 락을 보유한 스레드에서 커넥션을 하나 더 요구한다는 점이 동시성 테스트 과정에서 드러나, HikariCP `maximum-pool-size`를 기본값(10)에서 30으로 상향 조정했다(`application.yml` 참고).

## 9. 현재 진행 상태

**Sprint 0~3 (P0) 완료 — 제출 가능한 최소 완결 버전(v-submittable)** 이다. 지표 시뮬레이션부터 이상탐지, 사건/조치 자동 기록, AOP 감사로그, 전역 예외처리, Resource/Incident/Analytics/AuditLog 전체 API, MyBatis 집계, Actuator 커스텀 헬스 인디케이터까지 API 호출만으로 전체 시나리오를 재현할 수 있는 상태다.

**P1 (시간 되면)**
- OpenAI API 연동 — Incident 발생 시 규칙/지표를 프롬프트로 넣어 `aiSummary` 자동 생성(현재는 미착수, 위 4장 참고)
- JWT 최소 구현(관리자 엔드포인트 보호)
- 테스트 코드 보강(동시성 재현 테스트, 이상탐지 로직 단위테스트)

**P2 (시간 남으면만)**
- Docker Compose(app+DB)
- 정적 대시보드 페이지(최근 사건 목록을 보여주는 단일 HTML)

## 10. 한계 및 정직하게 밝힐 부분

- 본 프로젝트는 **실제 인프라가 아닌 시뮬레이션 데이터** 기반이다. 학술 논문이나 FINOS 문서가 전제하는 "실제 프로덕션 규모의 로그·트래픽"을 다루지 않는다.
- OpenAI API 활용은(P1, 아직 미연동) "판단"이 아니라 어디까지나 **사후 자연어 설명 생성**에 국한한다. 이는 기술적 한계가 아니라, 판단의 신뢰성·재현성을 지키고 과장을 막기 위한 의도적인 설계다.
- 배경 지식으로 참고한 SK AX × 대신증권 사례는 공식 뉴스룸(1차 소스) URL을 확인했으나, 이 저장소 작업 과정에서 원문을 직접 캡처하지는 못했고 대신 4개 이상의 독립 언론 보도로 계약 규모·1단계 적용범위(모니터링/백업/장애관리 에이전트)를 교차검증했다. 관련 상세 출처와 등급 평가는 `docs/07_배경조사_근거자료집.md`의 C장에 정리되어 있다.
- MSA 분리, K8s, 실서버(AWS 등) 배포는 범위에서 제외했다. 시간이 제한된 개인 과제에서 리스크 대비 실익이 낮다고 판단했기 때문이며, `docs/01_PRD_기획명세서_최종본.md` 3-1장에 그 근거를 기록해 두었다.
