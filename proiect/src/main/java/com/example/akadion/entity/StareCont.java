package com.example.akadion.entity;

import jakarta.persistence.*;
import lombok.*;

// Valorile permise în DB: 'PENDING', 'ACTIV', 'INACTIV', 'RESPINS'
@Entity
@Table(name = "stari_cont")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class StareCont {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 20)
    private String denumire;
}
