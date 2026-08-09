package com.example.akadion.repository;

import com.example.akadion.entity.Saptamana;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SaptamanaRepository extends JpaRepository<Saptamana, Long> {

    List<Saptamana> findByCursIdOrderByNrSaptamana(Long cursId);

    Optional<Saptamana> findTopByCursIdOrderByNrSaptamanaDesc(Long cursId);

    @EntityGraph(attributePaths = {"curs", "curs.profesor"})
    Optional<Saptamana> findWithCursAndProfesorById(Long id);

    long countByCursId(Long cursId);
}
