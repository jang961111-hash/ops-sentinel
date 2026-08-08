// US-020: G062_장병헌_백엔드최종실습.pdf 조판·렌더링 스크립트
// docs/08_최종PDF_생성_프롬프트.md 표준(5부 구조·디자인토큰·페이지문법)을 그대로 구현한다.
// 실행 방법:
//   cd docs/pdf/render-script && npm install playwright@1.62.1 && npx playwright install chromium
//   node build.mjs
// 입력: docs/pdf/captures/*.png + raw-logs/*.txt, docs/pdf/원본캡처/*.png
// 출력: docs/pdf/G062_장병헌_백엔드최종실습.pdf (ROOT 상수를 로컬 저장소 경로에 맞게 수정 후 실행)
import fs from 'node:fs';
import path from 'node:path';
import { chromium } from 'playwright';

const ROOT = '/Users/jangbyeongheon/workspace/ops-sentinel';
const CAP = path.join(ROOT, 'docs/pdf/captures');
const ORIG = path.join(ROOT, 'docs/pdf/원본캡처');
const OUT = path.join(ROOT, 'docs/pdf/G062_장병헌_백엔드최종실습.pdf');

function img(name) {
  const buf = fs.readFileSync(path.join(CAP, name));
  return `data:image/png;base64,${buf.toString('base64')}`;
}
function imgOrig(name) {
  const buf = fs.readFileSync(path.join(ORIG, name));
  return `data:image/png;base64,${buf.toString('base64')}`;
}
function esc(s) {
  return String(s).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
}

const FOOTER_NAME = 'SKALA 4기 광주 2반 장병헌 · Ops Sentinel 최종실습';

const pages = []; // array of {body, variant}

function pushPage(headHtml, bodyHtml, opts = {}) {
  const variant = opts.center ? 'center' : 'top';
  pages.push({ head: headHtml, body: bodyHtml, variant });
}

function sectionHead(num, title, subtitle) {
  return `
    <div class="page-head">
      <h1><span class="num">${esc(num)}.</span> ${esc(title)}</h1>
      <p class="subtitle">${esc(subtitle)}</p>
      <hr class="rule-strong" />
    </div>`;
}

function explainBlock({ proves, read, verify }) {
  return `
    <div class="explain">
      <p><span class="tri">▸</span><b>무엇을 증명하나</b> — ${proves}</p>
      <p><span class="tri">▸</span><b>읽는 법</b> — ${read}</p>
      ${verify ? `<p><span class="tri">▸</span><b>값 검증</b> — ${verify}</p>` : ''}
    </div>`;
}

function cmdBlock(lines) {
  return `<pre class="cmd">${esc(lines)}</pre>`;
}

/** A page whose body is: optional cmd block, image, explain block */
function capturePage({ num, title, subtitle, cmd, imageSrc, imageAlt, proves, read, verify, imgMaxHeight }) {
  const head = sectionHead(num, title, subtitle);
  const style = imgMaxHeight ? ` style="max-height:${imgMaxHeight}"` : '';
  const body = `
    <div class="page-body capture-body">
      ${cmd ? cmdBlock(cmd) : ''}
      <div class="img-wrap">
        <img src="${imageSrc}" alt="${esc(imageAlt || '')}"${style} />
      </div>
      ${explainBlock({ proves, read, verify })}
    </div>`;
  pushPage(head, body, { center: true });
}

/** Free-form text page */
function textPage({ num, title, subtitle, html, center }) {
  const head = sectionHead(num, title, subtitle);
  const body = `<div class="page-body text-body">${html}</div>`;
  pushPage(head, body, { center: !!center });
}

// ============================================================
// [1] 표지
// ============================================================
{
  const body = `
    <div class="cover">
      <div class="cover-eyebrow">SKALA 4기 백엔드 최종 실습과제 · 제출 보고서</div>
      <h1 class="cover-title">Ops Sentinel</h1>
      <p class="cover-sub">에이전틱 인프라 감시·감사 API</p>
      <hr class="rule-strong" />
      <table class="cover-meta">
        <tr><td>소속</td><td>SKALA 4기 광주캠퍼스 광주 2반</td></tr>
        <tr><td>이름 / 고유번호</td><td>장병헌 (G062)</td></tr>
        <tr><td>제출일</td><td>2026-08-09</td></tr>
        <tr><td>저장소</td><td>github.com/jang961111-hash/ops-sentinel</td></tr>
        <tr><td>기술 스택</td><td>Spring Boot 3.3 · Java 21 · Gradle · JPA(CRUD) + MyBatis(집계) · H2(기본)/PostgreSQL(Docker) · OpenAI API(gpt-4o-mini) · JWT(jjwt) · springdoc-openapi(Swagger)</td></tr>
        <tr><td>규모</td><td>엔드포인트 13개 · 테스트 45건(0 실패) · 이슈 17개(전체 CLOSED) · PR 21개(전체 MERGED) · 태그 12개</td></tr>
      </table>
      <div class="cover-links">
        <div class="cover-links-title">바로가기 (전부 접속 확인 완료, HTTP 200)</div>
        <ul>
          <li><a href="https://github.com/jang961111-hash/ops-sentinel">저장소 — github.com/jang961111-hash/ops-sentinel</a></li>
          <li><a href="https://github.com/jang961111-hash/ops-sentinel/releases/tag/v1.0.0">최종 태그 v1.0.0 — releases/tag/v1.0.0</a></li>
          <li><a href="https://github.com/jang961111-hash/ops-sentinel/issues?q=is%3Aissue+is%3Aclosed">이슈 목록(전체 CLOSED) — issues?q=is:issue+is:closed</a></li>
          <li><a href="https://github.com/jang961111-hash/ops-sentinel/pulls?q=is%3Apr+is%3Amerged">PR 목록(머지 이력) — pulls?q=is:pr+is:merged</a></li>
          <li><a href="https://github.com/jang961111-hash/ops-sentinel#readme">README — #readme</a></li>
        </ul>
      </div>
      <p class="cover-note">가상 인프라 지표를 감시하다가 이상을 감지하면 스스로 사건을 생성·심각도 판정·조치기록·AI 요약까지 수행하고,<br/>모든 과정을 감사 가능하게 남기는 백엔드 API. 2026년 4월 SK AX × 대신증권 7년 계약(에이전틱 AIOps)을 기획 동기로 삼았다.</p>
    </div>`;
  pushPage('', body, { center: true });
  pages[pages.length - 1].isCover = true;
}

// ============================================================
// [2] 목차 + 요구사항 대조표
// ============================================================
{
  const rows = [
    ['자유주제 기획 배경 (SK AX 대신증권 사례 근거)', 'p.3–4', '충족'],
    ['JPA + MyBatis 역할 분리', 'p.5–6, p.14–15', '충족'],
    ['동시성 제어 (중복 사건 방지)', 'p.22', '충족'],
    ['AOP 감사로그 (성공/실패 100% 기록)', 'p.16–17, p.23', '충족'],
    ['전역 예외처리', 'p.9, 11, 12, 13, 15, 16', '충족'],
    ['Swagger API 문서화', 'p.7', '충족'],
    ['Actuator 커스텀 HealthIndicator', 'p.24–28', '충족'],
    ['OpenAI API 연동 + 폴백 처리', 'p.21, 29–30', '충족'],
    ['JWT 인증 (선택 구현)', 'p.13, 16, 24–28', '충족'],
    ['Git 워크플로우 (이슈/PR/커밋 컨벤션)', 'p.31–33', '충족'],
    ['테스트 코드 (동시성 재현 포함)', 'p.22, 34–35', '충족'],
    ['의견·개선사항', 'p.37–38', '충족'],
  ];
  const toc = `
    <ol class="toc-list">
      <li>기획 배경 <span>p.3</span></li>
      <li>아키텍처 결정(ADR) <span>p.5</span></li>
      <li>도메인 모델 &amp; API 스펙 <span>p.7</span></li>
      <li>핵심 시나리오 재현 <span>p.18</span></li>
      <li>동시성 제어 검증 <span>p.22</span></li>
      <li>AOP 감사로그 검증 <span>p.23</span></li>
      <li>Actuator 헬스체크 검증 <span>p.24</span></li>
      <li>OpenAI 연동 검증 <span>p.29</span></li>
      <li>Git/협업 워크플로우 증빙 <span>p.31</span></li>
      <li>테스트 결과 <span>p.34</span></li>
      <li>근거자료 요약 <span>p.36</span></li>
      <li>의견·개선사항 <span>p.37</span></li>
      <li>부록 <span>p.39</span></li>
    </ol>`;
  const table = `
    <table class="req-table">
      <thead><tr><th>요구사항</th><th>증빙 페이지</th><th>판정</th></tr></thead>
      <tbody>
        ${rows.map(r => `<tr><td>${esc(r[0])}</td><td>${esc(r[1])}</td><td class="pass">${esc(r[2])}</td></tr>`).join('')}
      </tbody>
    </table>`;
  const html = `
    <div class="two-col">
      <div><h3 class="mini-h">목차</h3>${toc}</div>
      <div><h3 class="mini-h">요구사항 대조표</h3>${table}</div>
    </div>`;
  textPage({ num: '목차', title: '목차 & 요구사항 대조표', subtitle: '전체 문서 구조와 각 요구사항의 증빙 위치', html });
}

