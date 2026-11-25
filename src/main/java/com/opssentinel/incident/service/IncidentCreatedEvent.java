package com.opssentinel.incident.service;

import com.opssentinel.incident.entity.ActionType;
import java.util.List;

/**
 * 새 Incident와 그 조치가 기록됐다는 사실. 사건 생성 트랜잭션이 커밋된 뒤에만
 * {@link AiSummaryListener}가 받아 AI 요약을 채운다.
 */
public record IncidentCreatedEvent(Long incidentId, List<ActionType> actionTypes) {
}
