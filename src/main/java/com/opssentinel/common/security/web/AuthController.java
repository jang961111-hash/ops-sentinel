package com.opssentinel.common.security.web;

import com.opssentinel.common.security.JwtTokenProvider;
import com.opssentinel.common.security.web.dto.TokenRequest;
import com.opssentinel.common.security.web.dto.TokenResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * 데모용 관리자 토큰 발급 엔드포인트(US-014). 별도 회원 DB 없이 고정된 관리자
 * 계정(username={@code admin}, password={@code admin.password} 프로퍼티) 검증만으로
 * JWT를 발급한다 — 이 프로젝트에 회원가입/역할 체계가 없으므로 이 이상의 인증 로직은
 * 과설계다.
 */
@Tag(name = "Auth", description = "관리자 토큰 발급 API")
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final String ADMIN_USERNAME = "admin";

    private final JwtTokenProvider jwtTokenProvider;
    private final String adminPassword;

    public AuthController(JwtTokenProvider jwtTokenProvider, @Value("${admin.password}") String adminPassword) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.adminPassword = adminPassword;
    }

    @Operation(summary = "관리자 토큰 발급",
            description = "고정된 관리자 계정(username/password)을 검증해 JWT를 발급한다. "
                    + "발급받은 토큰은 GET /api/audit-logs, PATCH /api/incidents/{id}/resolve 호출 시 "
                    + "Authorization: Bearer <accessToken> 헤더로 사용한다.")
    @PostMapping("/token")
    public TokenResponse issueToken(@Valid @RequestBody TokenRequest request) {
        if (!ADMIN_USERNAME.equals(request.username()) || !adminPassword.equals(request.password())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "잘못된 관리자 인증 정보입니다.");
        }
        String token = jwtTokenProvider.generateToken(ADMIN_USERNAME);
        return TokenResponse.of(token, jwtTokenProvider.getExpirationMs());
    }
}
