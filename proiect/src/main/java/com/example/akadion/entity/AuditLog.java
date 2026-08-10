package com.example.akadion.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.Map;

@Entity
@Table(name = "audit_log")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "nume_tabel", nullable = false, length = 50)
    private String numeTabel;

    @Column(name = "id_inregistrare", nullable = false)
    private Long idInregistrare;

    @Column(name = "operatie", nullable = false, length = 30)
    private String operatie;

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
    private OffsetDateTime creatLa = OffsetDateTime.now();

}
