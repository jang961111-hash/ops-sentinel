package com.opssentinel.audit.repository;

import com.opssentinel.audit.entity.AuditLog;
import com.opssentinel.audit.entity.ResultStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    /** actorType/resultStatus/targetType 모두 선택 항목 필터로 감사로그를 페이지 조회한다(GET /api/audit-logs). */
    @Query("SELECT a FROM AuditLog a WHERE "
            + "(:actorType IS NULL OR a.actorType = :actorType) "
            + "AND (:resultStatus IS NULL OR a.resultStatus = :resultStatus) "
            + "AND (:targetType IS NULL OR a.targetType = :targetType)")
    Page<AuditLog> search(
            @Param("actorType") String actorType,
            @Param("resultStatus") ResultStatus resultStatus,
            @Param("targetType") String targetType,
            Pageable pageable);
}
