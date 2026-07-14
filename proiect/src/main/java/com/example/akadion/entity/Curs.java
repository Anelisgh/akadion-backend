package com.example.akadion.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.LocalDate;

@Entity
@Table(name = "cursuri", indexes = {
        @Index(name = "idx_cursuri_profesor", columnList = "id_profesor")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class Curs extends BaseAuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_profesor", nullable = false)
    private User profesor;

    @Column(nullable = false, length = 150)
    private String denumire;

    @Column(length = 1000)
    private String descriere;

    @Column(name = "data_inceput")
    private LocalDate dataInceput;

    @Column(name = "data_sfarsit")
    private LocalDate dataSfarsit;

    @Column(nullable = false)
    @Builder.Default
    private Boolean activ = true;
}
