package com.example.akadion.admin.repository;

import com.example.akadion.admin.entity.AuditLog;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    Slice<AuditLog> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
