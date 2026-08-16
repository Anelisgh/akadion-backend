package com.example.akadion.quiz.repository;

import com.example.akadion.quiz.entity.IncercareQuiz;
import com.example.akadion.quiz.entity.IncercareQuizStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface IncercareQuizRepository extends JpaRepository<IncercareQuiz, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM IncercareQuiz i WHERE i.id = :id")
    Optional<IncercareQuiz> findByIdForUpdate(@Param("id") Long id);

    @EntityGraph(attributePaths = {"curs", "document"})
    Page<IncercareQuiz> findByStudentIdAndStatusOrderByCreatedAtDesc(Long studentId, IncercareQuizStatus status, Pageable pageable);

    @EntityGraph(attributePaths = {"curs", "document"})
    Page<IncercareQuiz> findByStudentIdAndStatusAndCursIdOrderByCreatedAtDesc(Long studentId, IncercareQuizStatus status, Long cursId, Pageable pageable);

    @EntityGraph(attributePaths = {"student"})
    Page<IncercareQuiz> findByCursIdAndStatusOrderByCreatedAtDesc(Long cursId, IncercareQuizStatus status, Pageable pageable);
}
