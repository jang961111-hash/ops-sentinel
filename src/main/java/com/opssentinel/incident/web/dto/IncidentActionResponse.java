package com.opssentinel.incident.web.dto;

import com.opssentinel.incident.entity.ActionType;
import com.opssentinel.incident.entity.IncidentAction;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

/**
 * IncidentAction 엔티티를 외부에 노출하지 않기 위한 응답 DTO.
 */
@Schema(description = "사건 조치이력 응답")
public record IncidentActionResponse(
        ActionType actionType,
        String executedBy,
        String result,
        LocalDateTime executedAt
) {

    public static IncidentActionResponse from(IncidentAction action) {
        return new IncidentActionResponse(
                action.getActionType(),
                action.getExecutedBy(),
                action.getResult(),
                action.getExecutedAt()
        );
    }
}
