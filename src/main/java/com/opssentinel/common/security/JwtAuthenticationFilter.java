package com.opssentinel.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opssentinel.common.web.dto.ErrorResponse;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 관리자 전용 엔드포인트에만 {@code Authorization: Bearer <token>} 검증을 강제하는 최소 구현
 * 인증 필터(US-014, API 명세서 "공통 사항" — {@code GET /api/audit-logs},
 * {@code PATCH /api/incidents/{id}/resolve} 두 개만 보호 대상).
 *
 * <p><b>왜 Spring Security 풀스택 대신 이 방식을 택했는가:</b> 이 프로젝트는 회원가입/권한
 * 레벨/세션 등 인가 체계가 없고 "고정된 admin 하나가 관리자 엔드포인트 두 개만 두드릴 수
 * 있는가"만 검증하면 된다. SecurityFilterChain, UserDetailsService, PasswordEncoder,
 * AuthenticationManager 같은 풀스택 구성요소를 도입하면 이 과제 스케일에 맞지 않는 과설계가
 * 된다(PRD 6장 "JWT는 최소 구현"). jjwt로 토큰만 발급·검증하고, 보호 대상 두 경로만 좁게
 * 가로채는 커스텀 필터가 요구사항 대비 가장 작은 구현이다.
 *
 * <p><b>왜 URL 패턴 등록이 아니라 필터 내부에서 메서드+경로를 함께 확인하는가:</b>
 * {@code /api/incidents/{id}/resolve}처럼 경로 변수가 섞인 패턴을 서블릿 {@code urlPatterns}로
 * 등록하면 형제 경로(예: {@code GET /api/incidents/{id}})까지 함께 걸릴 위험이 있다. 필터는
 * 기본적으로 모든 요청("/*")에 대해 실행되게 두고, {@link #PROTECTED_ROUTES}에 정의한
 * (HTTP 메서드, {@link AntPathMatcher} 패턴) 쌍과 정확히 일치할 때만 인증을 강제해 보호
 * 범위를 명세서 그대로로 좁힌다.
 *
 * <p><b>왜 예외를 GlobalExceptionHandler로 넘기지 않는가:</b> 이 필터는 DispatcherServlet
 * 앞단에서 실행되므로 {@code @RestControllerAdvice}의 적용 범위 밖이다. 응답 포맷 일관성
 * ({@code {timestamp, status, error, message, path}})을 지키기 위해 {@link ErrorResponse}를
 * 직접 조립해 응답 바디를 쓴다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final List<ProtectedRoute> PROTECTED_ROUTES = List.of(
            new ProtectedRoute(HttpMethod.GET, "/api/audit-logs"),
            new ProtectedRoute(HttpMethod.PATCH, "/api/incidents/*/resolve")
    );

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenProvider jwtTokenProvider;
    private final ObjectMapper objectMapper;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (!requiresAuth(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            writeError(response, request, "인증 토큰이 필요합니다.");
            return;
        }

        String token = header.substring(BEARER_PREFIX.length());
        try {
            jwtTokenProvider.validate(token);
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("JWT 검증 실패: {}", e.getMessage());
            writeError(response, request, "유효하지 않거나 만료된 토큰입니다.");
            return;
        }

        filterChain.doFilter(request, response);
    }

    /**
     * HEAD 요청은 Spring MVC가 {@code @GetMapping}에 암묵적으로 매핑해 실제로 같은
     * 컨트롤러 메서드를 실행한다(본문만 응답에 쓰지 않을 뿐 비즈니스 로직은 그대로 탐).
     * 그런데 이 메서드가 {@link HttpMethod#GET}만 정확히 비교하면 {@code HEAD}는
     * {@link #PROTECTED_ROUTES}의 어떤 항목과도 일치하지 않아 인증 없이 그대로
     * 통과해버린다({@code GET /api/audit-logs}는 401인데 {@code HEAD /api/audit-logs}는
     * 인증 우회로 200이 나가는 문제). GET으로 보호되는 라우트는 HEAD도 같은 라우트로
     * 취급해 이 우회를 막는다.
     */
    private boolean requiresAuth(HttpServletRequest request) {
        HttpMethod method = HttpMethod.valueOf(request.getMethod());
        HttpMethod matchMethod = method == HttpMethod.HEAD ? HttpMethod.GET : method;
        String path = request.getServletPath();
        return PROTECTED_ROUTES.stream()
                .anyMatch(route -> route.method().equals(matchMethod) && pathMatcher.match(route.pattern(), path));
    }

    private void writeError(HttpServletResponse response, HttpServletRequest request, String message)
            throws IOException {
        ErrorResponse body = ErrorResponse.of(HttpStatus.UNAUTHORIZED, message, request.getRequestURI());
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }

    private record ProtectedRoute(HttpMethod method, String pattern) {
    }
}
