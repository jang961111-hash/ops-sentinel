ralph: 너는 지금부터 시니어 백엔드 아키텍트 + AIOps 엔지니어 역할로, SKALA 백엔드 최종 실습 과제를 완성한다. 마감은 유연하니 완성도를 우선하되, 중간에 멈추지 말고 아래 PRD·빌드순서·Git 워크플로우를 그대로 실행해라. 작업물이 절대 유실되지 않도록 "9. Git 워크플로우"를 문자 그대로 지켜라 — 이건 선택이 아니라 필수 규칙이다.

**0단계 (제일 먼저, 반드시 실행)**: 현재 폴더에 `.git`이 없으면 `git init`. `gh auth status`로 GitHub CLI 로그인 확인 후, `gh repo create ops-sentinel --private --source=. --remote=origin` (이미 원격 있으면 스킵). `main` 브랜치를 기본으로 하고 최초 커밋(`chore: init project`)을 push 해서 원격 저장소부터 살아있게 만들어라.

---

# PRD — Ops Sentinel: 에이전틱 인프라 감시 & 감사 API

## 1. 배경 (Why)
SK AX가 2026.4 대신증권과 7년 계약을 맺고 1단계로 투입한 것이 **모니터링 에이전트 / 백업 에이전트 / 장애·상황관리 에이전트**다. "선제적으로 문제를 탐지·분석·판단하고 조치까지 완료"하는 것이 핵심. 이 개념 구조를 개인 백엔드 과제 스케일로 축소 구현한다. 목표는 화려한 기능 나열이 아니라 "왜 이런 판단을 했는지 사람이 검증할 수 있는" 감사 가능한 백엔드.

## 2. 목표 / 비목표
- 목표: JPA+MyBatis 분리, 동시성 제어, AOP 감사로그, 규칙기반 이상탐지 + LLM 요약, Swagger 문서화
- 비목표(오늘은 안 함): MSA 분리, K8s, 실서버 배포, 정식 프론트엔드, 실제 물리 인프라 연동(전부 시뮬레이션 데이터)

## 3. 핵심 시나리오 (User Story)
1. 시스템은 가상의 서비스 리소스(CPU/메모리/큐 길이/에러율) 데이터를 주기적으로 생성·기록한다.
2. 스케줄러가 주기적으로 최신 지표를 스캔해 임계치 초과 여부를 판단한다 (예: 에러율 5% 이상, CPU 90% 이상).
3. 임계치 초과 시 **IncidentAgent**가 발동: 사건(Incident) 레코드 생성 → 심각도 판정(규칙기반) → 조치 로그(Action) 기록 → OpenAI API로 "왜 이 조치를 취했는지" 1~2문장 자연어 요약 생성해 Incident에 저장 (API 실패해도 기본 템플릿 문장으로 대체, 앱 절대 안 죽음).
4. 모든 사건/조치는 별도 트랜잭션(AOP `@Around`)으로 감사로그(AuditLog)에 성공/실패 여부와 함께 100% 기록된다 — 사건 처리 로직 자체가 실패해도 감사로그는 남는다.
5. 운영자는 API로 사건 목록, 상세 조회(조치 이력+AI 요약 타임라인), 리소스 지표 추이(집계는 MyBatis)를 확인한다.
6. 재고/포인트류 동시성 이슈 대신, **동일 리소스에 대한 중복 사건 생성 방지**(비관적 락 or unique 제약 + 재시도)를 동시성 제어 포인트로 구현한다.

## 4. 도메인 모델 (엔티티)
- `Resource` (id, name, type[SERVER/DB/QUEUE/API], createdAt)
- `MetricSnapshot` (id, resourceId, cpuUsage, memUsage, errorRate, queueDepth, capturedAt) — 시뮬레이션 데이터, 5~10초 간격으로 스케줄러가 랜덤/패턴 생성
- `Incident` (id, resourceId, severity[LOW/MEDIUM/HIGH/CRITICAL], status[DETECTED/ANALYZING/RESOLVED], ruleTriggered, aiSummary, detectedAt, resolvedAt, version — 낙관락)
- `IncidentAction` (id, incidentId, actionType[MONITOR/BACKUP/RESTART/ALERT/ESCALATE], executedBy["SYSTEM_AGENT"], result, executedAt)
- `AuditLog` (id, actorType, action, targetType, targetId, requestSummary, resultStatus[SUCCESS/FAIL], errorMessage, createdAt) — AOP가 자동 기록, 사람이 손대지 않음