// ============================================================
// [3-1] 기획 배경 (본문 1)
// ============================================================
{
  const html = `
    <p class="p">2026년 4월 23일, SK AX는 대신증권과 <b>7년 규모의 IT 운영 통합관리 계약</b>을 맺고 에이전틱 AI 기반 운영 서비스 'AXgenticWire NPO(New Paradigm for Operation)'를 1단계(모니터링·백업·장애 및 상황관리 에이전트)부터 단계적으로 도입한다고 공식 발표했다. 이 계약의 핵심은 "장애가 난 뒤 사람이 로그를 뒤지는" 방식에서 "시스템이 이상 징후를 선제적으로 탐지·분석·판단·조치하는" 방식으로 운영 패러다임이 전환된다는 점이다. Ops Sentinel은 이 개념적 파이프라인을 개인 백엔드 과제 스케일로 재구현한 프로젝트다.</p>
    <div class="quote-box">
      <div class="quote-src">SK AX 공식 뉴스룸 원문 (2026.04.23 게시, 사용자가 2026-08-09 직접 캡처 확보 — 원문 전체와 캡처는 부록 p.43 참고)</div>
      <p>"금융업계 최대 고민 중 하나가 장애 대응인만큼, AXgenticWire NPO 운영체계 도입을 통해 휴먼에러를 방지하고 문제상황을 사전에 조치하게 될 것" — <b>김용신 SK AX Cloud사업본부장</b></p>
      <p>"에이전틱AI 기반 IT 운영 체계를 도입하고, 장애 대응을 넘어 사전 예방 중심의 안정적인 금융 인프라를 구축해 나갈 것" — <b>홍종국 대신증권 IT부문장</b></p>
    </div>
    <p class="p-note">※ 원문에는 SK AX 대표이사가 "김완종(사장)", 대신증권 대표이사가 "진승욱"으로 기재되어 있다. 시점에 따라 실제 대표자 현황과 다를 수 있으나, 왜곡 없이 <b>SK AX 공식 발표 원문에 기재된 대로</b> 인용했다.</p>
    <h3 class="mini-h">8개 독립 매체 교차검증 — 통합 팩트시트</h3>
    <table class="fact-table">
      <thead><tr><th>항목</th><th>내용</th><th>교차검증</th></tr></thead>
      <tbody>
        <tr><td>계약 당사자 / 기간 / 발표일</td><td>SK AX ↔ 대신증권 / 7년 / 2026.04.23</td><td>10/10</td></tr>
        <tr><td>서비스명</td><td>AXgenticWire(엑스젠틱와이어) NPO</td><td>8/10</td></tr>
        <tr><td>1단계 적용 에이전트</td><td>모니터링 · 백업 · 장애/상황관리</td><td>10/10</td></tr>
        <tr><td>2단계 확대 에이전트</td><td>성능 · 용량 · 가용 · 보안</td><td>6/10</td></tr>
        <tr><td>운영 방식</td><td>노코드(No-Code) — 운영자가 직접 에이전트 생성</td><td>4/10</td></tr>
        <tr><td>계약 금액</td><td>전 매체 미공개</td><td>0/10</td></tr>
      </tbody>
    </table>
    <p class="p-note">출처: 한국경제, SEN TV, 매일일보, 뉴스포스트, 뉴데일리, 주간한국, 비즈트리뷴, AI타임스 등 8개 이상 독립 매체(상세 링크는 <code>docs/09_SKAX_대신증권_원문자료_심층조사.md</code> 2장). 근거 등급·비판적 평가는 <code>docs/07_배경조사_근거자료집.md</code> C장 참고.</p>`;
  textPage({ num: 1, title: '기획 배경', subtitle: 'SK AX × 대신증권 7년 계약 — 왜 이 주제를 선택했는가', html });
}
{
  const html = `
    <p class="p">현업 AIOps는 "장애가 난 뒤 사람이 로그를 뒤지는" 방식에서 "시스템이 스스로 이상을 감지·판단·조치하고, 그 판단 근거를 사람이 사후 검증할 수 있게 남기는" 방식으로 이동하고 있다. 이번 과제에서 흔한 "쇼핑몰+락 제어" 패턴 대신, <b>판단의 자동화 + 판단 근거의 검증 가능성</b>이라는 축으로 차별화했다(<code>docs/01_PRD_기획명세서_최종본.md</code> 2장).</p>
    <h3 class="mini-h">목표 / 비목표</h3>
    <ul class="bullet">
      <li><b>목표</b> — 규칙 기반 이상탐지 → Incident 자동 생성 → Action 자동 기록, AOP로 성공/실패 100% 감사로그 기록, OpenAI API로 판단근거 자연어 요약(실패 시 폴백), JPA(CRUD)+MyBatis(집계) 역할 분리, 동시성 제어(중복 사건 방지)</li>
      <li><b>비목표</b> — 실제 물리 인프라 연동(전부 시뮬레이션 데이터), MSA 분리·K8s·실서버 배포, 정식 SPA 프론트엔드(Swagger UI + 최소 정적 대시보드로 대체)</li>
    </ul>
    <h3 class="mini-h">설계 결정 ↔ 근거 출처 매핑</h3>
    <table class="fact-table">
      <thead><tr><th>우리 프로젝트의 설계</th><th>근거 출처</th></tr></thead>
      <tbody>
        <tr><td>지표(CPU/메모리/에러율/큐길이) 수집 구조</td><td>Google SRE Book, Four Golden Signals (1차)</td></tr>
        <tr><td>이상탐지 → 근본원인 → 자동조치 파이프라인</td><td>베이징대 AIOps 서베이 논문, arXiv:2406.11213 (1차)</td></tr>
        <tr><td>규칙기반 판단 + LLM은 설명만 담당</td><td>FINOS AI 거버넌스 프레임워크 Tier 2 (1차)</td></tr>
        <tr><td>OpenAI 실패 시 폴백 처리</td><td>Zalando Engineering Blog, AI 사후분석 2년 운영 한계 인정 (준1차)</td></tr>
        <tr><td>전체 기획 동기</td><td>SK AX × 대신증권 7년 계약(2026.4) (1차/2차 교차검증)</td></tr>
      </tbody>
    </table>
    <p class="p-note">출처 등급 기준(1차=원발행기관 공식문서, 2차=취재·해설, 3차=커뮤니티 정리글)과 각 항목의 비판적 평가는 <code>docs/07_배경조사_근거자료집.md</code>에 전문 수록.</p>`;
  textPage({ num: 1, title: '기획 배경', subtitle: '문제 정의(Why)와 설계 근거 매핑', html });
}

