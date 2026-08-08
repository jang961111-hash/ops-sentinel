# US-019 캡처 매니페스트

`docs/08_최종PDF_생성_프롬프트.md` [3]본문 1~11 순서에 따라 실제 로컬 서버(`./gradlew bootRun`)와
실제 GitHub 화면에서 직접 확보한 캡처 목록이다. 지어낸 화면 없음 — 전부 이 세션에서 실행한
curl/gh/git/gradle 명령의 실제 응답을 다크테마 터미널 스타일(브라우저 캡처는 라이트테마,
뷰포트 1440×900, deviceScaleFactor 2)로 렌더링했다. 원본 텍스트 로그는 `raw-logs/`에 함께 있다.

## 실측 수치 (표지용)

| 항목 | 값 | 근거 |
|---|---|---|
| 엔드포인트 개수 | **13개** | `GET /v3/api-docs` paths 집계 (Auth 1 + Resource 3 + Metric 3 + Incident 3 + Analytics 2 + Audit Log 1) |
| 테스트 건수 | **45건, 0 실패, 0 무시** | `./gradlew clean test` 프레시 실행 + `build/reports/tests/test/index.html` |
| 이슈 개수 | **17개 (전체 CLOSED)** | `gh issue list --state closed` |
| PR 개수 | **21개 (전체 MERGED)** | `gh pr list --state merged` |
| 태그 개수 | **12개** | `git tag` |

## 동시성 테스트 실측

- 동일 resourceId로 `POST /api/metrics/simulate` **10건 동시 전송**
- HTTP 201(요청 처리 성공) **10건 / 10건**
- 실제 생성된 OPEN(DETECTED/ANANLYZING) Incident **1건**
- 결과: **PASS** — 동시요청 10건에도 불구하고 Incident는 정확히 1건만 생성됨 (`08-concurrency-test-terminal.png`)

## 하이퍼링크 검증 (표지용 5개, 전부 HTTP 200)

| 링크 | 결과 |
|---|---|
| https://github.com/jang961111-hash/ops-sentinel | 200 |
| https://github.com/jang961111-hash/ops-sentinel/releases/tag/v1.0.0 | 200 |
| https://github.com/jang961111-hash/ops-sentinel/issues?q=is%3Aissue+is%3Aclosed | 200 |
| https://github.com/jang961111-hash/ops-sentinel/pulls?q=is%3Apr+is%3Amerged | 200 |
| https://github.com/jang961111-hash/ops-sentinel#readme | 200 |

## 캡처 파일 ↔ 본문 섹션 매핑 (US-020 참고용)