## 5. API 스펙 (필수)
- `POST /api/resources` , `GET /api/resources`
- `POST /api/metrics/simulate` (수동으로 지표 튐 유발 — 데모/테스트용, 시연 시 바로 사건 발생시켜 보여주기 위함)
- `GET /api/incidents` (필터: status, severity, resourceId, 페이징)
- `GET /api/incidents/{id}` (조치 이력 + AI 요약 타임라인 포함)
- `PATCH /api/incidents/{id}/resolve`
- `GET /api/analytics/incident-summary` (MyBatis — 리소스별/기간별 사건 집계, 평균 심각도, MTTR 유사 지표)
- `GET /api/audit-logs` (관리자용, 페이징)
- `GET /actuator/health`, `/actuator/health/incidentEngine` (커스텀 HealthIndicator — 최근 N분 내 미해결 CRITICAL 사건 있으면 DOWN)

## 6. 기술 스택 (고정)
Spring Boot 3.2+, Java 21, Gradle, PostgreSQL(로컬 Docker 1개 컨테이너 or H2 fallback — 시간 없으면 H2로 시작해서 나중에 전환), Spring Data JPA(CRUD) + MyBatis(집계 쿼리), Spring Scheduling, Spring AOP, springdoc-openapi(Swagger), Spring Actuator, OpenAI Java SDK 또는 WebClient로 직접 REST 호출(gpt-4o-mini 권장 — 저렴하고 충분), JWT는 최소 구현(관리자 엔드포인트만 보호, 시간 부족하면 스킵 가능 — P1)

## 7. 빌드 순서 (git commit 단위로 진행, 각 단계 완료 즉시 커밋)

**P0 (반드시 완성, 시간 90% 여기 배분)**
1. 프로젝트 초기화 (Gradle, 패키지 구조: `resource`, `metric`, `incident`, `audit`, `analytics`, `common`)
2. 엔티티 + JPA Repository (H2로 우선 시작)
3. 지표 시뮬레이터 (스케줄러, `@Scheduled(fixedRate=...)`) + 수동 트리거 API
4. 이상탐지 규칙엔진 (단순 if-else/전략패턴, 과하게 설계하지 말 것) + Incident 생성 + 동시성 제어(중복사건 방지 낙관락+재시도)
5. IncidentAction 자동 기록 로직
6. AOP 감사로그 (`@Around`, 별도 트랜잭션 `REQUIRES_NEW`, 성공/실패 모두 기록)
7. 전역 예외처리 (`@RestControllerAdvice`, 일관된 에러 응답 포맷)
8. Controller 전체 + Swagger 문서화
9. MyBatis 집계 쿼리 (analytics 엔드포인트)
10. Actuator 커스텀 HealthIndicator
11. README 작성 (아키텍처 설명, 실행 방법, API 목록)
12. 시드 데이터 (resource 5~10개, 초기 metric 몇 개)

**P1 (시간 되면)**
13. OpenAI API 연동 — Incident 발생 시 규칙/지표를 프롬프트로 넣어 1~2문장 요약 생성 (`OPENAI_API_KEY` 환경변수, 실패시 fallback 문장, 타임아웃 3초 짧게 설정해서 전체 흐름 안 막게)
14. JWT 최소 구현 (관리자 엔드포인트 보호)
15. 테스트 코드 (동시성 재현 테스트 1개, 이상탐지 로직 단위테스트 몇 개)

**P2 (시간 남으면만)**
16. Docker Compose (app+postgres)
17. 정적 대시보드 페이지 1개 (Swagger 대신 볼 것 — vanilla JS로 최근 사건 목록만 보여주는 단일 HTML)

## 8. 완료 기준 (self-QA, 커밋 전에 스스로 체크)
- [ ] `./gradlew build` 성공
- [ ] 서버 기동 후 `/api/metrics/simulate` 호출 → Incident 생성 확인
- [ ] `/api/incidents/{id}` 조회 시 조치이력+AI요약(또는 fallback) 확인
- [ ] 동시 요청으로 중복 사건 생성 안 되는지 확인 (curl 반복 or 간단 부하테스트)
- [ ] AuditLog에 실패 케이스도 기록되는지 확인 (일부러 잘못된 요청 보내보기)
- [ ] Swagger UI(`/swagger-ui/index.html`) 정상 노출
- [ ] README에 "이 프로젝트가 SK AX 대신증권 사례(2026.4)의 AIOps 개념을 개인 과제 스케일로 구현한 것"이라는 배경 설명 한 단락 포함 — PDF 제출 시 그대로 활용 가능하게

## 9. Git/협업 컨벤션 (국내 IT 대기업 실무 표준 — 토스/카카오/네이버/라인/당근 공통분모 기준)

