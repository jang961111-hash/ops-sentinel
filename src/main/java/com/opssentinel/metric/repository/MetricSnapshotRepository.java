package com.opssentinel.metric.repository;

import com.opssentinel.metric.entity.MetricSnapshot;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MetricSnapshotRepository extends JpaRepository<MetricSnapshot, Long> {

    Optional<MetricSnapshot> findFirstByResourceIdOrderByCapturedAtDesc(Long resourceId);

    /**
     * from/to는 둘 다 선택 항목이라 BETWEEN 대신 null 허용 조건으로 처리한다.
     * (LocalDateTime.MIN/MAX 같은 sentinel 값은 DB TIMESTAMP 표현 범위를 벗어나
     * 드라이버에 따라 조용히 빈 결과를 반환할 수 있어 피한다.)
     */
    @Query("SELECT m FROM MetricSnapshot m "
            + "WHERE m.resourceId = :resourceId "
            + "AND (:from IS NULL OR m.capturedAt >= :from) "
            + "AND (:to IS NULL OR m.capturedAt <= :to) "
            + "ORDER BY m.capturedAt ASC")
    List<MetricSnapshot> findHistory(
            @Param("resourceId") Long resourceId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);
}
