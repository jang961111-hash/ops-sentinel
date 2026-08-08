package com.opssentinel.resource.repository;

import com.opssentinel.resource.entity.Resource;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ResourceRepository extends JpaRepository<Resource, Long> {

    /**
     * Resource 행에 비관적 쓰기 락(SELECT ... FOR UPDATE)을 건다.
     * IncidentDetectionService가 동일 resourceId에 대한 "OPEN 사건 조회 → 없으면 생성"
     * 임계구간을 이 락으로 직렬화해 중복 Incident 생성을 막는다(US-004).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM Resource r WHERE r.id = :id")
    Optional<Resource> lockById(@Param("id") Long id);
}