이 프로젝트는 "혼자 만들지만 진짜 팀 프로젝트처럼" 기록을 남긴다. 아래 컨벤션은 국내 주요 테크기업들이 공통으로 쓰는 GitHub Flow + Conventional Commits + Issue 연동 표준을 그대로 따른다.

**0) 저장소 초기 세팅 (0단계 직후 바로 실행)**
- `.github/PULL_REQUEST_TEMPLATE.md` 생성: `## 배경(Why)` / `## 변경사항(What)` / `## 테스트 방법(How)` / `## 체크리스트` 섹션 포함
- `.github/ISSUE_TEMPLATE/feature.md` 생성: 빌드순서(7장)의 항목 하나하나를 **GitHub Issue로 먼저 등록**한다 (`gh issue create --title "..." --body "..."`) — 실무에서 "티켓 없는 작업 없다"는 원칙을 그대로 구현
- `CHANGELOG.md` 생성 (Keep a Changelog 포맷) — 태그 찍을 때마다 갱신
- `.editorconfig`, `.gitignore`(Spring Boot 표준) 정리

**1) 브랜치 전략 — GitHub Flow (트렁크 기반, 단명 브랜치)**
- `main`은 항상 빌드·실행 가능한 상태만 유지, 직접 push 금지 — 전부 PR로만 반영
- 브랜치명: `{type}/{issue번호}-{짧은-설명}` (예: `feat/12-incident-detection`, `fix/18-optimistic-lock-retry`) — 카카오·토스 공통으로 쓰는 이슈번호 연동 네이밍
- 브랜치 생명주기는 짧게(가능하면 1시간 이내) — 오래 끌면 그 전에 쪼개서 먼저 머지

**2) 커밋 컨벤션 — Conventional Commits + 당근/토스식 "Why 중심" 본문**
- 형식: `type(scope): subject` — subject는 명령형, 50자 이내, 마침표 없음. 예) `feat(incident): 낙관적 락 기반 중복 사건 생성 방지`
- type: `feat / fix / refactor / test / docs / chore / perf / style`
- **본문(body)에는 "무엇을 했는지"가 아니라 "왜 이렇게 했는지"를 쓴다** — 토스/당근 테크블로그가 반복 강조하는 원칙. 예: "비관적 락 대신 낙관적 락을 선택한 이유: 사건 생성은 충돌 빈도가 낮고 재시도 비용이 락 대기비용보다 싸기 때문"
- 푸터에 이슈 연결: `Closes #12`
- **15~20분마다, 혹은 안전하게 멈출 수 있는 지점마다 커밋 + `git push`** — 컴파일 안 되는 중간상태는 머지 전 브랜치에서만 `wip:` 접두사로 허용, main에는 절대 안 들어가게

**3) PR + 코드리뷰(셀프 리뷰 체제) + 머지**
1. 브랜치 push → `gh pr create --title "[Feat] 이슈제목" --body-file` (템플릿 채워서) `--body "Closes #N"`
2. **셀프 코드리뷰**: PR diff를 스스로 한 번 다시 읽고, "다른 팀원이 이 코드를 처음 본다면 이해되는가"를 기준으로 체크 — 실무 리뷰 문화를 혼자서도 흉내
3. self-QA 체크리스트(8장) 통과 확인 후 `gh pr merge --squash --delete-branch` (squash로 main 히스토리는 기능 단위로 깔끔하게 유지)
4. 머지 후 연결된 Issue 자동 close 확인

**4) 릴리즈 태깅 — Semantic Versioning**
- P0 완료(빌드순서 12번) → `v0.1.0` (MVP), P1 완료 → `v0.2.0`, P2까지 → `v1.0.0`
- 태그마다 `git tag -a v0.1.0 -m "..." && git push --tags`, `CHANGELOG.md`에 Added/Changed/Fixed 섹션 갱신
- `v0.1.0`이 곧 "언제든 제출 가능한 최소버전" — 이후 실패해도 여기로 롤백

**진행 방식**
- 막히는 부분은 스스로 대안을 찾아 진행하고, 정말 판단이 필요한 것만 짧게 질문
- 각 PR 머지 후 `git log --oneline --graph -10`으로 히스토리가 의도대로 쌓이는지 스스로 확인
- 마지막에 전체 실행 로그/스크린샷 캡처 방법을 안내(Swagger UI 스크린샷, 사건 생성→조회 흐름 스크린샷) — PDF 제출용
- 커밋/PR/이슈 전부가 "협업 기록"이자 제출 PDF에 그대로 캡처해 넣을 수 있는 자산이므로, 실제 팀원에게 설명하듯 구체적으로 작성할 것
