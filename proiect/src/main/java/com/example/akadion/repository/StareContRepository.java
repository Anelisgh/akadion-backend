package com.example.akadion.repository;

import com.example.akadion.entity.StareCont;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface StareContRepository extends JpaRepository<StareCont, Long> {

    Optional<StareCont> findByDenumire(String denumire);
}
