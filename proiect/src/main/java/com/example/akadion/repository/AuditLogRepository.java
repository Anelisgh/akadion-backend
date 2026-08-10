package com.example.akadion.repository;

import com.example.akadion.entity.AuditLog;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    Slice<AuditLog> findAllByOrderByCreatLaDesc(Pageable pageable);

}
