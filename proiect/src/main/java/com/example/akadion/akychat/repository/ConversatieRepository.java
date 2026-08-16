package com.example.akadion.akychat.repository;

import com.example.akadion.akychat.entity.Conversatie;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ConversatieRepository extends JpaRepository<Conversatie, Long> {
    Slice<Conversatie> findByUserIdAndCursIdAndActivTrueOrderByUpdatedAtDesc(Long userId, Long cursId, Pageable pageable);
    Slice<Conversatie> findByUserIdAndActivTrueOrderByUpdatedAtDesc(Long userId, Pageable pageable);
}
