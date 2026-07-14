package com.example.akadion.entity;

import jakarta.persistence.*;
import lombok.*;

// Valorile permise în DB: 'ADMIN', 'PROFESOR', 'STUDENT'
// Trebuie să coincidă cu rolurile din realm-ul Keycloak
@Entity
@Table(name = "roluri")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Rol {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String denumire;
}
