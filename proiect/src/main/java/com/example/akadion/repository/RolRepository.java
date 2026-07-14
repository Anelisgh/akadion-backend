package com.example.akadion.repository;

import com.example.akadion.entity.Rol;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

// Această interfață este o "punte" între codul nostru Java și tabela de Roluri din baza de date.
// Extinzând JpaRepository, Spring ne generează automat toate operațiunile de bază:
// de salvare (save), de citire a tuturor rândurilor (findAll), de căutare după ID (findById), etc.
// Nu este nevoie să scriem SQL pentru acestea, Spring le face în fundal.
public interface RolRepository extends JpaRepository<Rol, Long> {

    // Aceasta este o interogare generată automat (Query Derivation). 
    // Doar scriind numele metodei "findByDenumire", Spring înțelege automat că vrem să execute SQL-ul:
    // "SELECT * FROM roluri WHERE denumire = :denumire".
    // Optional<Rol> înseamnă că rezultatul poate fi prezent (cu datele rolului) sau gol (dacă rolul nu există în DB).
    Optional<Rol> findByDenumire(String denumire);
}
