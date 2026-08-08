package com.opssentinel.incident.service;

import com.opssentinel.audit.Auditable;
import com.opssentinel.incident.entity.ActionType;
import com.opssentinel.incident.entity.Incident;
import com.opssentinel.incident.entity.IncidentAction;
import com.opssentinel.incident.entity.IncidentSeverity;
import com.opssentinel.incident.entity.IncidentStatus;
import com.opssentinel.incident.repository.IncidentActionRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * severity(+ruleTriggered)에 따라 조치(IncidentAction)를 자동 결정하고 기록한다(US-005).
 *
 * <p>매핑 규칙(왜 이렇게 정했는지):
 * <ul>
 *   <li>LOW: MONITOR만 — 아직 사람이 볼 필요 없는 경미한 신호라 계속 지켜보는 것으로 충분</li>
 *   <li>MEDIUM: MONITOR + ALERT — 지켜보되 담당자에게는 조기에 알려서 인지시킴(API
 *       명세서 응답 예시에 등장하는 MONITOR+ALERT 조합을 이 등급에 대응시켰다)</li>
 *   <li>HIGH: ALERT + (RESTART 또는 BACKUP) — 담당자 알림은 필수이고, 추가로
 *       ruleTriggered가 CPU_EXCEEDED/MEM_EXCEEDED/QUEUE_DEPTH_EXCEEDED처럼 "프로세스
 *       재시작으로 회복될 가능성이 높은" 리소스 압박형 문제면 RESTART를, ERROR_RATE_EXCEEDED처럼
 *       재시작이 능사가 아닌(배포·외부 의존성 문제일 수 있는) 경우는 재시작으로 상태를
 *       더 꼬이게 만들기 전에 현재 데이터부터 지키는 BACKUP을 택한다</li>
 *   <li>CRITICAL: ESCALATE + ALERT — 시스템 자동 조치만으로 해결을 장담할 수 없는
 *       단계라 사람에게 바로 에스컬레이션하고 동시에 알림도 남긴다</li>
 * </ul>
 *
 * <p>조치를 기록하기 시작한 시점부터 Incident.status를 DETECTED → ANALYZING으로 전이한다
 * — "막 감지만 된 상태"와 "조치를 판단·기록 중인 상태"를 구분하기 위함이다(API 명세서
 * 응답 예시도 조치 이력이 있는 사건의 status를 ANALYZING으로 보여준다).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IncidentActionService {

    private static final String RESULT_OK = "OK";
    private static final List<String> RESTART_ELIGIBLE_RULES =
            List.of("CPU_EXCEEDED", "MEM_EXCEEDED", "QUEUE_DEPTH_EXCEEDED");

    private final IncidentActionRepository incidentActionRepository;

    /**
     * 새로 생성된 Incident에 대해 severity/ruleTriggered 기반으로 조치를 결정·기록하고,
     * status를 ANALYZING으로 전이한다. 호출자(IncidentDetectionService)의 열려있는
     * 트랜잭션 안에서 실행돼야 한다 — 조치 기록과 status 전이가 Incident 생성과 한
     * 트랜잭션으로 묶여야 원자성이 보장된다.
     */
    @Auditable(action = "INCIDENT_ACTION_DECIDE", targetType = "Incident")
    public void decideAndRecord(Incident incident) {
        List<ActionType> actionTypes = decideActionTypes(incident.getSeverity(), incident.getRuleTriggered());
        for (ActionType actionType : actionTypes) {
            IncidentAction action = IncidentAction.builder()
                    .incident(incident)
                    .actionType(actionType)
                    .result(RESULT_OK)
                    .build();
            incident.getActions().add(action);
            incidentActionRepository.save(action);
        }
        incident.setStatus(IncidentStatus.ANALYZING);
        log.info("Incident 조치 기록: id={}, severity={}, rule={}, actions={}",
                incident.getId(), incident.getSeverity(), incident.getRuleTriggered(), actionTypes);
    }

    private List<ActionType> decideActionTypes(IncidentSeverity severity, String ruleTriggered) {
        return switch (severity) {
            case LOW -> List.of(ActionType.MONITOR);
            case MEDIUM -> List.of(ActionType.MONITOR, ActionType.ALERT);
            case HIGH -> List.of(ActionType.ALERT, restartOrBackup(ruleTriggered));
            case CRITICAL -> List.of(ActionType.ESCALATE, ActionType.ALERT);
        };
    }

    private ActionType restartOrBackup(String ruleTriggered) {
        return RESTART_ELIGIBLE_RULES.contains(ruleTriggered) ? ActionType.RESTART : ActionType.BACKUP;
    }
}