// ============================================================
// [3-2] 아키텍처 결정 (본문 2)
// ============================================================
{
  const html = `
    <table class="fact-table">
      <thead><tr><th>옵션</th><th>채택</th><th>근거</th></tr></thead>
      <tbody>
        <tr><td>모듈러 모놀리식 (도메인별 패키지, 단일 배포단위)</td><td class="pass">채택</td><td>혼자·제한시간 안에 완성해야 하는 조건에서 서비스 간 통신·분산 트랜잭션 문제 없이 "구조는 잘 나뉘어 있다"는 신호를 줄 수 있는 현실적인 선택</td></tr>
        <tr><td>MSA (서비스 완전 분리)</td><td class="fail">기각</td><td>서비스 간 통신·분산 트랜잭션·배포 파이프라인까지 혼자 감당하기엔 리스크 대비 이득이 작음</td></tr>
        <tr><td>단순 레이어드 모놀리식 (도메인 구분 없음)</td><td class="fail">기각</td><td>도메인 경계가 드러나지 않아 구조적 차별점이 없음</td></tr>
      </tbody>
    </table>
    <h3 class="mini-h">패키지 구조</h3>
    <pre class="cmd small">src/main/java/com/opssentinel
├── resource   # 인프라 리소스(서버/DB/큐/API) 등록·조회
├── metric     # 지표 시뮬레이션·조회
├── incident   # 이상탐지 규칙엔진, 사건 생성·조회·해결, 커스텀 헬스 인디케이터
├── audit      # AOP 감사로그(@Auditable, Aspect, Recorder)
├── analytics  # MyBatis 기반 집계 API
└── common     # 전역 예외처리, 공통 응답 DTO</pre>
    <h3 class="mini-h">JPA(CRUD) + MyBatis(집계) 역할 분리</h3>
    <p class="p">단건 CRUD·상태 전이·락이 필요한 조회(<code>SELECT ... FOR UPDATE</code>)는 Spring Data JPA로 처리한다(Resource/MetricSnapshot/Incident/IncidentAction/AuditLog 5개 엔티티). 여러 테이블을 JOIN하고 GROUP BY·CASE·AVG로 집계하는 통계성 조회(<code>/api/analytics/*</code>)는 MyBatis(<code>AnalyticsMapper.xml</code>)로 순수 SQL을 직접 작성한다. JPA로도 구현 가능하지만 다중 테이블 집계를 JPQL/Criteria로 억지로 표현하면 가독성이 떨어지고 실행계획을 예측하기 어려워지므로 역할을 명확히 나눴다.</p>
    <h3 class="mini-h">배포 결정</h3>
    <p class="p">로컬 실행(Gradle) + 선택적 Docker Compose(app+PostgreSQL)까지만 지원한다. AWS EC2 등 실제 클라우드 배포는 하지 않았다 — 공식 제출 요구사항에 배포가 없고, 계정 생성·보안그룹·비용 관리 등 부가 작업이 마감 임박 상황에서 리스크 대비 실익이 없다고 판단했다(<code>docs/01_PRD_기획명세서_최종본.md</code> 3-1장).</p>`;
  textPage({ num: 2, title: '아키텍처 결정 (ADR)', subtitle: '왜 모듈러 모놀리식인가 — 대안과의 비교', html });
}
{
  const diagram = `
    <div class="flow">
      <div class="flow-row">
        <div class="fbox">Scheduler(7s) /<br/>POST /metrics/simulate</div>
        <div class="farrow">→</div>
        <div class="fbox">MetricSnapshot 생성</div>
        <div class="farrow">→</div>
        <div class="fbox accent">IncidentRuleEngine<br/>(임계치 판단)</div>
      </div>
      <div class="flow-row">
        <div class="fbox ghost"></div>
        <div class="farrow">↓ 이상 감지</div>
        <div class="fbox ghost"></div>
        <div class="farrow"></div>
        <div class="fbox ghost"></div>
      </div>
      <div class="flow-row">
        <div class="fbox">IncidentDetectionService<br/>(비관적 락, 중복방지)</div>
        <div class="farrow">→</div>
        <div class="fbox">IncidentActionService<br/>(조치 자동 결정·기록)</div>
        <div class="farrow">→</div>
        <div class="fbox">AiSummaryService<br/>(OpenAI, 3s timeout+폴백)</div>
      </div>
      <div class="flow-row">
        <div class="fbox ghost"></div><div class="farrow"></div>
        <div class="fbox ghost"></div><div class="farrow"></div>
        <div class="farrow">↓</div>
      </div>
      <div class="flow-row">
        <div class="fbox wide">REST Controller → Swagger UI / 정적 대시보드 · MyBatis 집계 → /api/analytics/* · Actuator Custom HealthIndicator</div>
      </div>
      <div class="flow-row">
        <div class="fbox wide accent2">AOP @Around (AuditLogAspect) — 위 모든 단계를 가로채 AuditLog에 REQUIRES_NEW 트랜잭션으로 성공/실패 100% 기록</div>
      </div>
    </div>
    <p class="p-note">원본 다이어그램(Mermaid)은 <code>docs/03_시스템아키텍처.html</code>에 있으며, 이 페이지는 PDF 텍스트 선택성을 위해 동일 구조를 표/텍스트로 재구성한 것이다(원본 파일은 수정하지 않았다).</p>`;
  const npo = `
    <h3 class="mini-h">근거의 삼각검증 — 학술 표준 · 산업 사례(SK AX) · 우리 구현</h3>
    <table class="fact-table">
      <thead><tr><th>AXgenticWire NPO 공식 역할분담</th><th>Ops Sentinel 대응 컴포넌트</th></tr></thead>
      <tbody>
        <tr><td>탐지 에이전트 — 이상 징후 감지</td><td><code>MetricSnapshot</code> 수집 + <code>RuleEngine</code> 임계치 탐지</td></tr>
        <tr><td>분석 에이전트 — 원인 추론·해석</td><td><code>Incident.ruleTriggered</code> (근본원인 카테고리화)</td></tr>
        <tr><td>영향도 에이전트 — 주변 시스템 영향 판단</td><td><code>Incident.severity</code> 자동 판정</td></tr>
        <tr><td>조치 에이전트 — 복구·설정변경·자원재할당 즉시 실행</td><td><code>IncidentAction</code> 자동 기록</td></tr>
      </tbody>
    </table>
    <p class="p-note">이 4단계 분담(CEOSCOREDAILY, 2026-04-02)은 베이징대 AIOps 서베이 논문(arXiv:2406.11213)의 4단계 파이프라인(전처리→이상탐지→근본원인분석→자동복구)과도 구조적으로 일치한다 — "학계 표준"과 "SK AX 실제 상용 서비스"가 같은 4단계로 수렴함을 확인했다(<code>docs/09_SKAX_대신증권_원문자료_심층조사.md</code> 3-2장).</p>`;
  textPage({ num: 2, title: '아키텍처 결정 (ADR)', subtitle: '처리 파이프라인 & SK AX NPO 구조 대응', html: diagram + npo });
}

// ============================================================
// [3-3] 도메인 모델 & API 스펙 (본문 3)
// ============================================================
capturePage({
  num: 3, title: '도메인 모델 & API 스펙', subtitle: 'Swagger UI — 전체 엔드포인트(13개) 및 스키마',
  imageSrc: img('01-swagger-overview.png'), imageAlt: 'Swagger UI 전체 엔드포인트 목록',
  proves: '요구사항 "Swagger API 문서화"를 증명 — Resource/Metric/Incident/Analytics/Audit Log/Auth 6개 태그, 13개 엔드포인트가 모두 문서화되어 있다.',
  read: '화면 상단부터 태그별로 그룹핑된 엔드포인트 목록과 각 요청/응답 스키마(DTO) 구조를 확인한다.',
  verify: 'Auth 1 + Resource 3 + Metric 3 + Incident 3 + Analytics 2 + Audit Log 1 = 13개, <code>GET /v3/api-docs</code> paths 집계와 일치.',
});
capturePage({
  num: 3, title: '도메인 모델 & API 스펙', subtitle: 'POST /api/resources — 성공 케이스(201)',
  cmd: `$ curl -i -X POST http://localhost:8080/api/resources \\
    -H "Content-Type: application/json" \\
    -d '{"name":"payment-api-gateway","type":"API"}'`,
  imageSrc: img('02-api-resource-success.png'), imageAlt: '리소스 등록 성공 201',
  proves: 'Resource 등록 API의 정상 동작 — DB 저장 후 id/createdAt이 채워진 리소스가 반환됨을 증명.',
  read: 'HTTP 상태 201과 응답 바디의 id, type=API 값을 확인한다.',
  verify: '요청한 name="payment-api-gateway", type="API"가 응답에 그대로 반영되고 id=9가 신규 부여됨.',
});
capturePage({
  num: 3, title: '도메인 모델 & API 스펙', subtitle: 'POST /api/resources — 검증 실패 케이스(400)',
  cmd: `$ curl -i -X POST http://localhost:8080/api/resources \\
    -H "Content-Type: application/json" \\
    -d '{"name":"","type":"API"}'`,
  imageSrc: img('02-api-resource-fail.png'), imageAlt: '리소스 등록 검증 실패 400',
  proves: '전역 예외처리 요구사항을 증명 — Bean Validation 실패가 500이 아니라 400 + 공통 에러 포맷으로 응답됨.',
  read: 'status=400, message="name: 공백일 수 없습니다"가 <code>{timestamp,status,error,message,path}</code> 공통 포맷을 따르는지 확인한다.',
  verify: 'name을 빈 문자열로 보냈을 때 정확히 400과 검증 메시지가 반환됨 — 서버가 죽지 않고 정상 응답.',
});
capturePage({
  num: 3, title: '도메인 모델 & API 스펙', subtitle: 'POST /api/metrics/simulate — 성공 케이스(201)',
  cmd: `$ curl -i -X POST http://localhost:8080/api/metrics/simulate \\
    -H "Content-Type: application/json" \\
    -d '{"resourceId":9,"cpuUsage":95.0}'`,
  imageSrc: img('03-api-metric-success.png'), imageAlt: '지표 시뮬레이션 성공 201',
  proves: '수동 지표 생성 API가 지정 필드(cpuUsage)는 고정하고 미지정 필드는 랜덤값으로 채움을 증명.',
  read: '요청에는 cpuUsage=95.0만 지정했는데 응답에는 memUsage/errorRate/queueDepth까지 값이 채워져 있음을 확인한다.',
  verify: 'cpuUsage=95.0(요청값 그대로), 나머지 3개 필드는 랜덤 생성 — 이 요청이 CPU_EXCEEDED 규칙을 트리거해 뒤에 나올 근본원인 분석의 입력이 된다.',
});
capturePage({
  num: 3, title: '도메인 모델 & API 스펙', subtitle: 'POST /api/metrics/simulate — 존재하지 않는 리소스(404)',
  cmd: `$ curl -i -X POST http://localhost:8080/api/metrics/simulate \\
    -H "Content-Type: application/json" \\
    -d '{"resourceId":999999,"cpuUsage":50.0}'`,
  imageSrc: img('03-api-metric-fail.png'), imageAlt: '지표 시뮬레이션 404',
  proves: '존재하지 않는 resourceId에 대한 예외처리 및 AOP 감사로그 FAIL 기록(6장에서 재확인)을 증명.',
  read: 'status=404, message="Resource not found: 999999" 확인.',
  verify: '이 요청은 의도적 실패 케이스로, 감사로그에도 동일 정보로 FAIL 기록이 남는다(p.23에서 대조 확인).',
});
capturePage({
  num: 3, title: '도메인 모델 & API 스펙', subtitle: 'GET /api/incidents/{id} — 존재하지 않는 사건(404)',
  cmd: `$ curl -i http://localhost:8080/api/incidents/999999`,
  imageSrc: img('04-api-incident-fail-notfound.png'), imageAlt: 'Incident 조회 404',
  proves: '전역 예외처리가 컨트롤러 계층에서 던진 <code>ResponseStatusException</code>도 공통 포맷으로 매핑함을 증명.',
  read: 'status=404, message="Incident not found: 999999" 확인.',
  verify: '존재하지 않는 id 999999 요청에 정확히 404가 반환되고 500으로 새지 않음.',
});
capturePage({
  num: 3, title: '도메인 모델 & API 스펙', subtitle: 'PATCH /api/incidents/{id}/resolve — 인증 없이 호출(401)',
  cmd: `$ curl -i -X PATCH http://localhost:8080/api/incidents/10/resolve
  (Authorization 헤더 없이 호출)`,
  imageSrc: img('04-api-incident-resolve-fail-noauth.png'), imageAlt: 'resolve 인증실패 401',
  proves: 'JWT 인증 요구사항 증명 — 관리자 전용 엔드포인트가 토큰 없이는 401로 차단됨.',
  read: 'status=401, message="인증 토큰이 필요합니다." 확인.',
  verify: '<code>PATCH /api/incidents/{id}/resolve</code>는 JWT 보호 대상 2개 엔드포인트 중 하나 — Authorization 헤더가 없으면 서비스 로직에 도달하지 못하고 필터 단에서 차단됨.',
});
capturePage({
  num: 3, title: '도메인 모델 & API 스펙', subtitle: 'GET /api/analytics/incident-summary — 성공(200, MyBatis 집계)',
  cmd: `$ curl -i http://localhost:8080/api/analytics/incident-summary`,
  imageSrc: img('05-api-analytics-success.png'), imageAlt: 'analytics incident-summary 성공',
  proves: 'MyBatis 기반 집계 쿼리(JOIN + GROUP BY + CASE 조건부 SUM)가 정상 동작함을 증명 — JPA/MyBatis 역할 분리의 실증.',
  read: '리소스별로 incidentCount, severity별 건수(low/medium/high/critical), avgResolutionMinutes가 집계되어 있는지 확인한다.',
  verify: '리소스 10개 각각 incidentCount=1로, 시드 데이터+시나리오 재현 과정에서 생성된 Incident 수와 일치.',
});
capturePage({
  num: 3, title: '도메인 모델 & API 스펙', subtitle: 'GET /api/analytics/incident-summary — 파라미터 오류(400)',
  cmd: `$ curl -i "http://localhost:8080/api/analytics/incident-summary?from=not-a-date"`,
  imageSrc: img('05-api-analytics-fail.png'), imageAlt: 'analytics 400',
  proves: '쿼리 파라미터 타입 불일치(잘못된 날짜 형식)도 전역 예외처리가 500이 아닌 400으로 정확히 매핑함을 증명.',
  read: 'status=400, message="잘못된 요청 파라미터입니다: from=not-a-date" 확인.',
  verify: 'from 파라미터에 날짜가 아닌 문자열을 넣었을 때 서버가 파싱 예외를 그대로 흘리지 않고 의미 있는 메시지로 응답함.',
});
capturePage({
  num: 3, title: '도메인 모델 & API 스펙', subtitle: 'GET /api/audit-logs — 인증 없이 호출(401)',
  cmd: `$ curl -i http://localhost:8080/api/audit-logs
  (Authorization 헤더 없이 호출)`,
  imageSrc: img('06-api-auditlog-fail-noauth.png'), imageAlt: 'audit-logs 401',
  proves: 'JWT 인증 요구사항 증명 — 감사로그 조회도 관리자 전용으로 보호됨.',
  read: 'status=401, message="인증 토큰이 필요합니다." 확인.',
  verify: '<code>GET /api/audit-logs</code>가 JWT 보호 대상 2개 엔드포인트 중 나머지 하나임을 실측으로 확인.',
});
capturePage({
  num: 3, title: '도메인 모델 & API 스펙', subtitle: 'GET /api/audit-logs — 관리자 토큰으로 성공(200)',
  cmd: `$ curl -i http://localhost:8080/api/audit-logs?size=5 \\
    -H "Authorization: Bearer $TOKEN"`,
  imageSrc: img('06-api-auditlog-success.png'), imageAlt: 'audit-logs 200',
  proves: '유효한 JWT를 실으면 감사로그 전체가 페이지네이션되어 조회됨을 증명.',
  read: 'content 배열의 각 항목(actorType, action, targetType, resultStatus)과 totalElements 값을 확인한다.',
  verify: '이 시점 기준 totalElements=381건 — 지금까지 실행한 모든 @Auditable 메서드 호출(성공/실패 무관)이 누락 없이 누적 기록되고 있음을 보여준다.',
});

