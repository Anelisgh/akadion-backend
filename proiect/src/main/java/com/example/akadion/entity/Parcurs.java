package com.example.akadion.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "parcursuri", indexes = {
        @Index(name = "idx_parcursuri_user_curs", columnList = "id_user_curs"),
        @Index(name = "idx_parcursuri_saptamana", columnList = "id_saptamana")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class Parcurs extends BaseAuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_user_curs", nullable = false)
    private UserCurs userCurs;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_saptamana", nullable = false)
    private Saptamana saptamana;
}
