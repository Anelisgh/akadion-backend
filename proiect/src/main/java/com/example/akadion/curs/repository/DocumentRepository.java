package com.example.akadion.curs.repository;

import com.example.akadion.curs.entity.Document;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DocumentRepository extends JpaRepository<Document, Long> {

    @EntityGraph(attributePaths = {"saptamana", "saptamana.curs"})
    List<Document> findBySaptamanaIdAndActivTrue(Long saptamanaId);

    List<Document> findBySaptamanaIdInAndActivTrue(List<Long> saptamanaIds);

    List<Document> findBySaptamanaId(Long saptamanaId);

    @EntityGraph(attributePaths = {"saptamana", "saptamana.curs", "saptamana.curs.profesor"})
    Optional<Document> findWithSaptamanaAndCursAndProfesorById(Long id);

    boolean existsBySaptamanaIdAndHashContinutAndActivTrue(Long saptamanaId, String hashContinut);

    boolean existsBySaptamanaIdAndHashContinutAndIdNotAndActivTrue(Long saptamanaId, String hashContinut, Long id);
}
