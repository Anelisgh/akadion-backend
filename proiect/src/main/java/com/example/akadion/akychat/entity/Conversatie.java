package com.example.akadion.akychat.entity;

import com.example.akadion.common.entity.BaseAuditableEntity;
import com.example.akadion.curs.entity.Curs;
import com.example.akadion.common.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "conversatii")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
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
    @Builder.Default
    private boolean activ = true;
}
