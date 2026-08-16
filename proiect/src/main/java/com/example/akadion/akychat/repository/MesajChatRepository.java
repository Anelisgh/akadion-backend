package com.example.akadion.akychat.repository;

import com.example.akadion.akychat.entity.MesajChat;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MesajChatRepository extends JpaRepository<MesajChat, Long> {
    List<MesajChat> findTop10ByConversatieIdOrderByCreatedAtDesc(Long conversatieId);
    List<MesajChat> findByConversatieIdAndIdLessThanOrderByIdDesc(Long conversatieId, Long id, Pageable pageable);
}
