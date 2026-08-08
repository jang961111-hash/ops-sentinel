# API 기능명세서 — Ops Sentinel

## 공통 사항
- Base URL: `/api`
- 응답 포맷(에러): `{ "timestamp", "status", "error", "message", "path" }` (RFC7807 스타일 권장)
- 인증: 관리자 전용 엔드포인트(`/api/audit-logs`, `PATCH /resolve`)는 JWT 필요 (P1)

## 1. Resource

| Method | Path | 설명 | 요청 | 응답 |
|---|---|---|---|---|
| POST | /api/resources | 리소스 등록 | `{name, type}` | 201 `{id, name, type, createdAt}` |
| GET | /api/resources | 리소스 목록 | 쿼리: `type`(optional), page, size | 200 페이지 목록 |
| GET | /api/resources/{id} | 리소스 단건 | - | 200 |

`type` enum: `SERVER, DATABASE, QUEUE, API`

## 2. Metric

| Method | Path | 설명 | 요청 | 응답 |
|---|---|---|---|---|
| POST | /api/metrics/simulate | 수동 지표 생성(데모용, 강제로 임계치 초과값 주입 가능) | `{resourceId, cpuUsage?, memUsage?, errorRate?, queueDepth?}` (미지정 필드는 랜덤) | 201 생성된 스냅샷 |
| GET | /api/metrics/{resourceId}/latest | 최신 지표 | - | 200 |
| GET | /api/metrics/{resourceId}/history | 지표 이력 | 쿼리: from, to | 200 목록 |

## 3. Incident

| Method | Path | 설명 | 요청 | 응답 |
|---|---|---|---|---|
| GET | /api/incidents | 사건 목록 | 쿼리: status, severity, resourceId, page, size | 200 |
| GET | /api/incidents/{id} | 사건 상세 (조치이력+AI요약 포함) | - | 200 |
| PATCH | /api/incidents/{id}/resolve | 사건 해결 처리 | - | 200 |

응답 예시 (`GET /api/incidents/{id}`):
```json
{
  "id": 12,
  "resourceId": 3,
  "resourceName": "order-service-db",
  "severity": "HIGH",
  "status": "ANALYZING",
  "ruleTriggered": "ERROR_RATE_EXCEEDED",
  "aiSummary": "최근 1분간 에러율이 8.2%로 임계치(5%)를 초과하여 HIGH 등급으로 판정, 모니터링 강화 및 담당자 알림 조치를 자동 실행했습니다.",
  "detectedAt": "2026-08-08T22:40:11",
  "actions": [
    {"actionType": "MONITOR", "executedBy": "SYSTEM_AGENT", "result": "OK", "executedAt": "2026-08-08T22:40:11"},
    {"actionType": "ALERT", "executedBy": "SYSTEM_AGENT", "result": "OK", "executedAt": "2026-08-08T22:40:12"}
  ]
}
```

## 4. Analytics (MyBatis 집계)

| Method | Path | 설명 |
|---|---|---|
| GET | /api/analytics/incident-summary | 리소스별/기간별 사건 건수, 심각도 분포, 평균 해결시간(MTTR 유사 지표) |
| GET | /api/analytics/resource-health-rank | 사건 발생 빈도 기준 리소스 위험도 랭킹 |

## 5. Audit Log (관리자용)

| Method | Path | 설명 |
|---|---|---|
| GET | /api/audit-logs | 전체 감사로그 (필터: actorType, resultStatus, targetType, page, size) |

## 6. Actuator (운영)

| Path | 설명 |
|---|---|
| /actuator/health | 기본 헬스체크 |
| /actuator/health/incidentEngine | 커스텀: 최근 5분 내 미해결 CRITICAL 존재 시 DOWN |

## 7. 에러 코드

| 코드 | 상황 |
|---|---|
| 400 | 잘못된 요청 파라미터 |
| 404 | 존재하지 않는 리소스/사건 |
| 409 | 동시성 충돌 (중복 사건 생성 시도) |
| 500 | 서버 내부 오류 (OpenAI 실패는 500이 아니라 폴백으로 200 처리되어야 함) |