// ============================================================
// [3-4] 핵심 시나리오 재현 (본문 4)
// ============================================================
capturePage({
  num: 4, title: '핵심 시나리오 재현', subtitle: '1단계 — 리소스 등록(checkout-order-db)',
  cmd: `$ curl -i -X POST http://localhost:8080/api/resources \\
    -H "Content-Type: application/json" \\
    -d '{"name":"checkout-order-db","type":"DATABASE"}'`,
  imageSrc: img('07-scenario-step1-register-resource.png'), imageAlt: '시나리오 1단계 리소스등록',
  proves: '"지표 이상 발생 → Incident 자동생성 → 조치기록 → AI요약" 전체 흐름의 출발점 — 운영자가 감시 대상 리소스를 등록한다.',
  read: 'id=10, name="checkout-order-db", type="DATABASE" 확인.',
  verify: '이 리소스 id=10이 이후 2~4단계 전체에서 동일하게 추적된다.',
});
capturePage({
  num: 4, title: '핵심 시나리오 재현', subtitle: '2단계 — 지표 이상치 주입(errorRate=12.5)',
  cmd: `$ curl -i -X POST http://localhost:8080/api/metrics/simulate \\
    -H "Content-Type: application/json" \\
    -d '{"resourceId":10,"errorRate":12.5}'`,
  imageSrc: img('07-scenario-step2-inject-anomaly.png'), imageAlt: '시나리오 2단계 이상치주입',
  proves: 'IncidentRuleEngine이 errorRate 임계치(5%)를 판단 기준으로 사용함을 증명.',
  read: '요청 errorRate=12.5가 응답에 그대로 반영되고, 이 값이 ERROR_RATE_EXCEEDED 규칙을 트리거한다.',
  verify: '12.5% &gt; 임계치 5% → 규칙 위반 확정, 다음 단계에서 Incident가 자동 생성됨.',
});
capturePage({
  num: 4, title: '핵심 시나리오 재현', subtitle: '3단계 — GET /api/incidents로 사건 확인(HIGH)',
  cmd: `$ curl -s "http://localhost:8080/api/incidents?resourceId=10"`,
  imageSrc: img('07-scenario-step3-incidents-list.png'), imageAlt: '시나리오 3단계 사건목록',
  proves: '2단계의 이상치 주입이 실제로 Incident 레코드 생성까지 이어짐을 증명 — 사람 개입 없이 시스템이 스스로 사건화했다.',
  read: 'totalElements=1, content[0].severity="HIGH", ruleTriggered="ERROR_RATE_EXCEEDED", status="ANALYZING" 확인.',
  verify: 'resourceId=10 하나에 정확히 1건의 Incident만 생성됨 — 규칙엔진→사건생성 파이프라인이 1:1로 정상 작동.',
});
capturePage({
  num: 4, title: '핵심 시나리오 재현', subtitle: '4단계 — 사건 상세(조치이력 2건 + AI 요약)',
  cmd: `$ curl -s http://localhost:8080/api/incidents/10`,
  imageSrc: img('07-scenario-step4-incident-detail.png'), imageAlt: '시나리오 4단계 사건상세',
  proves: '시나리오의 마지막 단계 — IncidentActionService의 자동 조치 기록과 AiSummaryService의 실제 OpenAI 자연어 요약이 함께 채워짐을 증명.',
  read: 'actions 배열에 ALERT/BACKUP 2건, aiSummary에 실제 생성된 한국어 문장("리소스ID 10에서 오류율이... 백업을 수행하기로 결정했습니다") 확인.',
  verify: 'HIGH 등급 + ERROR_RATE_EXCEEDED 규칙 → ALERT+BACKUP 조합 조치가 README 4장에서 서술한 분기 로직과 일치.',
});

// ============================================================
// [3-5] 동시성 제어 검증 (본문 5)
// ============================================================
capturePage({
  num: 5, title: '동시성 제어 검증', subtitle: '동일 resourceId로 10건 동시 요청 → 터미널 결과',
  cmd: `$ RESOURCE_ID=11; N=10  # 동일 resourceId로 동시 전송
$ curl -s "http://localhost:8080/api/incidents?resourceId=11" | jq '.totalElements'`,
  imageSrc: img('08-concurrency-test-terminal.png'), imageAlt: '동시성 테스트 터미널',
  proves: '요구사항 "동시 요청 시 동일 리소스에 중복 사건이 생성되지 않음"을 실측으로 증명.',
  read: 'HTTP 201 성공 건수와 그 결과로 실제 생성된 Incident 건수를 나란히 비교한다.',
  verify: '요청 10건 전송 → HTTP 201 <b>10/10</b> 성공 → 생성된 OPEN Incident는 정확히 <b>1건</b>. 즉 "요청은 10건 다 처리됐지만 Resource 행 비관적 락(<code>SELECT ... FOR UPDATE</code>)이 임계구간을 직렬화해 9건은 기존 OPEN 사건을 재사용하고 신규 생성은 1건만 발생" — 10건 요청 대비 Incident 1건, 계산이 일치(PASS).',
});

