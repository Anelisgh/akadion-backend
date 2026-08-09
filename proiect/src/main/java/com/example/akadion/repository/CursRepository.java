package com.example.akadion.repository;

import com.example.akadion.entity.Curs;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CursRepository extends JpaRepository<Curs, Long> {

    @EntityGraph(attributePaths = {"profesor"})
    List<Curs> findByProfesorId(Long profesorId);

    long countByActiv(Boolean activ);

    @org.springframework.data.jpa.repository.Query("""
        SELECT c FROM Curs c
        WHERE c.activ = true
          AND NOT EXISTS (
            SELECT uc FROM UserCurs uc
            WHERE uc.curs = c
              AND uc.student.id = :studentId
              AND uc.activ = true
          )
        """)
    List<Curs> findCursuriDisponibilePentruStudent(@org.springframework.data.repository.query.Param("studentId") Long studentId);
}
