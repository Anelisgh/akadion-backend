package com.example.akadion.repository;

import com.example.akadion.entity.IncercareQuiz;
import com.example.akadion.entity.StatusIncercareQuiz;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface IncercareQuizRepository extends JpaRepository<IncercareQuiz, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM IncercareQuiz i WHERE i.id = :id")
    Optional<IncercareQuiz> findByIdForUpdate(@Param("id") Long id);

    Page<IncercareQuiz> findByStudentIdAndStatusOrderByCreatedAtDesc(Long studentId, StatusIncercareQuiz status, Pageable pageable);

    Page<IncercareQuiz> findByStudentIdAndStatusAndCursIdOrderByCreatedAtDesc(Long studentId, StatusIncercareQuiz status, Long cursId, Pageable pageable);

    Page<IncercareQuiz> findByCursIdAndStatusOrderByCreatedAtDesc(Long cursId, StatusIncercareQuiz status, Pageable pageable);
}
