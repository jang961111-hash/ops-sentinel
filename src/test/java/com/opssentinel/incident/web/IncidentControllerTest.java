package com.opssentinel.incident.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.opssentinel.common.security.JwtTokenProvider;
import com.opssentinel.incident.entity.Incident;
import com.opssentinel.incident.entity.IncidentSeverity;
import com.opssentinel.incident.entity.IncidentStatus;
import com.opssentinel.incident.repository.IncidentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 컨트롤러 계층 스모크 테스트 — architect 최종검증이 지적한 "웹계층(MockMvc) 테스트 전무"
 * 갭 중, 이번 결함수정(C3/M1)이 실제 HTTP 디스패치 경로(컨트롤러 → GlobalExceptionHandler
 * 포함)에서도 성립하는지를 최소 범위로 고정한다. 다른 서비스 테스트들과 달리 서비스
 * 메서드를 직접 호출하지 않고 {@link MockMvc}로 실제 {@link IncidentController} 엔드포인트를
 * 호출한다.
 *
 * <p>M2(JwtAuthenticationFilter의 HEAD 우회 방지)는 이 테스트가 아니라
 * {@code JwtAuthenticationFilterTest}가 담당한다 — {@code @AutoConfigureMockMvc}의 기본
 * 필터 자동등록이 이 프로젝트의 MOCK 웹 환경 테스트 컨텍스트에서 커스텀 {@code Filter} 빈을
 * 반영하지 않는 것이 실측 확인되어(같은 요청을 curl로 실제 기동한 앱에 보내면 정상적으로
 * 401이 나옴 — 운영 동작 자체는 문제 없고 이 MockMvc 하네스만 필터를 누락시킴), 필터
 * 자체는 서블릿 컨테이너 목업 없이 직접 단위테스트하는 편이 더 안정적이다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class IncidentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private IncidentRepository incidentRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    /** C3 회귀 확인: 존재하지 않는 사건 조회가 500이 아니라 공통 에러 포맷의 404로 응답되는지. */
    @Test
    void 존재하지_않는_사건_조회는_404와_공통_에러_포맷으로_응답한다() throws Exception {
        mockMvc.perform(get("/api/incidents/{id}", Long.MAX_VALUE))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.timestamp", notNullValue()))
                .andExpect(jsonPath("$.status", is(404)))
                .andExpect(jsonPath("$.path", is("/api/incidents/" + Long.MAX_VALUE)));
    }

    /** M1 회귀 확인: 실제 HTTP 경로로 resolve를 두 번 호출해도 최초 resolvedAt이 재설정되지 않는지. */
    @Test
    void 유효한_토큰으로_resolve를_두_번_호출해도_resolvedAt은_바뀌지_않는다() throws Exception {
        Incident incident = incidentRepository.save(newDetectedIncident());
        String bearer = "Bearer " + jwtTokenProvider.generateToken("admin");

        mockMvc.perform(patch("/api/incidents/{id}/resolve", incident.getId())
                        .header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("RESOLVED")));
        var firstResolvedAt = incidentRepository.findById(incident.getId()).orElseThrow().getResolvedAt();

        Thread.sleep(10);

        mockMvc.perform(patch("/api/incidents/{id}/resolve", incident.getId())
                        .header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("RESOLVED")));
        var secondResolvedAt = incidentRepository.findById(incident.getId()).orElseThrow().getResolvedAt();

        assertThat(secondResolvedAt).isEqualTo(firstResolvedAt);
    }

    private Incident newDetectedIncident() {
        return Incident.builder()
                .resourceId(1L)
                .severity(IncidentSeverity.LOW)
                .status(IncidentStatus.DETECTED)
                .ruleTriggered("TEST_RULE")
                .build();
    }
}
