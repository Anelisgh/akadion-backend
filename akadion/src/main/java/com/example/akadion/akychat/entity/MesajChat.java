package com.example.akadion.akychat.entity;

import com.example.akadion.common.entity.BaseAuditableEntity;
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
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "mesaje_chat")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
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
    @Builder.Default
    private boolean areRaspuns = false;
}
