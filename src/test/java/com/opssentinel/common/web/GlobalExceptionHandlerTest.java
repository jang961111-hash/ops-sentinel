package com.opssentinel.common.web;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.opssentinel.common.exception.ConflictException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * US-007 검증: {@link GlobalExceptionHandler}가 API 명세서 "7. 에러 코드" 표대로
 * 400/404/409/500을 매핑하고, 응답 바디가 {@code {timestamp, status, error, message, path}}
 * 형식을 지키는지 확인한다. 실제 컨트롤러에 의존하지 않도록 standalone MockMvc로 검증한다.
 */
class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ThrowingTestController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void IllegalArgumentException은_400으로_매핑된다() throws Exception {
        mockMvc.perform(get("/test/illegal-argument"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.timestamp", notNullValue()))
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.error", is("Bad Request")))
                .andExpect(jsonPath("$.message", is("잘못된 파라미터")))
                .andExpect(jsonPath("$.path", is("/test/illegal-argument")));
    }

    @Test
    void 경로변수_타입불일치는_400으로_매핑된다() throws Exception {
        mockMvc.perform(get("/test/type-mismatch/{id}", "not-a-number"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.path", is("/test/type-mismatch/not-a-number")));
    }

    @Test
    void ResponseStatusException_404는_그대로_매핑된다() throws Exception {
        mockMvc.perform(get("/test/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status", is(404)))
                .andExpect(jsonPath("$.error", is("Not Found")))
                .andExpect(jsonPath("$.message", is("리소스를 찾을 수 없습니다")))
                .andExpect(jsonPath("$.path", is("/test/not-found")));
    }

    @Test
    void ConflictException은_409로_매핑된다() throws Exception {
        mockMvc.perform(get("/test/conflict"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status", is(409)))
                .andExpect(jsonPath("$.error", is("Conflict")))
                .andExpect(jsonPath("$.message", is("동시성 충돌")))
                .andExpect(jsonPath("$.path", is("/test/conflict")));
    }

    @Test
    void 처리되지_않은_예외는_500으로_매핑된다() throws Exception {
        mockMvc.perform(get("/test/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status", is(500)))
                .andExpect(jsonPath("$.error", is("Internal Server Error")))
                .andExpect(jsonPath("$.message", is("서버 내부 오류가 발생했습니다.")))
                .andExpect(jsonPath("$.path", is("/test/boom")));
    }

    @RestController
    static class ThrowingTestController {

        @GetMapping("/test/illegal-argument")
        public void illegalArgument() {
            throw new IllegalArgumentException("잘못된 파라미터");
        }

        @GetMapping("/test/type-mismatch/{id}")
        public void typeMismatch(@PathVariable Long id) {
            // MockMvc가 경로변수 바인딩 단계에서 예외를 던지므로 본문은 호출되지 않는다.
        }

        @GetMapping("/test/not-found")
        public void notFound() {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "리소스를 찾을 수 없습니다");
        }

        @GetMapping("/test/conflict")
        public void conflict() {
            throw new ConflictException("동시성 충돌");
        }

        @GetMapping("/test/boom")
        public void boom() {
            throw new RuntimeException("boom");
        }
    }
}
