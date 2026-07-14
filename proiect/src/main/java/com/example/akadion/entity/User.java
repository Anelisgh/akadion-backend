package com.example.akadion.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

// ⚠️ Tabela se numește 'app_user' — 'USER' este cuvânt rezervat în PostgreSQL
@Entity
@Table(name = "app_user", uniqueConstraints = {
        @UniqueConstraint(name = "uk_app_user_id_keycloak", columnNames = { "id_keycloak" }),
        @UniqueConstraint(name = "uk_app_user_mail", columnNames = { "mail" })
}, indexes = {
        @Index(name = "idx_app_user_rol", columnList = "id_rol"),
        @Index(name = "idx_app_user_stare_cont", columnList = "id_stare_cont"),
        @Index(name = "idx_app_user_mail", columnList = "mail"),
        @Index(name = "idx_app_user_id_keycloak", columnList = "id_keycloak")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class User extends BaseAuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Acum NOT NULL + UNIQUE de la register (înregistrat mai întâi în Keycloak)
    @Column(name = "id_keycloak", nullable = false, length = 36)
    private String idKeycloak;

    // Mail unic în DB, propagat automat din Keycloak la primul login/register
    @Column(name = "mail", nullable = false, length = 100)
    private String mail;

    // Nullable în prima fază (stare INCOMPLET)
    @Column(length = 100, nullable = true)
    private String nume;

    // Nullable în prima fază (stare INCOMPLET)
    @Column(length = 100, nullable = true)
    private String prenume;

    @Column(length = 100, nullable = true)
    private String facultate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_stare_cont", nullable = false)
    private StareCont stareCont;

    // Nullable în prima fază (stare INCOMPLET)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_rol", nullable = true)
    private Rol rol;

    // Contorizăm numărul de respingeri pentru admin context
    @Column(name = "nr_respingeri", nullable = false)
    @Builder.Default
    private Integer nrRespingeri = 0;
}
