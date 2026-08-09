package com.example.akadion.repository;

import com.example.akadion.entity.UserCurs;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UserCursRepository extends JpaRepository<UserCurs, Long> {

    List<UserCurs> findByCursId(Long cursId);

    // Returnează înscrierile active (userCurs.activ = true) pentru un curs,
    // filtrând și după starea contului studentului (trebuie să fie ACTIV).
    // JOIN-ul pe stareCont evită N+1 și mută filtrarea la nivelul bazei de date.
    @Query("""
            SELECT uc FROM UserCurs uc
            JOIN FETCH uc.student s
            JOIN s.stareCont sc
            WHERE uc.curs.id = :cursId
              AND uc.activ = true
              AND sc.denumire = 'ACTIV'
            """)
    List<UserCurs> findStudentiActivi(@Param("cursId") Long cursId);

    java.util.Optional<UserCurs> findByStudentIdAndCursId(Long studentId, Long cursId);

    boolean existsByStudentIdAndCursId(Long studentId, Long cursId);

    boolean existsByStudentIdAndCursIdAndActivTrue(Long studentId, Long cursId);

    @Query("""
            SELECT uc FROM UserCurs uc
            JOIN FETCH uc.curs c
            JOIN FETCH c.profesor p
            WHERE uc.student.id = :studentId
              AND uc.activ = true
            """)
    List<UserCurs> findCursuriInrolatePentruStudent(@Param("studentId") Long studentId);

    long countByCursIdAndActivTrue(Long cursId);

    @Modifying
    @Query("UPDATE UserCurs uc SET uc.activ = false WHERE uc.student.id = :studentId")
    void dezactiveazaInrolariStudent(@Param("studentId") Long studentId);

    @Modifying
    @Query("UPDATE UserCurs uc SET uc.activ = true WHERE uc.student.id = :studentId")
    void reactiveazaInrolariStudent(@Param("studentId") Long studentId);
}
