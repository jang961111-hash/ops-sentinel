package com.opssentinel.common.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger UI에 JWT Bearer 토큰 입력 UI를 노출한다(US-014, 필수는 아니지만 curl 없이도
 * 관리자 엔드포인트를 바로 테스트할 수 있도록 추가). 실제 인증 강제는 여전히
 * {@link com.opssentinel.common.security.JwtAuthenticationFilter}가 담당하고, 여기서는
 * "Authorize" 버튼과 스키마 정의만 등록한다.
 */
@Configuration
public class OpenApiConfig {

    private static final String BEARER_AUTH_SCHEME = "bearerAuth";

    @Bean
    public OpenAPI opsSentinelOpenApi() {
        return new OpenAPI()
                .components(new Components()
                        .addSecuritySchemes(BEARER_AUTH_SCHEME, new SecurityScheme()
                                .name(BEARER_AUTH_SCHEME)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));
    }
}
