package com.opssentinel.audit.service;

import com.opssentinel.audit.entity.AuditLog;
import com.opssentinel.audit.entity.ResultStatus;
import com.opssentinel.audit.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

/**
 * 감사로그 조회 서비스(관리자용). 기록 자체는 AuditLogAspect/AuditLogRecorder가 담당하고,
 * 이 서비스는 조회만 다룬다.
 */
@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;

    public Page<AuditLog> list(String actorType, ResultStatus resultStatus, String targetType, Pageable pageable) {
        return auditLogRepository.search(actorType, resultStatus, targetType, pageable);
    }
}
