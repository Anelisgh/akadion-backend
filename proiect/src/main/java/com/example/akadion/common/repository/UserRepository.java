package com.example.akadion.common.repository;

import com.example.akadion.common.entity.User;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    @EntityGraph(attributePaths = {"rol", "stareCont"})
    Optional<User> findByIdKeycloak(String idKeycloak);

    @EntityGraph(attributePaths = {"rol", "stareCont"})
    Optional<User> findByMail(String mail);

    @EntityGraph(attributePaths = {"rol", "stareCont"})
    List<User> findByStareCont_Denumire(String denumire);

    @Query("SELECT COUNT(u) FROM User u WHERE u.stareCont.denumire = :denumire AND (u.rol IS NULL OR u.rol.denumire <> :#{T(com.example.akadion.common.entity.NumeRol).ADMIN.name()})")
    long countNonAdminByStareCont_Denumire(@Param("denumire") String denumire);

    @Query("SELECT COUNT(u) FROM User u WHERE u.rol IS NULL OR u.rol.denumire <> :#{T(com.example.akadion.common.entity.NumeRol).ADMIN.name()}")
    long countNonAdminAll();

    @Override
    @EntityGraph(attributePaths = {"rol", "stareCont"})
    java.util.List<User> findAll();

    @EntityGraph(attributePaths = {"rol", "stareCont"})
    List<User> findByIdKeycloakIn(List<String> idKeycloakList);
}
