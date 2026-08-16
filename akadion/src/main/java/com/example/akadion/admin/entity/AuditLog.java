package com.example.akadion.admin.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;

@Entity
@Table(name = "audit_log")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "nume_tabel", nullable = false, length = 50)
    private NumeTabelAudit numeTabel;

    @Column(name = "id_inregistrare", nullable = false)
    private Long idInregistrare;

    @Enumerated(EnumType.STRING)
    @Column(name = "operatie", nullable = false, length = 30)
    private OperatieAudit operatie;

    @Column(name = "utilizator", length = 36)
    private String utilizator;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "valori_vechi", columnDefinition = "jsonb")
    private Map<String, Object> valoriVechi;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "valori_noi", columnDefinition = "jsonb")
    private Map<String, Object> valoriNoi;

    @Column(name = "creat_la", nullable = false, updatable = false)
    @Builder.Default
    private OffsetDateTime createdAt = OffsetDateTime.now(ZoneOffset.UTC);
}
