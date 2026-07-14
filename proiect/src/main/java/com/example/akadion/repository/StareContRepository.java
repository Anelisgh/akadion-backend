package com.example.akadion.repository;

import com.example.akadion.entity.StareCont;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

// Această interfață se ocupă de comunicarea cu tabela de Stări Cont (stari_cont) din baza de date.
// Ca și la roluri, extindem JpaRepository pentru a ne folosi de metodele predefinite de scriere/citire SQL.
public interface StareContRepository extends JpaRepository<StareCont, Long> {

    // Metodă generată automat de Spring Data JPA. Execută în fundal SQL-ul:
    // "SELECT * FROM stari_cont WHERE denumire = :denumire" pentru a găsi o stare (ex: 'PENDING', 'ACTIV').
    Optional<StareCont> findByDenumire(String denumire);
}