// ============================================================
// [3-6] AOP 감사로그 검증 (본문 6)
// ============================================================
capturePage({
  num: 6, title: 'AOP 감사로그 검증', subtitle: '동일 action(METRIC_SIMULATE)의 FAIL 1건 + SUCCESS 2건 대조',
  cmd: `$ curl -s ".../api/audit-logs?targetType=MetricSnapshot&resultStatus=FAIL" -H "Authorization: Bearer $TOKEN"
$ curl -s ".../api/audit-logs?targetType=MetricSnapshot&resultStatus=SUCCESS&size=2" -H "Authorization: Bearer $TOKEN"`,
  imageSrc: img('09-auditlog-verification-both.png'), imageAlt: '감사로그 성공/실패 대조',
  imgMaxHeight: '128mm',
  proves: '요구사항 "AOP 감사로그, 성공/실패 100% 기록"을 실측으로 증명 — p.11의 의도적 404 실패 요청이 감사로그에도 빠짐없이 남았다.',
  read: 'FAIL 레코드의 errorMessage="404 NOT_FOUND \\"Resource not found: 999999\\""가 p.11 캡처의 응답과 동일한지 대조하고, SUCCESS 2건은 targetId가 실제 생성된 MetricSnapshot id와 일치하는지 확인한다.',
  verify: '같은 action="METRIC_SIMULATE"에 대해 실패 케이스(존재하지 않는 resourceId=999999)는 targetId=null·resultStatus=FAIL로, 성공 케이스는 targetId에 실제 생성된 스냅샷 id(259, 314)가 채워진 채로 SUCCESS 기록됨 — 성공/실패 모두 감사 가능하다는 설계 목표 실증.',
});

// ============================================================
// [3-7] Actuator 헬스체크 검증 (본문 7)
// ============================================================
capturePage({
  num: 7, title: 'Actuator 헬스체크 검증', subtitle: '1/5 — 초기 상태 UP',
  cmd: `$ curl -s http://localhost:8080/actuator/health | jq .`,
  imageSrc: img('10-actuator-01-up-before.png'), imageAlt: 'actuator UP before',
  proves: '커스텀 HealthIndicator(<code>incidentEngine</code>)가 <code>/actuator/health</code> 응답에 실제로 노출되어 있음을 증명.',
  read: 'components.incidentEngine.status="UP", details.unresolvedCriticalCount=0 확인.',
  verify: '이 상태를 기준선으로 다음 캡처에서 CRITICAL 사건을 강제 유발해 DOWN 전환을 재현한다.',
});
capturePage({
  num: 7, title: 'Actuator 헬스체크 검증', subtitle: '2/5 — CRITICAL 사건 강제 유발(errorRate=25.0)',
  cmd: `$ curl -i -X POST http://localhost:8080/api/metrics/simulate \\
    -H "Content-Type: application/json" \\
    -d '{"resourceId":12,"errorRate":25.0}'`,
  imageSrc: img('10-actuator-02-trigger-critical.png'), imageAlt: 'CRITICAL 유발',
  proves: 'errorRate≥20 구간이 CRITICAL 등급 규칙 경계임을 실측으로 증명.',
  read: 'HTTP 201, errorRate=25.0가 응답에 반영됨을 확인.',
  verify: '25.0% ≥ CRITICAL 임계치 20% → 다음 캡처에서 헬스체크가 DOWN으로 전환되는지 확인한다.',
});
capturePage({
  num: 7, title: 'Actuator 헬스체크 검증', subtitle: '3/5 — DOWN 확인(unresolvedCriticalCount=1)',
  cmd: `$ curl -s http://localhost:8080/actuator/health | jq .`,
  imageSrc: img('10-actuator-03-down-confirmed.png'), imageAlt: 'actuator DOWN',
  proves: '"최근 5분 내 미해결 CRITICAL 존재 시 DOWN" 커스텀 로직이 실제로 작동함을 증명.',
  read: 'components.incidentEngine.status="DOWN", details.unresolvedCriticalCount=1로 변경됨을 확인(db/ping 등 다른 컴포넌트는 여전히 UP).',
  verify: '전 단계에서 만든 CRITICAL Incident 1건이 미해결 상태이므로 unresolvedCriticalCount가 0→1로 정확히 증가.',
});
capturePage({
  num: 7, title: 'Actuator 헬스체크 검증', subtitle: '4/5 — PATCH resolve로 해결 처리',
  cmd: `$ curl -i -X PATCH http://localhost:8080/api/incidents/12/resolve \\
    -H "Authorization: Bearer $TOKEN"`,
  imageSrc: img('10-actuator-04-resolve.png'), imageAlt: 'resolve 처리',
  proves: '관리자 전용 resolve 엔드포인트가 정상 동작하며 status가 RESOLVED로 전이됨을 증명.',
  read: 'severity="CRITICAL", status="RESOLVED", aiSummary에 에스컬레이션 조치 근거 문장이 채워져 있음을 확인.',
  verify: 'id=12 사건이 RESOLVED로 전이 — 다음 캡처에서 헬스체크가 다시 UP으로 돌아오는지 확인한다.',
});
capturePage({
  num: 7, title: 'Actuator 헬스체크 검증', subtitle: '5/5 — 다시 UP으로 복귀',
  cmd: `$ curl -s http://localhost:8080/actuator/health | jq .`,
  imageSrc: img('10-actuator-05-up-after.png'), imageAlt: 'actuator UP after',
  proves: 'UP → DOWN → UP 전체 전환 사이클이 실제로 재현 가능함을 증명 — 요구사항 "Actuator 커스텀 HealthIndicator"의 완결된 실증.',
  read: 'components.incidentEngine.status="UP", unresolvedCriticalCount=0으로 복귀했는지 확인.',
  verify: 'resolve 처리 직후 미해결 CRITICAL 건수가 1→0으로 감소하며 상태가 즉시 UP으로 전환됨 — 헬스 인디케이터가 폴링이 아닌 실시간 DB 조회 기반임을 보여준다.',
});

// ============================================================
// [3-8] OpenAI 연동 검증 (본문 8)
// ============================================================
capturePage({
  num: 8, title: 'OpenAI 연동 검증', subtitle: '정상 케이스 — 실제 OpenAI가 생성한 자연어 요약(재게시)',
  cmd: `$ curl -s http://localhost:8080/api/incidents/10   # p.21과 동일 응답`,
  imageSrc: img('07-scenario-step4-incident-detail.png'), imageAlt: 'OpenAI 정상 요약',
  proves: 'OpenAI API(gpt-4o-mini) 연동이 실제로 라이브 동작하며, 판단(규칙엔진)과 설명(LLM)이 분리되어 있음을 증명.',
  read: 'aiSummary 필드가 고정 템플릿이 아니라 이 사건의 구체적 맥락(오류율 초과, 백업 조치)을 반영한 자연어 문장인지 확인한다.',
  verify: '규칙엔진이 이미 severity=HIGH, actions=[ALERT,BACKUP]을 확정한 뒤, LLM은 그 결정을 사후에 자연어로 설명만 했다 — 판단 자체를 LLM에 맡기지 않는다는 설계 원칙과 일치(p.4 FINOS Tier 2 근거).',
});
capturePage({
  num: 8, title: 'OpenAI 연동 검증', subtitle: '강제 타임아웃(1ms) → 폴백 템플릿 문장 확인',
  cmd: `$ OPENAI_TIMEOUT_MS=1 ./gradlew bootRun   # 서버 재기동, 타임아웃 강제
$ curl -s -X POST .../api/metrics/simulate -d '{"resourceId":9,"cpuUsage":95.0}'
$ curl -s http://localhost:8080/api/incidents/3`,
  imageSrc: img('11-openai-fallback.png'), imageAlt: 'OpenAI 폴백',
  proves: '요구사항 "OpenAI API 연동 + 폴백 처리"의 실패 경로를 실측으로 증명 — API 장애가 전체 흐름을 막지 않는다.',
  read: 'aiSummary가 "HIGH 등급의 QUEUE_DEPTH_EXCEEDED 이상이 감지되어 자동 조치가 실행되었습니다."라는 고정 템플릿 문장(<code>AiSummaryService.fallback()</code>)인지 확인한다.',
  verify: '타임아웃을 1ms로 강제해 OpenAI 호출이 반드시 실패하는 조건에서도 HTTP 요청 자체는 정상 처리(status=200 상당)되고 Incident 생성·조치기록은 그대로 완료됨 — 외부 API 장애가 핵심 흐름을 막지 않는다는 설계 목표 실증.',
});

