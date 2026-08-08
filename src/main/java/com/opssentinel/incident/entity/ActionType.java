package com.opssentinel.incident.entity;

/**
 * IncidentAgent가 자동 결정·기록하는 조치 종류.
 */
public enum ActionType {
    MONITOR,
    BACKUP,
    RESTART,
    ALERT,
    ESCALATE
}
