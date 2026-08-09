package com.example.akadion.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "mesaje_chat")
@Getter @Setter
public class MesajChat extends BaseAuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "id_conversatie", nullable = false)
    private Conversatie conversatie;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RolMesaj rol;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String continut;

    @Column(name = "surse_folosite", columnDefinition = "TEXT")
    private String surseFolosite;

    @Column(name = "are_raspuns", nullable = false)
    private Boolean areRaspuns = false;
}
