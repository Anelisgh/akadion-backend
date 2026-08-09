package com.example.akadion.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "conversatii")
@Getter @Setter
public class Conversatie extends BaseAuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "id_user", nullable = false)
    private User user;

    @ManyToOne(optional = false)
    @JoinColumn(name = "id_curs", nullable = false)
    private Curs curs;

    @Column(length = 150)
    private String titlu;

    @Column(nullable = false)
    private Boolean activ = true;
}
