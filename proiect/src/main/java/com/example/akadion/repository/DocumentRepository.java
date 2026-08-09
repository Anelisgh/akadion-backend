package com.example.akadion.repository;

import com.example.akadion.entity.Document;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DocumentRepository extends JpaRepository<Document, Long> {

    @EntityGraph(attributePaths = {"saptamana", "saptamana.curs"})
    List<Document> findBySaptamanaIdAndActivTrue(Long saptamanaId);

    List<Document> findAllBySaptamanaId(Long saptamanaId);

    @EntityGraph(attributePaths = {"saptamana", "saptamana.curs", "saptamana.curs.profesor"})
    Optional<Document> findWithSaptamanaAndCursAndProfesorById(Long id);
}