// ============================================================
// [3-9] Git/협업 워크플로우 증빙 (본문 9)
// ============================================================
capturePage({
  num: 9, title: 'Git/협업 워크플로우 증빙', subtitle: 'GitHub 이슈 목록 — 17건 전체 CLOSED',
  cmd: `$ gh issue list --state closed --limit 20`,
  imageSrc: img('12-github-issues-closed.png'), imageAlt: 'issues closed',
  proves: '요구사항 "Git 워크플로우(이슈 연동)"를 증명 — 모든 작업 단위가 이슈로 추적되고 완료 처리됨.',
  read: '이슈 번호 1~17이 전부 CLOSED이고, 제목이 "[Feat] P0/P1/P2-N. ..." 컨벤션을 따르는지 확인한다.',
  verify: '이슈 개수 = 17개, 전부 CLOSED — 스프린트 P0(1~12) → P1(13~15) → P2(16~17) 순서와 04_일정관리.md의 스프린트 계획이 정확히 일치.',
});
capturePage({
  num: 9, title: 'Git/협업 워크플로우 증빙', subtitle: 'GitHub PR 목록 — 21건 전체 MERGED',
  cmd: `$ gh pr list --state merged --limit 30`,
  imageSrc: img('13-github-prs-merged.png'), imageAlt: 'PRs merged',
  proves: '요구사항 "Git 워크플로우(PR 템플릿·머지 이력)"를 증명 — 이슈 대비 PR이 4건 더 많은 이유(architect 검증 후속 수정 PR #35~38)까지 추적 가능.',
  read: 'PR #18~38까지 전부 MERGED, 브랜치명이 <code>feat/N-설명</code>·<code>fix/N-설명</code>·<code>docs/설명</code> 컨벤션을 따르는지 확인한다.',
  verify: 'PR 개수 = 21개 전부 MERGED. 이슈(17개)보다 PR이 많은 이유는 P0~P2 기능 개발 PR 17개 외에 릴리스 이후 발견된 architect 검증 결함 수정(#36) + 정직성 정리(#37) + AI 슬롭 정리(#38) 등 이슈 없이 진행된 후속 PR이 추가됐기 때문(p.37 참고).',
});
capturePage({
  num: 9, title: 'Git/협업 워크플로우 증빙', subtitle: 'git log --oneline --graph -30 — 커밋 컨벤션',
  cmd: `$ git log --oneline --graph -30`,
  imageSrc: img('14-git-log-graph.png'), imageAlt: 'git log graph',
  proves: '커밋 메시지가 Conventional Commits(<code>feat/fix/docs/test/chore</code> 접두사) + 이슈 번호(<code>#N</code>) 연동 컨벤션을 실제로 지켰음을 증명.',
  read: '각 커밋 메시지 접두사와 말미의 <code>(#N)</code> PR 번호 표기를 확인한다.',
  verify: '전체 태그 개수 12개(<code>git tag</code>) — Sprint 0~8 매 체크포인트마다 태그를 남겨 언제든 직전 완결 상태로 롤백 가능한 이력 관리 원칙을 지켰다.',
});

// ============================================================
// [3-10] 테스트 결과 (본문 10)
// ============================================================
capturePage({
  num: 10, title: '테스트 결과', subtitle: './gradlew clean test — 프레시 실행 (BUILD SUCCESSFUL)',
  cmd: `$ ./gradlew clean test --console=plain`,
  imageSrc: img('15-gradle-test-terminal.png'), imageAlt: 'gradle test terminal',
  proves: '요구사항 "테스트 코드"를 증명 — clean 상태에서 캐시 없이 전체 테스트를 프레시 실행해 결과를 확보했다.',
  read: 'BUILD SUCCESSFUL, 5 actionable tasks: 5 executed 확인.',
  verify: '45 tests, 0 failures, 0 ignored, 0.498s — 캐시된 이전 결과가 아니라 이번 세션에서 직접 재실행해 확보한 수치.',
});
capturePage({
  num: 10, title: '테스트 결과', subtitle: 'Gradle HTML 테스트 리포트 — 패키지별 분포',
  imageSrc: img('16-gradle-test-report-browser.png'), imageAlt: 'gradle test report',
  proves: '테스트가 동시성 재현(<code>IncidentDetectionConcurrencyTest</code>), 규칙엔진 경계값(파라미터화 13건), JWT, AI 폴백까지 고르게 커버함을 증명.',
  read: '<code>build/reports/tests/test/index.html</code>의 패키지별 테스트 건수와 100% successful 배지를 확인한다.',
  verify: '전체 45건, 실패 0건 — 규칙엔진 심각도 경계값(LOW/MEDIUM/HIGH/CRITICAL × CPU/MEM/QUEUE/ERROR 4개 규칙) 파라미터화 테스트가 가장 큰 비중을 차지한다.',
});

// ============================================================
// [3-11] 근거자료 요약 (본문 11)
// ============================================================
{
  const html = `
    <table class="fact-table">
      <thead><tr><th>출처</th><th>등급</th><th>핵심 인용 / 연결점</th></tr></thead>
      <tbody>
        <tr><td>Google, <i>Site Reliability Engineering</i> — Four Golden Signals</td><td>1차</td><td>지연·트래픽·오류·포화도. <code>MetricSnapshot</code>의 cpuUsage/memUsage(포화도)·errorRate(오류)·queueDepth(트래픽/포화도)가 이 프레임워크를 그대로 반영.</td></tr>
        <tr><td>베이징대, <i>AIOps Survey for LLM Era</i>(arXiv:2406.11213)</td><td>1차</td><td>전처리→이상탐지→근본원인분석→자동복구 4단계. <code>MetricSnapshot→RuleEngine→Incident(ruleTriggered)→IncidentAction</code> 구조와 1:1 대응.</td></tr>
        <tr><td>FINOS, <i>Agent Decision Audit and Explainability</i></td><td>1차</td><td>"Tier 2: 명시적 추론이 도구 호출 전에 생성·기록, 자연어 설명 포함". 규칙엔진 결정(Decision) → LLM 설명(Explanation) → AOP 감사기록 순서와 정확히 일치.</td></tr>
        <tr><td>SK AX × 대신증권 7년 계약(2026.4, 8개 매체 교차검증 + 원문 확보)</td><td>1차</td><td>전체 기획의 출발점 — "선제적 탐지·분석·판단·조치" 개념적 파이프라인.</td></tr>
        <tr><td>Zalando Engineering Blog, AI 사후분석 2년 운영 사례</td><td>준1차</td><td>"AI 요약은 만능이 아니다" — OpenAI 실패 시 폴백 설계의 실무적 근거.</td></tr>
      </tbody>
    </table>
    <p class="p">이 5개 출처는 성격이 서로 다르다 — 학술 논문(베이징대), 산업 거버넌스 표준(FINOS), 업계 표준 텍스트(Google SRE), 실제 기업 사례(SK AX), 빅테크 실무 회고(Zalando). 서로 독립적인 5개 축이 "지표 감시 → 이상탐지 → 판단 → 설명 → 감사"라는 같은 구조로 수렴한다는 점이 이 설계가 즉흥적 발상이 아니라는 근거다. 출처 등급 기준과 각 항목의 비판적 평가(예: Gartner AIOps 정의 페이지의 한계, arXiv 프리프린트의 동료심사 여부)는 <code>docs/07_배경조사_근거자료집.md</code>에 전문 수록했다.</p>`;
  textPage({ num: 11, title: '근거자료 요약', subtitle: '학술·산업·기업 근거의 삼각검증', html });
}

