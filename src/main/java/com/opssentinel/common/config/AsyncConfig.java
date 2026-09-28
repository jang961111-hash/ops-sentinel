package com.opssentinel.common.config;

import java.util.concurrent.Executor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 커밋 후 AI 요약({@code AiSummaryListener}) 전용 스레드풀.
 *
 * <p>큐 상한을 두고, 넘치면 요약을 건너뛴다(사건에는 이미 폴백 문구가 저장돼 있다).
 * OpenAI가 느려질 때 요청 스레드나 DB 커넥션이 아니라 이 풀의 큐만 차오르게 하려는 것이다.
 */
@Slf4j
@EnableAsync
@Configuration
public class AsyncConfig {

    public static final String AI_SUMMARY_EXECUTOR = "aiSummaryExecutor";

    @Bean(name = AI_SUMMARY_EXECUTOR)
    public Executor aiSummaryExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("ai-summary-");
        executor.setRejectedExecutionHandler((task, pool) ->
                log.warn("AI 요약 큐가 가득 차 요약을 건너뜁니다(사건에는 폴백 문구가 남습니다)"));
        executor.initialize();
        return executor;
    }
}
