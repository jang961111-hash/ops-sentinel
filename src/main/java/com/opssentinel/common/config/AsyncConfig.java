package com.opssentinel.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 커밋 후 AI 요약({@code AiSummaryListener}) 전용 스레드풀.
 *
 * <p>큐 상한을 두고, 넘치면 제출이 거부된다(AbortPolicy). 리스너가 거부를 잡아 incidentId와
 * 함께 로그를 남기고 요약을 건너뛴다(사건에는 이미 폴백 문구가 저장돼 있다). OpenAI가 느려질 때
 * 요청 스레드나 DB 커넥션이 아니라 이 풀의 큐만 차오르게 하려는 것이다. 종료 시에는 진행 중인
 * 요약을 최대 10초 기다린다.
 */
@Configuration
public class AsyncConfig {

    public static final String AI_SUMMARY_EXECUTOR = "aiSummaryExecutor";

    @Bean(name = AI_SUMMARY_EXECUTOR)
    public ThreadPoolTaskExecutor aiSummaryExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("ai-summary-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        return executor;
    }
}
