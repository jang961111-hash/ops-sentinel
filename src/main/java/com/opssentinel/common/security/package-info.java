/**
 * 보안(Security) 모듈.
 * JWT 최소 구현(발급/검증/필터)으로 관리자 전용 엔드포인트만 보호한다(US-014).
 * Spring Security 풀스택 대신 jjwt + 커스텀 {@link jakarta.servlet.Filter}를 쓴 이유는
 * {@link com.opssentinel.common.security.JwtAuthenticationFilter}의 클래스 주석 참고.
 */
package com.opssentinel.common.security;
