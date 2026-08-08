package com.opssentinel.common.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * {@link JwtAuthenticationFilter} 자체를 서블릿 컨테이너/MockMvc 없이 직접 단위테스트한다.
 *
 * <p>M2 회귀 확인: 보호 대상 라우트({@code GET /api/audit-logs})를 {@code HEAD}로 호출해도
 * {@code GET}과 동일하게 인증이 강제되는지 — 수정 전에는 {@code HEAD}가 어떤
 * {@code PROTECTED_ROUTES} 항목과도 일치하지 않아 인증 우회로 200이 나갔다.
 *
 * <p>{@code MockHttpServletRequest}는 기본적으로 서블릿을 "/"에 매핑한 것처럼
 * {@code getServletPath()}가 전체 경로를 반환하므로, 필터가 실제 배포 환경에서 보는
 * 요청과 동일한 조건으로 검증된다.
 */
class JwtAuthenticationFilterTest {

    private JwtAuthenticationFilter filter;
    private JwtTokenProvider jwtTokenProvider;

    @BeforeEach
    void setUp() {
        jwtTokenProvider = new JwtTokenProvider("test-secret-abcdefg", 60_000L);
        filter = new JwtAuthenticationFilter(jwtTokenProvider, new ObjectMapper().registerModule(new JavaTimeModule()));
    }

    @Test
    void 보호되지_않은_라우트는_토큰_없이도_통과한다() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/incidents/1");
        request.setServletPath("/api/incidents/1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        RecordingFilterChain chain = new RecordingFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.invoked).isTrue();
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void 보호된_라우트를_GET으로_토큰_없이_호출하면_401이고_체인이_실행되지_않는다() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/audit-logs");
        request.setServletPath("/api/audit-logs");
        MockHttpServletResponse response = new MockHttpServletResponse();
        RecordingFilterChain chain = new RecordingFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.invoked).isFalse();
        assertThat(response.getStatus()).isEqualTo(401);
    }

    /** M2 회귀: HEAD도 같은 보호 대상 라우트의 GET과 동일하게 인증을 요구해야 한다. */
    @Test
    void 보호된_라우트를_HEAD로_토큰_없이_호출해도_401이다() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("HEAD", "/api/audit-logs");
        request.setServletPath("/api/audit-logs");
        MockHttpServletResponse response = new MockHttpServletResponse();
        RecordingFilterChain chain = new RecordingFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.invoked).isFalse();
        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    void 보호된_라우트를_유효한_토큰으로_호출하면_통과한다() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("PATCH", "/api/incidents/5/resolve");
        request.setServletPath("/api/incidents/5/resolve");
        request.addHeader("Authorization", "Bearer " + jwtTokenProvider.generateToken("admin"));
        MockHttpServletResponse response = new MockHttpServletResponse();
        RecordingFilterChain chain = new RecordingFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.invoked).isTrue();
    }

    @Test
    void 보호된_라우트를_유효하지_않은_토큰으로_호출하면_401이다() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("PATCH", "/api/incidents/5/resolve");
        request.setServletPath("/api/incidents/5/resolve");
        request.addHeader("Authorization", "Bearer not-a-real-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        RecordingFilterChain chain = new RecordingFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.invoked).isFalse();
        assertThat(response.getStatus()).isEqualTo(401);
    }

    private static class RecordingFilterChain extends MockFilterChain {
        private boolean invoked = false;

        @Override
        public void doFilter(jakarta.servlet.ServletRequest request, jakarta.servlet.ServletResponse response) {
            invoked = true;
        }
    }
}
