package com.opssentinel.incident.service;

import com.opssentinel.common.config.AsyncConfig;
import com.opssentinel.incident.repository.IncidentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 사건 생성 트랜잭션이 커밋된 뒤, 전용 스레드풀에서 AI 요약을 만들어 폴백 문구를 덮어쓴다.
 *
 * <p><b>왜 커밋 후 비동기인가:</b> 예전에는 요약 호출이 Resource 비관적 락을 쥔 트랜잭션
 * 안에 있었다. 그래서 OpenAI 응답 시간(연결·읽기 타임아웃 각 3초, 최악 약 6초)만큼 같은
 * 리소스의 다른 요청이 모두 락 대기로 묶였다(2026-09 재측정: 지연 3초에서 20건 전부
 * 3.05초, 340 → 6.6 rps). 이제 락 구간에는 DB 작업만 남고, 요약은 락·커넥션 없이 만든다.
 * 저장만 짧은 UPDATE 한 번이다.
 *
 * <p>요약 실패는 사건 생성에 영향이 없다. 사건은 이미 폴백 문구로 커밋돼 있고,
 * {@link AiSummaryService#summarize}도 예외 대신 폴백 문구를 돌려준다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiSummaryListener {

    private final AiSummaryService aiSummaryService;
    private final IncidentRepository incidentRepository;

    @Async(AsyncConfig.AI_SUMMARY_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onIncidentCreated(IncidentCreatedEvent event) {
        if (!aiSummaryService.isEnabled()) {
            return; // 키가 없으면 커밋된 폴백 문구가 최종 요약이다
        }
        incidentRepository.findById(event.incidentId()).ifPresent(incident -> {
            String summary = aiSummaryService.summarize(incident, event.actionTypes());
            incidentRepository.updateAiSummary(incident.getId(), summary);
        });
    }
}
