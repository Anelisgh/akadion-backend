package com.example.akadion.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "saptamani", indexes = {
        @Index(name = "idx_saptamani_curs", columnList = "id_curs")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class Saptamana extends BaseAuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_curs", nullable = false)
    private Curs curs;

    @Column(name = "nr_saptamana", nullable = false)
    private Integer nrSaptamana;

    @Column(length = 500)
    private String descriere;
}
