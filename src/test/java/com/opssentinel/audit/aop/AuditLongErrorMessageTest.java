package com.opssentinel.audit.aop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.opssentinel.audit.Auditable;
import com.opssentinel.audit.entity.AuditLog;
import com.opssentinel.audit.entity.ResultStatus;
import com.opssentinel.audit.repository.AuditLogRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * 예외 메시지가 AuditLog.errorMessage 컬럼 길이(2000)보다 길어도 FAIL 감사는 저장돼야 한다.
 * 잘라 넣지 않으면 INSERT가 실패하고 recordSafely가 삼켜 FAIL 감사가 조용히 사라진다.
 */
@SpringBootTest
class AuditLongErrorMessageTest {

    static final String LONG_MESSAGE = "x".repeat(2500);

    static class LongFailureTarget {
        @Auditable(action = "LONG_ERROR_TEST", targetType = "Test")
        public void fail() {
            throw new IllegalStateException(LONG_MESSAGE);
        }
    }

    @TestConfiguration
    static class Config {
        @Bean
        LongFailureTarget longFailureTarget() {
            return new LongFailureTarget();
        }
    }

    @Autowired
    private LongFailureTarget target;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Test
    void 예외_메시지가_2000자를_넘어도_FAIL_감사가_잘린_메시지로_저장된다() {
        assertThatThrownBy(target::fail).isInstanceOf(IllegalStateException.class).hasMessage(LONG_MESSAGE);

        List<AuditLog> fails = auditLogRepository.findAll().stream()
                .filter(a -> "LONG_ERROR_TEST".equals(a.getAction()))
                .filter(a -> a.getResultStatus() == ResultStatus.FAIL)
                .toList();
        assertThat(fails).hasSize(1);
        assertThat(fails.get(0).getErrorMessage()).hasSize(2000).startsWith("xxx");
    }
}
