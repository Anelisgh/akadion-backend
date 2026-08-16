package com.example.akadion.curs.repository;

import com.example.akadion.curs.entity.Curs;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CursRepository extends JpaRepository<Curs, Long> {

    @EntityGraph(attributePaths = {"profesor"})
    List<Curs> findByProfesorId(Long profesorId);

    long countByActiv(boolean activ);

    // Ca findAll(), dar cu profesorul preîncărcat (evită N+1 la listarea tuturor cursurilor, ex. pentru admin).
    @Query("SELECT c FROM Curs c JOIN FETCH c.profesor")
    List<Curs> findAllWithProfesor();

    @Query("""
        SELECT c FROM Curs c
        JOIN FETCH c.profesor
        WHERE c.activ = true
          AND NOT EXISTS (
            SELECT uc FROM UserCurs uc
            WHERE uc.curs = c
              AND uc.student.id = :studentId
              AND uc.activ = true
          )
        """)
    List<Curs> findAvailableCoursesForStudent(@Param("studentId") Long studentId);
}
