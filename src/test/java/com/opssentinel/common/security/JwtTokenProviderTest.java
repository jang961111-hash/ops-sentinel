package com.opssentinel.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.security.SignatureException;
import org.junit.jupiter.api.Test;

/**
 * US-015 보강: US-014(JWT 최소 구현)에서 관리자 엔드포인트 보호에 쓰이는 토큰
 * 발급/검증 로직인데도 테스트가 전혀 없었다. Spring 컨텍스트 없이도 순수 로직만으로
 * 검증 가능해(생성자에 secret/expirationMs를 직접 주입) @SpringBootTest 없이 단위테스트로 둔다.
 */
class JwtTokenProviderTest {

    @Test
    void 발급한_토큰을_검증하면_subject가_그대로_나온다() {
        JwtTokenProvider provider = new JwtTokenProvider("test-secret-abcdefg", 60_000L);

        String token = provider.generateToken("admin");
        Claims claims = provider.validate(token);

        assertThat(claims.getSubject()).isEqualTo("admin");
    }

    @Test
    void 만료된_토큰을_검증하면_ExpiredJwtException이_발생한다() {
        // expirationMs를 음수로 주면 발급 즉시(issuedAt 기준 과거) 만료된 토큰이 만들어진다.
        JwtTokenProvider provider = new JwtTokenProvider("test-secret-abcdefg", -1_000L);

        String expiredToken = provider.generateToken("admin");

        assertThatThrownBy(() -> provider.validate(expiredToken))
                .isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    void 다른_시크릿으로_서명된_토큰은_검증에_실패한다() {
        JwtTokenProvider issuer = new JwtTokenProvider("secret-A-1234567", 60_000L);
        JwtTokenProvider verifier = new JwtTokenProvider("secret-B-1234567", 60_000L);

        String token = issuer.generateToken("admin");

        assertThatThrownBy(() -> verifier.validate(token))
                .isInstanceOf(SignatureException.class);
    }

    @Test
    void 형식이_깨진_토큰은_검증에_실패한다() {
        JwtTokenProvider provider = new JwtTokenProvider("test-secret-abcdefg", 60_000L);

        assertThatThrownBy(() -> provider.validate("this-is-not-a-jwt"))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void secret이_비어있으면_랜덤키로도_정상적으로_발급_검증된다() {
        // application.yml 주석대로 jwt.secret이 optional이라, 빈 값이어도 기동/동작해야 한다.
        JwtTokenProvider provider = new JwtTokenProvider("", 60_000L);

        String token = provider.generateToken("admin");
        Claims claims = provider.validate(token);

        assertThat(claims.getSubject()).isEqualTo("admin");
    }
}
