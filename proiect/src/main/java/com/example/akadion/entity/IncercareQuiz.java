package com.example.akadion.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "incercari_quiz", indexes = {
        @Index(name = "idx_incercari_quiz_student_status", columnList = "id_student, status, created_at DESC"),
        @Index(name = "idx_incercari_quiz_curs_status", columnList = "id_curs, status, created_at DESC")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class IncercareQuiz extends BaseAuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_student", nullable = false)
    private User student;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_curs", nullable = false)
    private Curs curs;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_document")
    private Document document;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatusIncercareQuiz status;

    @Column(name = "nr_intrebari", nullable = false)
    private Integer nrIntrebari;

    @Column
    private Integer scor;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "detalii_json", columnDefinition = "jsonb", nullable = false)
    private String detaliiJson;
}
