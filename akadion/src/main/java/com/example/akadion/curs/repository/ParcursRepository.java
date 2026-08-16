package com.example.akadion.curs.repository;

import com.example.akadion.curs.entity.Parcurs;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ParcursRepository extends JpaRepository<Parcurs, Long> {

    List<Parcurs> findBySaptamanaId(Long saptamanaId);

    Optional<Parcurs> findByUserCursIdAndSaptamanaId(Long userCursId, Long saptamanaId);

    @Query("""
        SELECT p.saptamana.id FROM Parcurs p
        WHERE p.userCurs.student.id = :studentId
          AND p.userCurs.curs.id = :cursId
          AND p.userCurs.activ = true
        """)
    List<Long> findCompletedWeekIds(
        @Param("studentId") Long studentId,
        @Param("cursId") Long cursId
    );

    // Numărul de săptămâni bifate per curs pentru un student, într-o singură interogare
    // (evită N+1 la listarea cursurilor în care e înrolat studentul).
    @Query("""
        SELECT p.userCurs.curs.id, COUNT(p) FROM Parcurs p
        WHERE p.userCurs.student.id = :studentId
          AND p.userCurs.curs.id IN :cursIds
          AND p.userCurs.activ = true
        GROUP BY p.userCurs.curs.id
        """)
    List<Object[]> countCompletedWeeksByCursIdIn(
        @Param("studentId") Long studentId,
        @Param("cursIds") List<Long> cursIds
    );
}
