# README 시각자료 삽입 가이드 (v2 — 공식 기술 로고 반영)

방금 만든 정적 다이어그램 4종 + 로컬 세션이 추가해야 할 실행 캡처 목록을 정리한 문서. `docs/images/`에 파일을 넣고 아래 마크다운을 README.md에 그대로 삽입하면 된다.

## 1. 생성한 정적 다이어그램 (6종, `docs/images/`에 배치 완료)

| 파일명 | 내용 | README 배치 위치(권장) |
|---|---|---|
| `tech-stack.png` | **[신규]** 실제 사용 기술 공식 로고 스트립 (Spring Boot/PostgreSQL/Docker/Swagger 등 실제 브랜드 컬러 아이콘) | **README 최상단, 타이틀 바로 아래** |
| `architecture-v2.png` | **[신규, 권장]** 시스템 아키텍처 + 각 단계별 실제 기술 로고 매핑 (기존 architecture.png 대체용) | README 상단, "프로젝트 소개" 다음 |
| `architecture.png` | (구버전) 로고 없는 순수 플로우차트 — v2로 대체하거나 부록용으로만 사용 | 필요시만 |
| `erd.png` | ERD (Resource/MetricSnapshot/Incident/IncidentAction/AuditLog 관계) | "데이터 모델" 섹션 |
| `decision-flow.png` | "판단·설명 분리" 핵심 설계 원칙 시퀀스 다이어그램 | "핵심 설계 원칙" 섹션 — **가장 차별화되는 이미지** |
| `wireframe.png` | UI 화면 구조 와이어프레임 (사건목록/사건상세) | "화면 구성" 섹션 |

