package com.example.akadion.curs.repository;

import com.example.akadion.curs.entity.Saptamana;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

    // Numărul de săptămâni per curs, într-o singură interogare (evită N+1 la listarea mai multor cursuri).
    @Query("SELECT s.curs.id, COUNT(s) FROM Saptamana s WHERE s.curs.id IN :cursIds GROUP BY s.curs.id")
    List<Object[]> countByCursIdIn(@Param("cursIds") List<Long> cursIds);
}
