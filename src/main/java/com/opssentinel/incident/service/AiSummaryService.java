package com.opssentinel.incident.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.opssentinel.incident.entity.ActionType;
import com.opssentinel.incident.entity.Incident;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * OpenAI Chat Completions API로 Incident의 "왜 이 조치를 취했는지" 자연어 판단근거
 * 요약(aiSummary)을 생성한다(US-013).
 *
 * <p><b>어디서 호출되는가:</b> 사건 생성 트랜잭션이 커밋된 뒤 {@link AiSummaryListener}가
 * 전용 스레드풀에서 호출한다. 예전에는 비관적 락(Resource SELECT ... FOR UPDATE)을 쥔
 * 트랜잭션 안에서 호출돼, 아래 타임아웃만큼 같은 리소스의 요청 전체가 멈췄다.
 *
 * <p><b>왜 실패를 절대 밖으로 던지지 않는가:</b> LLM 호출은 외부 네트워크 의존이라 언제든
 * 실패·지연할 수 있는데, 이게 Incident 생성/조치기록이라는 핵심 흐름을 막으면 안 된다(PRD 3-3: "API 실패해도
 * 기본 템플릿 문장으로 대체, 앱 절대 안 죽음"). 그래서 모든 예외를 이 클래스 안에서
 * 흡수하고 절대 밖으로 던지지 않으며, {@code OPENAI_API_KEY}가 비어있으면 네트워크
 * 호출 자체를 시도하지 않고 즉시 폴백한다(연동 자체가 optional).
 *
 * <p><b>타임아웃:</b> 연결·읽기에 각각 {@code openai.timeout-ms}(기본 3초)가 걸려 최악은
 * 약 6초다. 이제 락 밖이라 요청 지연에는 영향이 없고, 요약 스레드풀 점유 시간만 늘린다.
 * 재시도는 하지 않는다 — 실패하면 이미 저장된 폴백 문장이 그대로 남아 감사 목적(판단근거
 * 기록)은 충분히 달성된다.
 */
@Slf4j
@Service
public class AiSummaryService {

    /**
     * 기본값은 실제 OpenAI 엔드포인트다. 테스트·측정에서는 로컬 가짜 서버(지연 주입 등)로,
     * 운영에서는 프록시·호환 공급자로 바꿀 수 있도록 {@code openai.base-url}로 덮어쓸 수 있게 했다.
     */
    static final String DEFAULT_BASE_URL = "https://api.openai.com/v1";
    private static final String CHAT_COMPLETIONS_PATH = "/chat/completions";

    private final String apiKey;
    private final String model;
    private final RestClient restClient;

    @Autowired
    public AiSummaryService(
            @Value("${openai.api-key:}") String apiKey,
            @Value("${openai.model:gpt-4o-mini}") String model,
            @Value("${openai.timeout-ms:3000}") int timeoutMs,
            @Value("${openai.base-url:" + DEFAULT_BASE_URL + "}") String baseUrl) {
        this.apiKey = apiKey;
        this.model = model;

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(timeoutMs);
        requestFactory.setReadTimeout(timeoutMs);

        this.restClient = RestClient.builder()
                .baseUrl(baseUrl + CHAT_COMPLETIONS_PATH)
                .requestFactory(requestFactory)
                .build();
    }

    /** 기존 호출부(단위 테스트 등) 호환용: base-url은 기본값(실제 OpenAI)을 쓴다. */
    public AiSummaryService(String apiKey, String model, int timeoutMs) {
        this(apiKey, model, timeoutMs, DEFAULT_BASE_URL);
    }

    /**
     * incident/actionTypes를 근거로 1~2문장 한국어 요약을 생성한다. 실패해도 예외를 던지지
     * 않고 기본 템플릿 문장을 반환한다.
     */
    public String summarize(Incident incident, List<ActionType> actionTypes) {
        if (!isEnabled()) {
            return fallback(incident);
        }
        try {
            JsonNode response = restClient.post()
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .body(buildRequest(incident, actionTypes))
                    .retrieve()
                    .body(JsonNode.class);
            String content = extractContent(response);
            return (content == null || content.isBlank()) ? fallback(incident) : content.trim();
        } catch (Exception e) {
            log.warn("OpenAI 요약 생성 실패(incidentId={}) - 기본 템플릿으로 대체합니다: {}",
                    incident.getId(), e.getMessage());
            return fallback(incident);
        }
    }

    /** API 키가 있어 실제 호출을 시도할지 여부. */
    public boolean isEnabled() {
        return apiKey != null && !apiKey.isBlank();
    }

    /** 네트워크 없이 만드는 기본 템플릿 문장. 사건 생성 시 먼저 저장되고, AI 요약이 실패하면 그대로 남는다. */
    public String fallback(Incident incident) {
        return "%s 등급의 %s 이상이 감지되어 자동 조치가 실행되었습니다."
                .formatted(incident.getSeverity(), incident.getRuleTriggered());
    }

    private ChatCompletionRequest buildRequest(Incident incident, List<ActionType> actionTypes) {
        String prompt = ("인프라 이상탐지 시스템이 아래 사건에 대해 자동 조치를 결정했다. "
                + "운영자가 바로 이해할 수 있도록 왜 이렇게 판단했는지 한국어 1~2문장으로만 요약해라. "
                + "다른 설명이나 인사말은 붙이지 마라. "
                + "리소스ID: %d, 심각도: %s, 트리거된 규칙: %s, 결정된 조치: %s")
                .formatted(incident.getResourceId(), incident.getSeverity(), incident.getRuleTriggered(), actionTypes);
        return new ChatCompletionRequest(model, List.of(new ChatMessage("user", prompt)), 150, 0.3);
    }

    private String extractContent(JsonNode response) {
        if (response == null) {
            return null;
        }
        JsonNode content = response.at("/choices/0/message/content");
        return content.isMissingNode() ? null : content.asText(null);
    }

    private record ChatCompletionRequest(
            String model,
            List<ChatMessage> messages,
            @JsonProperty("max_tokens") int maxTokens,
            double temperature) {
    }

    private record ChatMessage(String role, String content) {
    }
}
