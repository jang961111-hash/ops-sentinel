package com.opssentinel.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Date;
import javax.crypto.SecretKey;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * JWT 발급/검증 유틸(US-014).
 *
 * <p><b>왜 시크릿이 비어있어도 기동이 되는가:</b> {@code jwt.secret}(환경변수 {@code JWT_SECRET})은
 * optional 프로퍼티다. 값이 없으면 매 기동마다 랜덤 키를 생성해서라도 앱이 정상 동작하게
 * 한다 — 데모/과제 스케일에서 시크릿 미설정이 기동 실패로 이어지면 안 되기 때문이다. 대신
 * 재기동하면 이전에 발급한 토큰은 전부 무효화된다(서명 검증 실패)는 트레이드오프를 감수한다.
 *
 * <p><b>왜 문자열 시크릿을 그대로 서명 키로 쓰지 않는가:</b> HS256은 최소 256비트(32바이트) 키를
 * 요구하는데, 사람이 넣는 평문 시크릿은 이보다 짧을 수 있어 그대로 쓰면 {@code WeakKeyException}으로
 * 기동이 죽을 수 있다. SHA-256으로 해시해 항상 32바이트 키를 만들어 이 문제를 원천 차단한다
 * (같은 시크릿이면 항상 같은 키가 나오므로, 값이 주어졌을 때는 재기동해도 토큰이 유효하다).
 */
@Slf4j
@Component
public class JwtTokenProvider {

    private final SecretKey key;
    private final long expirationMs;

    public JwtTokenProvider(
            @Value("${jwt.secret:}") String secret,
            @Value("${jwt.expiration-ms:3600000}") long expirationMs) {
        this.expirationMs = expirationMs;
        if (secret == null || secret.isBlank()) {
            log.warn("jwt.secret(JWT_SECRET)이 설정되지 않아 랜덤 키를 생성합니다 — "
                    + "데모 용도로만 사용하고, 앱을 재기동하면 이전에 발급한 토큰은 모두 무효화됩니다.");
            this.key = Jwts.SIG.HS256.key().build();
        } else {
            this.key = Keys.hmacShaKeyFor(sha256(secret));
        }
    }

    /** subject(사용자명)를 담은 JWT를 발급한다. */
    public String generateToken(String subject) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expirationMs);
        return Jwts.builder()
                .subject(subject)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(key)
                .compact();
    }

    public long getExpirationMs() {
        return expirationMs;
    }

    /** 토큰 서명·만료를 검증한다. 유효하지 않으면 {@link JwtException}(또는 하위타입)을 던진다. */
    public Claims validate(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private static byte[] sha256(String secret) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(secret.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            // SHA-256은 모든 JDK 구현체가 표준으로 제공하므로 실제로는 발생하지 않는다.
            throw new IllegalStateException("SHA-256 알고리즘을 사용할 수 없습니다.", e);
        }
    }
}