// ============================================================
// [4] 의견 · 개선사항 — 4개 소제목, 2페이지 고정
// ============================================================
{
  const html = `
    <h3 class="op-h">1. 막혔던 부분 &amp; 해결 과정</h3>
    <p class="p op">독립 architect(Opus) 검증에서 <b>REJECTED</b> 판정을 받은 뒤 실제 재현 → 수정 → 재검증한 결함 5건(C1~C5)이 가장 구체적인 사례다. <b>C1</b>: <code>AuditLogAspect.extractTargetId</code>가 반환값에서 id를 못 찾으면 메서드 인자 중 아무 <code>getId()</code>나 가져다 썼는데, <code>detectAndCreate()</code>가 정상 지표(약 90% 확률)에서 <code>Optional.empty()</code>를 반환해도 이 폴백이 발동해 <b>인자로 넘어온 MetricSnapshot의 id가 targetType="Incident" 감사로그의 targetId로 잘못 기록</b>됐다 — Optional.empty()가 "타겟 없음"이라는 확정 신호였는데 그 신호를 무시하고 엉뚱한 id를 삼킨 것. 반환 타입이 Optional이면 그 값을 그대로 신뢰하도록 수정. <b>C2</b>: HikariCP 풀을 3으로 좁히고 40건 동시요청을 보내자 클라이언트 실패가 40건 발생했는데, <code>recordFailure()</code>(REQUIRES_NEW, 별도 커넥션 필요)가 커넥션 고갈로 자체 실패하면 <b>그 예외가 원래 비즈니스 예외를 덮어써</b> FAIL 감사로그가 40건 중 4건만 남았다(약 90% 유실) — "커넥션 풀 고갈이 원래 예외를 삼켰다". 감사기록 호출 자체를 try-catch로 한 번 더 감싸 39건 중 34건(87%)까지 개선(정상 pool=30에서는 40/40 전량 기록). <b>C3</b>: <code>GlobalExceptionHandler</code>가 <code>ResponseEntityExceptionHandler</code>를 상속하지 않아 Spring MVC 프레임워크 예외(깨진 JSON, 405, 404)가 최상위 catch-all로 떨어져 전부 500이 됐던 것을 상속·오버라이드로 수정. <b>flaky test</b>: <code>IncidentActionServiceTest</code>가 4개 지표 필드 중 1개만 지정하고 나머지를 null로 둬 <code>MetricService</code>의 10% 확률 이상치 혼입 로직에 의존했는데, 드물게 더 심각한 규칙이 함께 트리거되며 반복 빌드마다 간헐적으로 실패했다 — 4개 필드를 모두 명시적 안전값으로 채워 결정론적으로 만들어 근본 원인을 제거했다.</p>
    <h3 class="op-h">2. 왜 이 방법을 선택했나</h3>
    <p class="p op">모듈러 모놀리식 vs MSA는 "혼자·제한시간" 조건에서 서비스 간 통신·분산 트랜잭션·배포 파이프라인을 감당할 실익이 없다고 판단해 모듈러 모놀리식을 택했다(p.5 ADR). 규칙엔진(판단)+LLM(설명) 분리는 FINOS Tier 2 요구사항과 "이게 진짜 AI냐"는 과장 논란을 피하기 위한 의도적 설계다 — 판단을 LLM에 맡기면 재현성·설명력·테스트 용이성이 모두 떨어진다. JPA(CRUD)/MyBatis(집계) 분담은 다중 테이블 집계를 JPQL로 표현하면 가독성이 떨어지고 실행계획을 예측하기 어렵다는 실무적 판단에서다. 동시성 제어는 원래 <code>Incident.version</code> 낙관적 락을 계획했으나, H2가 부분 unique 인덱스(<code>resourceId+status=OPEN</code>)를 지원하지 않고 <b>낙관적 락은 이미 존재하는 같은 row를 다시 쓸 때만 충돌을 감지할 뿐 서로 다른 두 새 row가 동시에 insert되는 상황 자체는 막지 못한다</b>는 점이 드러나 Resource 행 비관적 락으로 전환했다.</p>`;
  textPage({ num: 4, title: '의견 · 개선사항', subtitle: '1. 막혔던 부분 & 해결 과정  ·  2. 왜 이 방법을 선택했나', html });
}
{
  const html = `
    <h3 class="op-h">3. 개선 사항 / 코드 품질 의견</h3>
    <p class="p op">현재 코드의 한계를 스스로 지적한다. (1) 실제 인프라가 아닌 시뮬레이션 데이터라, 학술논문·FINOS 문서가 전제하는 "실제 프로덕션 규모 로그·트래픽"은 다루지 않는다. (2) AOP(<code>AuditLogAspect</code>)는 로깅(감사) 관심사 하나만 다룬다 — 트랜잭션 재시도, 성능 계측 등 다른 cross-cutting concern으로는 아직 확장하지 않았다. (3) 관리자 계정이 <code>admin</code> 하나뿐인 최소 구현이다 — 회원가입, role 구분, 토큰 재발급/폐기(refresh/revoke) 같은 정식 인가 체계는 이 과제 스케일에서 과설계라 판단해 의도적으로 제외했다. (4) <code>JWT_SECRET</code>을 고정하지 않으면 재기동마다 랜덤 키가 생성돼 기존 토큰이 무효화된다 — 데모 편의를 위한 의도적 타협이며, 운영 환경에서는 반드시 고정값을 지정해야 한다. (5) <code>/h2-console</code>이 기본 프로필에서 별도 인증 없이 노출된다 — 로컬 데모 편의 설정으로, PostgreSQL 프로필에는 해당하지 않지만 운영 준하는 환경이라면 비활성화가 필수다. (6) 테스트 45건은 규칙엔진 경계값·동시성·JWT·AI 폴백을 커버하지만, 컨트롤러 계층 통합테스트(MockMvc 기반 API E2E)는 상대적으로 얇다.</p>
    <h3 class="op-h">4. 한계의 솔직한 인정</h3>
    <p class="p op">기획의 핵심 근거인 SK AX × 대신증권 계약의 1차 소스(공식 뉴스룸)는 이 세션 초반 robots.txt 차단으로 자동 fetch가 3회 모두 실패해, 처음에는 8개 언론사의 2차 보도 교차검증으로만 근거를 구성했었다. 이후 사용자가 직접 브라우저로 접속해 풀페이지 스크린샷(PDF+PNG)을 확보해줘서 원문 텍스트까지 인용할 수 있었다(부록 p.43) — 이 과정 자체를 "확인 못 했던 것을 나중에 확인했다"고 정직하게 기록한다. 원문에는 대신증권 대표이사가 "진승욱"으로 기재돼 있으나, 실제 알려진 대표 체제(오익근·이어룡 각자대표)와 차이가 있을 수 있어 — 검증 없이 임의로 정정하지 않고 <b>SK AX 공식 발표 원문에 기재된 대로</b> 인용했음을 명시한다. 또한 "성과보고서"는 계약 개시(2026.4)로부터 아직 3.5개월밖에 지나지 않아 1단계 시범 적용 진행 중으로 추정되며, 대신증권 건에 특정된 후속 성과 보도는 확인되지 않았다 — 이 프로젝트는 계약의 "설계 철학"을 구현 대상으로 삼았을 뿐 실제 계약의 성과를 검증하거나 대변하지 않는다. 마지막으로, 내부 기획서·제안서·성과보고서 원문은 B2B 계약의 영업비밀 성격상 애초에 공개될 수 없는 문서라는 점도 조사 실패가 아니라 사실관계상 당연한 결과로 판단해 대체 근거(원문 보도자료+8개 매체 교차검증+AXgenticWire 브랜드 공식자료)로 근거 밀도를 최대화했다.</p>`;
  textPage({ num: 4, title: '의견 · 개선사항', subtitle: '3. 개선 사항 / 코드 품질 의견  ·  4. 한계의 솔직한 인정', html });
}

// ============================================================
// [5] 부록
// ============================================================
{
  const gitlog = fs.readFileSync(path.join(CAP, 'raw-logs/14-git-log-graph.txt'), 'utf-8').trim();
  const html = `
    <p class="p">아래는 <code>docs/pdf/captures/raw-logs/</code>에 저장된 원본 텍스트 로그 중 전체 이력을 보여주는 <code>git log</code> 전문이다(캡처 이미지는 p.33 참고). curl 원본 로그 26건은 저장소 <code>docs/pdf/captures/raw-logs/*.txt</code>에 캡처와 동일 파일명으로 함께 보관되어 있다.</p>
    <pre class="cmd small appendix-log">${esc(gitlog)}</pre>`;
  textPage({ num: '부록 A', title: '부록 — 원본 로그: git log 전문', subtitle: 'docs/pdf/captures/raw-logs/14-git-log-graph.txt', html });
}
{
  const issues = fs.readFileSync(path.join(CAP, 'raw-logs/12-github-issues-closed.txt'), 'utf-8').trim();
  const html = `<pre class="cmd small appendix-log">${esc(issues)}</pre>`;
  textPage({ num: '부록 B', title: '부록 — 이슈 원본 목록', subtitle: 'gh issue list --state closed 실제 출력 전문 (17건, 전체 CLOSED)', html });
}
{
  const prs = fs.readFileSync(path.join(CAP, 'raw-logs/13-github-prs-merged.txt'), 'utf-8').trim();
  const html = `<pre class="cmd small appendix-log">${esc(prs)}</pre>`;
  textPage({ num: '부록 B', title: '부록 — PR 원본 목록', subtitle: 'gh pr list --state merged 실제 출력 전문 (21건, 전체 MERGED)', html });
}
{
  const html = `
    <h3 class="mini-h">스프린트 진행 계획 (docs/04_일정관리.md)</h3>
    <table class="fact-table">
      <thead><tr><th>스프린트</th><th>범위</th><th>완료 태그</th></tr></thead>
      <tbody>
        <tr><td>Sprint 0</td><td>프로젝트 초기화, 엔티티, Repository</td><td>v0-skeleton</td></tr>
        <tr><td>Sprint 1</td><td>지표 시뮬레이터 + 규칙엔진 + Incident + 동시성 제어</td><td>v1-core-flow</td></tr>
        <tr><td>Sprint 2</td><td>AOP 감사로그 + 예외처리 + Controller + Swagger</td><td>v2-api-complete</td></tr>
        <tr><td>Sprint 3 (P0 완료)</td><td>MyBatis 집계 + Actuator 커스텀 헬스체크</td><td>v3-analytics / v-submittable</td></tr>
        <tr><td>Sprint 4</td><td>OpenAI API 연동(판단근거 요약)</td><td>v4-ai-summary</td></tr>
        <tr><td>Sprint 5</td><td>JWT 최소 구현</td><td>v5-auth</td></tr>
        <tr><td>Sprint 6</td><td>테스트 코드(동시성 재현 + 단위테스트)</td><td>v6-tested</td></tr>
        <tr><td>Sprint 7</td><td>Docker Compose, 정적 대시보드</td><td>v7-polish / v1.0.0</td></tr>
        <tr><td>Sprint 8 (사후)</td><td>architect 최종검증(C1~C5) 수정</td><td>v1.0.1</td></tr>
      </tbody>
    </table>
    <p class="p-note">매 스프린트 종료 시 <code>git tag</code>를 남겨 언제든 직전 태그로 롤백해 "제출 가능 상태"를 잃지 않는 체크포인트 원칙을 지켰다. 전체 12개 태그는 <code>git tag</code> 실행 결과와 일치한다.</p>
    <h3 class="mini-h">SK AX 뉴스룸 원문 자료</h3>
    <p class="p-note">SK AX 공식 뉴스룸(<a href="https://www.skax.co.kr/company/news-room/sk-ax-대신증권-에이전틱ai로-금융인프라-운영-혁신-나섰다">skax.co.kr/company/news-room/...</a>)을 2026-08-09 사용자가 직접 브라우저로 접속해 확보한 풀페이지 캡처(PDF+PNG)를 다음 페이지에 원본 그대로 첨부한다.</p>`;
  textPage({ num: '부록 C', title: '부록 — 스프린트 실측 기록 & 원문 확인 자료 안내', subtitle: 'docs/04_일정관리.md 요약 · SK AX 원본 캡처 안내', html });
}
{
  const html = `
    <div class="img-wrap appendix-img">
      <img src="${imgOrig('screencapture-skax-co-kr-company-news-room-sk-ax-ai-2026-08-09-03_13_55.png')}" alt="SK AX 뉴스룸 원문 풀페이지 캡처" />
    </div>
    <p class="p-note">SK AX 공식 뉴스룸 "SK AX-대신증권, 에이전틱AI로 금융인프라 운영 혁신 나섰다"(2026.04.23 게시) 풀페이지 스크린샷. 2026-08-09 03:13:55에 사용자가 직접 브라우저로 접속해 캡처했다(robots.txt 차단으로 자동 fetch 실패 이후 확보한 1차 소스 원문). 동일 캡처의 PDF 원본은 <code>docs/pdf/원본캡처/</code>에 함께 보관되어 있다.</p>`;
  textPage({ num: '부록 D', title: '부록 — SK AX 뉴스룸 원문 캡처', subtitle: '1차 소스 확보 자료 (2026-08-09 직접 캡처)', html, center: true });
}

