package com.opssentinel.incident.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.opssentinel.incident.entity.ActionType;
import com.opssentinel.incident.entity.Incident;
import com.opssentinel.incident.entity.IncidentSeverity;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * US-015 보강: US-013(OpenAI 연동) 도입 이후 폴백 로직(PRD 3-3: "API 실패해도 기본
 * 템플릿 문장으로 대체, 앱 절대 안 죽음")에 대한 테스트가 전혀 없었다. 실제 네트워크
 * 호출 경로(apiKey가 있는 경우)는 외부 의존이라 이 스위트 범위 밖으로 두고, apiKey가
 * 비어있을 때 즉시 폴백 문장을 반환하는 핵심 안전장치만 단위테스트로 고정한다.
 */
class AiSummaryServiceTest {

    @Test
    void apiKey가_비어있으면_네트워크_호출_없이_기본_템플릿_문장을_반환한다() {
        AiSummaryService service = new AiSummaryService("", "gpt-4o-mini", 3000);

        Incident incident = Incident.builder()
                .resourceId(1L)
                .severity(IncidentSeverity.CRITICAL)
                .ruleTriggered("ERROR_RATE_EXCEEDED")
                .build();

        String summary = service.summarize(incident, List.of(ActionType.ESCALATE, ActionType.ALERT));

        assertThat(summary).isEqualTo("CRITICAL 등급의 ERROR_RATE_EXCEEDED 이상이 감지되어 자동 조치가 실행되었습니다.");
    }

    @Test
    void apiKey가_공백만_있어도_기본_템플릿_문장을_반환한다() {
        AiSummaryService service = new AiSummaryService("   ", "gpt-4o-mini", 3000);

        Incident incident = Incident.builder()
                .resourceId(2L)
                .severity(IncidentSeverity.MEDIUM)
                .ruleTriggered("CPU_EXCEEDED")
                .build();

        String summary = service.summarize(incident, List.of(ActionType.MONITOR, ActionType.ALERT));

        assertThat(summary).isEqualTo("MEDIUM 등급의 CPU_EXCEEDED 이상이 감지되어 자동 조치가 실행되었습니다.");
    }
}
