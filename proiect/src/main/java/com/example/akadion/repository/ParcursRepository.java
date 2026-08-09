package com.example.akadion.repository;

import com.example.akadion.entity.Parcurs;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ParcursRepository extends JpaRepository<Parcurs, Long> {

    List<Parcurs> findBySaptamanaId(Long saptamanaId);

    java.util.Optional<Parcurs> findByUserCursIdAndSaptamanaId(Long userCursId, Long saptamanaId);

    @org.springframework.data.jpa.repository.Query("""
        SELECT p.saptamana.id FROM Parcurs p
        WHERE p.userCurs.student.id = :studentId
          AND p.userCurs.curs.id = :cursId
          AND p.userCurs.activ = true
        """)
    List<Long> findCompletedSaptamaniIds(
        @org.springframework.data.repository.query.Param("studentId") Long studentId, 
        @org.springframework.data.repository.query.Param("cursId") Long cursId
    );

    @org.springframework.data.jpa.repository.Query("""
        SELECT COUNT(p) FROM Parcurs p 
        WHERE p.userCurs.student.id = :studentId 
          AND p.userCurs.curs.id = :cursId 
          AND p.userCurs.activ = true
        """)
    long countCompletedSaptamani(
        @org.springframework.data.repository.query.Param("studentId") Long studentId, 
        @org.springframework.data.repository.query.Param("cursId") Long cursId
    );
}
