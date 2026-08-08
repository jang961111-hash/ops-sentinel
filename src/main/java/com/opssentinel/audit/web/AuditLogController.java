package com.opssentinel.audit.web;

import com.opssentinel.audit.entity.ResultStatus;
import com.opssentinel.audit.service.AuditLogService;
import com.opssentinel.audit.web.dto.AuditLogResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Audit Log", description = "관리자용 감사로그 조회 API")
@RestController
@RequestMapping("/api/audit-logs")
@RequiredArgsConstructor
public class AuditLogController {

    private final AuditLogService auditLogService;

    @Operation(summary = "감사로그 목록 조회", description = "actorType/resultStatus/targetType으로 필터링 가능하며, 페이지 단위로 반환한다.")
    @GetMapping
    public Page<AuditLogResponse> list(
            @Parameter(description = "감사 주체 필터") @RequestParam(required = false) String actorType,
            @Parameter(description = "처리 결과 필터") @RequestParam(required = false) ResultStatus resultStatus,
            @Parameter(description = "감사 대상 타입 필터") @RequestParam(required = false) String targetType,
            Pageable pageable) {
        return auditLogService.list(actorType, resultStatus, targetType, pageable).map(AuditLogResponse::from);
    }
}