| 파일명 | 본문 섹션(doc [3] 순서) | 내용 |
|---|---|---|
| `01-swagger-overview.png` | 3. 도메인 모델 & API 스펙 | Swagger UI 전체 엔드포인트 목록(13개) + 스키마, 풀페이지, 라이트테마 |
| `02-api-resource-success.png` | 3. 도메인 모델 & API 스펙 | POST /api/resources 성공(201) |
| `02-api-resource-fail.png` | 3. 도메인 모델 & API 스펙 | POST /api/resources 검증 실패(400, name 공백) |
| `03-api-metric-success.png` | 3. 도메인 모델 & API 스펙 | POST /api/metrics/simulate 성공(201) |
| `03-api-metric-fail.png` | 3. 도메인 모델 & API 스펙 / 6. AOP 감사로그 검증 | POST /api/metrics/simulate 존재하지 않는 resourceId(404) — 감사로그 의도적 실패 케이스로 재사용 |
| `04-api-incident-fail-notfound.png` | 3. 도메인 모델 & API 스펙 | GET /api/incidents/{id} 존재하지 않는 id(404) |
| `04-api-incident-resolve-fail-noauth.png` | 3. 도메인 모델 & API 스펙 | PATCH /api/incidents/{id}/resolve 토큰 없음(401) — JWT 보호 검증 |
| `05-api-analytics-success.png` | 3. 도메인 모델 & API 스펙 | GET /api/analytics/incident-summary 성공(200) |
| `05-api-analytics-fail.png` | 3. 도메인 모델 & API 스펙 | GET /api/analytics/incident-summary 잘못된 파라미터(400) |
| `06-api-auditlog-fail-noauth.png` | 3. 도메인 모델 & API 스펙 | GET /api/audit-logs 토큰 없음(401) |
| `06-api-auditlog-success.png` | 3. 도메인 모델 & API 스펙 | GET /api/audit-logs 관리자 토큰으로 성공(200) |
| `07-scenario-step1-register-resource.png` | 4. 핵심 시나리오 재현 | 1단계: 리소스 등록(checkout-order-db) |
| `07-scenario-step2-inject-anomaly.png` | 4. 핵심 시나리오 재현 | 2단계: 지표 이상치 주입(errorRate=12.5 → ERROR_RATE_EXCEEDED) |
| `07-scenario-step3-incidents-list.png` | 4. 핵심 시나리오 재현 | 3단계: GET /api/incidents로 사건 확인(HIGH) |
| `07-scenario-step4-incident-detail.png` | 4. 핵심 시나리오 재현 / 8. OpenAI 연동 검증(성공) | 4단계: GET /api/incidents/{id} 상세(조치이력 2건 + 실제 OpenAI가 생성한 자연어 aiSummary) |
| `08-concurrency-test-terminal.png` | 5. 동시성 제어 검증 | 동시요청 10건 → HTTP 201 10건, OPEN Incident 1건만 생성(PASS) |
| `09-auditlog-verification-both.png` | 6. AOP 감사로그 검증 | 동일 action(METRIC_SIMULATE)의 FAIL 1건 + SUCCESS 2건이 모두 감사로그에 남아있음을 확인 |
| `10-actuator-01-up-before.png` | 7. Actuator 헬스체크 검증 | 초기 상태 UP (unresolvedCriticalCount=0) |
| `10-actuator-02-trigger-critical.png` | 7. Actuator 헬스체크 검증 | errorRate=25.0 주입 → CRITICAL Incident 유발 |
| `10-actuator-03-down-confirmed.png` | 7. Actuator 헬스체크 검증 | DOWN 확인 (incidentEngine.status=DOWN, unresolvedCriticalCount=1) |
| `10-actuator-04-resolve.png` | 7. Actuator 헬스체크 검증 | PATCH /api/incidents/{id}/resolve로 해결 처리 |
| `10-actuator-05-up-after.png` | 7. Actuator 헬스체크 검증 | 다시 UP 확인 (unresolvedCriticalCount=0) |
| `11-openai-fallback.png` | 8. OpenAI 연동 검증(폴백) | `OPENAI_TIMEOUT_MS=1`로 서버 재기동 후 강제 타임아웃 → 폴백 템플릿 문장 확인 |
| `12-github-issues-closed.png` | 9. Git/협업 워크플로우 증빙 | `gh issue list --state closed` — 17건 전부 CLOSED |
| `13-github-prs-merged.png` | 9. Git/협업 워크플로우 증빙 | `gh pr list --state merged` — 21건 전부 MERGED |
| `14-git-log-graph.png` | 9. Git/협업 워크플로우 증빙 | `git log --oneline --graph -30` |
| `15-gradle-test-terminal.png` | 10. 테스트 결과 | `./gradlew clean test --console=plain` 프레시 실행, BUILD SUCCESSFUL |
| `16-gradle-test-report-browser.png` | 10. 테스트 결과 | Gradle HTML 테스트 리포트 — 45 tests, 0 failures, 100% successful (패키지별 분포) |

## 참고 — 사용된 리소스/사건 ID (재현 시 참고)

- Resource id=9 `payment-api-gateway`: 도메인모델 섹션용, 스케줄러가 먼저 감지한 QUEUE_DEPTH_EXCEEDED(HIGH) Incident(id=9)를 재사용 — 동일 리소스에 OPEN 사건이 있으면 새로 만들지 않는 로직이 실제로 작동한 사례이기도 함.
- Resource id=10 `checkout-order-db` / Incident id=10: 핵심 시나리오(errorRate=12.5 → ERROR_RATE_EXCEEDED, HIGH), 실제 OpenAI 요약 성공.
- Resource id=11 `concurrency-demo-server`: 동시성 테스트 전용.
- Resource id=12 `healthcheck-demo-api` / Incident id=12: Actuator 헬스체크(errorRate=25.0 → CRITICAL) → resolve.
- Resource id=9(재기동 후 시드 재생성, `OPENAI_TIMEOUT_MS=1`) `openai-fallback-demo` / Incident id=3: OpenAI 폴백 검증 전용(서버 재기동으로 H2 인메모리 DB가 리셋되어 ID가 재사용됨).

## 원본 텍스트 로그

`raw-logs/` 아래 각 캡처와 동일한 파일명(.txt)으로 curl 커맨드 + 실제 JSON 응답 원문이 있다.
US-020에서 캡처 이미지 대신 선택 가능한 텍스트로 옮겨 적을 때 그대로 참고하면 된다.