### 로고 출처 및 정직성 고지 (중요 — README/PDF에 그대로 명시 권장)
- 모든 기술 로고는 **[simple-icons](https://simpleicons.org)** (MIT License, 오픈소스 공식 브랜드 아이콘 저장소)에서 가져온 실제 브랜드 색상·형태의 공식 아이콘이다 — 임의로 그린 유사 로고가 아니다.
- 단, **OpenAI**와 **MyBatis**는 simple-icons에 공식 등록된 아이콘이 없어(라이선스/등록 문제로 추정) 브랜드명 이니셜을 넣은 미니멀 컬러 배지로 대체했다 — 이 부분은 "공식 로고"가 아니라 "자체 제작 텍스트 배지"임을 README나 PDF 캡션에 한 줄로 밝히는 것을 권장 (정직성 원칙 유지).

### README 삽입 마크다운 예시

```markdown
# Ops Sentinel

![기술 스택](docs/images/tech-stack.png)

## 🧭 핵심 설계 원칙 — "판단은 검증 가능해야 한다"
![판단·설명 분리 흐름](docs/images/decision-flow.png)

## 🏗 시스템 아키텍처
![시스템 아키텍처](docs/images/architecture-v2.png)

## 🗂 데이터 모델 (ERD)
![ERD](docs/images/erd.png)

## 🖥 화면 구성
![와이어프레임](docs/images/wireframe.png)
```

## 2. 로컬 세션(Ralph)이 추가로 캡처해야 할 "실제 실행 화면" (README를 진짜 신뢰가는 프로젝트로 만드는 핵심)

정적 다이어그램은 "설계도"일 뿐이고, README의 설득력은 **실제로 동작한다는 증거**에서 나온다. 아래 항목을 로컬 세션에 요청할 것 — 마침 PDF용으로 이미 캡처 중인 화면들과 상당 부분 겹치므로 재활용 가능:

| 캡처 대상 | 형식 | README 섹션 |
|---|---|---|
| Swagger UI 전체 엔드포인트 목록 | PNG | "API 문서" |
| 핵심 시나리오 데모 (지표 이상→Incident 생성→AI요약까지) | **GIF** (터미널 or 브라우저 연속 캡처를 GIF로 변환) | README 최상단, 가장 강력한 임팩트 |
| Actuator 헬스체크 UP→DOWN→UP 전환 | PNG 2~3장 또는 GIF | "신뢰성/모니터링" |
| 동시성 테스트 통과 결과 (터미널) | PNG | "테스트" |
| GitHub Actions/테스트 실행 결과 배지 | Shields.io 배지 (이미지 아님, 마크다운 배지) | README 최상단 |

### 로컬 세션에 넘길 추가 프롬프트 (원하면 이대로 복사해서 사용 — 검증 단계 포함)

```
ralph: docs/10_README_시각자료_삽입가이드.md를 읽고, 아래를 순서대로 진행해라. 특히 0번 검증 단계를 절대 건너뛰지 마라 — 그림이 예뻐 보인다고 바로 신뢰하지 말고, 실제 코드와 다른 부분이 있는지부터 의심하고 확인해라.

0. [검증 — 최우선] docs/images/erd.png, architecture-v2.png, decision-flow.png, tech-stack.png가 실제 최종 코드와 일치하는지 항목별로 대조해라:
   - tech-stack.png: build.gradle을 열어서 실제 Java 버전, Spring Boot 버전, 실제 사용 중인 의존성(JWT 라이브러리 포함 여부, Docker Compose 파일 존재 여부 등)이 이미지에 적힌 것과 일치하는지 확인. 다르면 이미지 텍스트를 코드에 맞게 다시 만들어야 하니 나에게 보고해라 (이 이미지는 mmd 소스가 아니라 HTML 기반이라 재렌더링이 어려우니, 불일치 시 README 캡션에 "일부 버전 표기는 실제와 다를 수 있음" 대신 정확한 텍스트로 직접 고쳐써라).
   - erd.png: 실제 Resource/MetricSnapshot/Incident/IncidentAction/AuditLog 엔티티 클래스(src/main/java/.../entity 또는 domain)를 열어서 필드명·타입·Enum값을 한 줄씩 비교. 다른 점이 있으면 목록으로 정리.
   - architecture-v2.png: 실제 클래스명(RuleEngine, IncidentAgent, AuditLogAspect 등)과 실제 흐름(어떤 클래스가 어떤 클래스를 호출하는지)이 다이어그램과 일치하는지 확인.
   - decision-flow.png: 실제 판단→AOP기록→LLM설명 흐름이 코드 흐름과 일치하는지, 특히 REQUIRES_NEW 트랜잭션·3초 타임아웃·폴백 로직이 실제로 그렇게 구현됐는지 확인.
   - 불일치를 발견하면: docs/images/source/ 안의 대응 .mmd 파일을 실제 코드에 맞게 수정하고, mmdc(설치돼 있으면)로 재렌더링해서 PNG를 교체해라. mmdc를 못 쓰면 PNG는 그대로 두되 README와 PDF 어디에도 쓰지 말고, 불일치 내역을 progress.txt에 정리해서 나(사용자)에게 보고해라 — 절대 틀린 그림을 그냥 제출용으로 쓰지 마라.
   - 문제 없으면 "검증 완료, 실제 코드와 일치함"이라고 명시적으로 기록해라.

1. 검증을 통과한(또는 수정 완료한) 이미지만 docs/images/ 기준으로 README.md 상단에 가이드의 배치 순서대로 삽입해라.
2. 추가로 실제 서버를 띄워서: Swagger UI 캡처, "지표 이상→Incident 생성→AI요약" 핵심 시나리오를 순서대로 캡처한 뒤 GIF로 합성(imagemagick convert 또는 ffmpeg 사용), Actuator UP→DOWN→UP 캡처, 동시성 테스트 통과 터미널 캡처를 만들어서 docs/images/에 추가하고 README에 반영해라.
3. README 최상단에 배지(Java 버전, Spring Boot 버전, 테스트 통과 배지, 라이선스 배지)도 shields.io 마크다운으로 추가해라. 배지 수치(테스트 개수 등)는 실제 gradle test 결과에서 가져오고 지어내지 마라.
4. 마지막으로 README 전체를 다시 읽으면서: 깨진 상대경로 링크, 최신 상태와 안 맞는 서술(예: "구현 예정"인데 이미 구현된 기능), 중복 섹션이 있는지 한 번 더 훑고 발견하면 고쳐라.
5. 완료되면 무엇을 검증했고 무엇을 고쳤는지 요약해서 나에게 보고하고, 커밋 메시지는 "docs: README 시각자료 보강 및 다이어그램 정확성 검증" 형식으로, GitHub Flow 규칙대로 브랜치→PR→머지해라.
```

## 2-1. ⚠️ 반드시 먼저 할 것 — 다이어그램 정확성 검증 (중요)

`erd.png` / `architecture.png` / `decision-flow.png`는 **PRD 설계 스펙을 기준으로 만든 것**이지, 실제 최종 코드를 직접 읽고 만든 게 아니다. 즉 Ralph가 구현하면서 필드명을 바꿨거나(`ruleTriggered`→`triggeredRule` 등), 클래스명이 다르거나(`RuleEngine`이 실제로는 다른 이름), P1/P2 단계에서 스펙이 조금 달라졌을 가능성이 있다. **이 상태로 README에 그냥 박아넣으면 "코드랑 다른 그림"이 되어 오히려 신뢰도를 깎아먹을 수 있다.** 그래서 삽입 전에 반드시 실제 코드와 대조 검증부터 해야 한다.

검증용 mermaid 원본 소스도 `docs/images/source/`에 함께 넣어뒀다 (`architecture.mmd`, `erd.mmd`, `sequence.mmd`) — 불일치가 발견되면 이 파일을 실제 코드에 맞게 고친 뒤 재렌더링(`mmdc` 사용 가능하면 그걸로, 없으면 mermaid 문법만 고쳐서 사용자에게 보고)하면 된다.

## 3. 활용 팁
- `decision-flow.png`는 이 프로젝트의 가장 큰 차별화 포인트(판단·설명 분리)를 한 장으로 보여주므로, README뿐 아니라 **발표자료(06_발표자료.pptx) 3번 슬라이드에도 그대로 삽입** 권장.
- `architecture.png`는 **PDF 본문 2번(아키텍처 결정) 섹션**에도 그대로 재사용 가능.
- 네 이미지 모두 동일 디자인 토큰(초록 accent, Noto Sans CJK KR)으로 만들어졌으므로 서로 섞어 써도 톤이 어긋나지 않는다.
