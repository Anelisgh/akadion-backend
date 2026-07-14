package com.example.akadion.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "user_cursuri", uniqueConstraints = {
        @UniqueConstraint(name = "uk_user_cursuri_student_curs", columnNames = { "id_student", "id_curs" })
}, indexes = {
        @Index(name = "idx_user_cursuri_student", columnList = "id_student"),
        @Index(name = "idx_user_cursuri_curs", columnList = "id_curs")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class UserCurs extends BaseAuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_student", nullable = false)
    private User student;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_curs", nullable = false)
    private Curs curs;

    @Column(nullable = false)
    @Builder.Default
    private Boolean activ = true;
}