console.log(`총 페이지 수: ${pages.length}`);

// ============================================================
// HTML 조립
// ============================================================
const CSS = `
:root{
  --ink: #14161a; --ink-2: #5b6470; --ink-3: #98a1ac;
  --accent: #1f7a5a; --accent-sub: #e8f2ee; --line: #dfe3e8;
  --bg: #ffffff; --code-bg: #1b1f24; --code-fg: #d6dae0;
}
*{ box-sizing: border-box; }
html,body{ margin:0; padding:0; background:var(--bg); color:var(--ink);
  font-family: -apple-system, "Apple SD Gothic Neo", "Pretendard", "Noto Sans KR", sans-serif;
}
a{ color: var(--accent); text-decoration: none; }
code{ font-family: "SF Mono","D2Coding","JetBrains Mono", monospace; background: var(--accent-sub); color:#155c42; padding: 0 3px; border-radius: 3px; font-size: 0.92em; }
.page{ min-height: 263mm; display:flex; flex-direction:column; page-break-after: always; }
.page:last-child{ page-break-after: auto; }
.page-head h1{ font-size: 20pt; font-weight:700; margin: 0 0 3mm 0; letter-spacing:-0.01em; }
.page-head h1 .num{ color: var(--accent); }
.page-head .subtitle{ font-size: 10.5pt; font-weight:400; color: var(--ink-2); margin: 0 0 4mm 0; }
.rule-strong{ border:none; border-top: 1.6pt solid var(--ink); margin: 0 0 6mm 0; }
.rule-thin{ border:none; border-top: 0.8pt solid var(--line); margin: 5mm 0 3mm 0; }
.page-body{ flex: 1 1 auto; display:flex; flex-direction: column; }
.page-body.text-body{ justify-content: flex-start; }
.page-body.capture-body{ justify-content: center; align-items:stretch; }
.page-foot{ flex: 0 0 auto; margin-top: 6mm; }
.footer-text{ display:flex; justify-content: space-between; font-size: 8.5pt; color: var(--ink-3); }

.p{ font-size: 10pt; line-height: 1.65; margin: 0 0 3mm 0; text-align: justify; }
.p.op{ margin-bottom: 5mm; }
.p-note{ font-size: 9pt; line-height: 1.55; color: var(--ink-2); margin: 2mm 0 0 0; }
.mini-h{ font-size: 11.5pt; font-weight:700; margin: 5mm 0 2.5mm 0; color: var(--ink); }
.op-h{ font-size: 12.5pt; font-weight:700; margin: 0 0 2.5mm 0; color: var(--accent); }
.bullet{ margin: 0 0 3mm 0; padding-left: 4.5mm; font-size: 10pt; line-height:1.6; }
.bullet li{ margin-bottom: 1.6mm; }

.cmd{ font-family: "SF Mono","D2Coding","JetBrains Mono", monospace; background: var(--code-bg); color: var(--code-fg);
  font-size: 8.6pt; line-height: 1.55; padding: 3mm 4mm; border-radius: 5px; white-space: pre-wrap; word-break: break-word; margin: 0 0 3.5mm 0; }
.cmd.small{ font-size: 8pt; }
.cmd.appendix-log{ font-size: 7.6pt; max-height: 235mm; overflow:hidden; }

.explain{ font-size: 10pt; line-height: 1.65; margin-top: 3.5mm; }
.explain p{ margin: 0 0 1.6mm 0; }
.explain .tri{ color: var(--accent); margin-right: 1.5mm; }

.img-wrap{ display:flex; justify-content:center; align-items:center; margin: 2mm 0; }
.img-wrap img{ max-width: 100%; max-height: 148mm; width:auto; height:auto; display:block;
  border: 0.8pt solid var(--line); border-radius: 4px; box-shadow: 0 1px 4px rgba(0,0,0,0.06); }
.img-wrap.appendix-img img{ max-height: 172mm; }

table{ width:100%; border-collapse: collapse; font-size: 9.3pt; margin-bottom: 3mm; }
.req-table th:last-child, .req-table td:last-child{ width: 14mm; }
th,td{ border: 0.8pt solid var(--line); padding: 1.8mm 2.4mm; text-align:left; vertical-align:top; line-height:1.45; }
th{ background: var(--accent-sub); color:#155c42; font-weight:700; }
.req-table td.pass{ color: var(--accent); font-weight:700; white-space:nowrap; }
.fact-table td:first-child{ font-weight:600; }
.cover-meta td.pass, .fail{ font-weight:700; }
td.fail{ color:#a33; white-space:nowrap; }
.fact-table td.pass{ white-space:nowrap; }

.two-col{ display:flex; gap: 8mm; }
.two-col > div{ flex:1; min-width:0; }
.toc-list{ list-style:none; counter-reset: toc; margin:0; padding:0; font-size:10pt; }
.toc-list li{ counter-increment: toc; display:flex; justify-content:space-between; padding: 1.6mm 0; border-bottom: 0.6pt dotted var(--line); }
.toc-list li::before{ content: counter(toc) ". "; color: var(--accent); font-weight:700; margin-right:1.5mm; }
.toc-list li span{ color: var(--ink-3); }

.quote-box{ background: var(--accent-sub); border-left: 3pt solid var(--accent); padding: 3.5mm 4.5mm; margin: 2mm 0 3.5mm 0; border-radius: 3px; }
.quote-box .quote-src{ font-size: 8.5pt; color: var(--ink-2); margin-bottom: 2mm; }
.quote-box p{ font-size: 9.6pt; line-height:1.6; margin: 0 0 2mm 0; }
.quote-box p:last-child{ margin-bottom:0; }

.flow{ margin: 2mm 0 4mm 0; }
.flow-row{ display:flex; align-items:center; gap: 2mm; margin-bottom: 2mm; }
.fbox{ flex:1; border: 1pt solid var(--line); border-radius: 5px; padding: 2.4mm 2.8mm; font-size: 8.6pt; line-height:1.4; text-align:center; background:#fafbfb; }
.fbox.wide{ flex: 3.3; }
.fbox.accent{ border-color: var(--accent); background: var(--accent-sub); font-weight:600; }
.fbox.accent2{ border-color: var(--accent); background: #eef7f3; color:#155c42; font-weight:600; font-size: 9pt; }
.fbox.ghost{ visibility:hidden; padding:0; border:none; }
.farrow{ flex: 0 0 auto; color: var(--ink-3); font-size: 9pt; min-width: 8mm; text-align:center; }

/* 표지 */
.cover{ display:flex; flex-direction:column; align-items:flex-start; justify-content:center; height:100%; padding-top: 20mm; }
.cover-eyebrow{ font-size: 10.5pt; color: var(--ink-2); margin-bottom: 4mm; letter-spacing: 0.02em; }
.cover-title{ font-size: 34pt; font-weight:800; margin:0; letter-spacing:-0.02em; }
.cover-sub{ font-size: 13pt; color: var(--accent); font-weight:600; margin: 2mm 0 6mm 0; }
.cover .rule-strong{ width:100%; margin-bottom: 6mm; }
.cover-meta{ width:100%; margin-bottom: 6mm; }
.cover-meta td{ border:none; padding: 1.5mm 0; font-size: 10.3pt; }
.cover-meta td:first-child{ width: 32mm; color: var(--ink-2); font-weight:600; }
.cover-links{ width:100%; margin-bottom: 6mm; }
.cover-links-title{ font-size: 9.5pt; color: var(--ink-2); margin-bottom: 2mm; font-weight:600; }
.cover-links ul{ margin:0; padding-left: 4.5mm; font-size: 9.6pt; line-height: 1.85; }
.cover-note{ font-size: 9pt; color: var(--ink-2); line-height:1.7; margin-top: 2mm; }
`;

const bodyHtml = pages.map((p, i) => {
  const pnum = i + 1;
  const footer = `
    <div class="page-foot">
      <hr class="rule-thin" />
      <div class="footer-text"><span>${FOOTER_NAME}</span><span>p.${pnum}</span></div>
    </div>`;
  return `<div class="page">${p.head}${p.body}${footer}</div>`;
}).join('\n');

const fullHtml = `<!doctype html><html lang="ko"><head><meta charset="utf-8"/><title>G062_장병헌_백엔드최종실습</title>
<style>${CSS}</style></head><body>${bodyHtml}</body></html>`;

fs.writeFileSync('/private/tmp/claude-501/-Users-jangbyeongheon/50ceb8c1-fa03-49a8-9de3-f2a88820970f/scratchpad/pdfbuild/rendered.html', fullHtml);

const browser = await chromium.launch();
const page = await browser.newPage();
await page.setContent(fullHtml, { waitUntil: 'networkidle' });
await page.pdf({
  path: OUT,
  format: 'A4',
  printBackground: true,
  margin: { top: '18mm', bottom: '16mm', left: '16mm', right: '16mm' },
});
await browser.close();

console.log('DONE ->', OUT);
