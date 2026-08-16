package com.example.akadion.common.repository;

import com.example.akadion.common.entity.StareCont;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface StareContRepository extends JpaRepository<StareCont, Long> {
    Optional<StareCont> findByDenumire(String denumire);
}
