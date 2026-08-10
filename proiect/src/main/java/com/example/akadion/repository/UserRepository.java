package com.example.akadion.repository;

import com.example.akadion.entity.User;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

// Această interfață este responsabilă cu toate interogările făcute pe tabela de Utilizatori (app_user).
// Extinde JpaRepository<User, Long>, însemnând că lucrează cu entitatea 'User' care are ID-ul de tip 'Long'.
public interface UserRepository extends JpaRepository<User, Long> {

    // 2. Caută utilizatorul după UUID-ul unic pe care l-a primit de la Keycloak (idKeycloak).
    // Rulează SQL-ul: "SELECT * FROM app_user WHERE id_keycloak = :idKeycloak"
    @EntityGraph(attributePaths = {"rol", "stareCont"})
    Optional<User> findByIdKeycloak(String idKeycloak);

    // 1. Caută utilizatorul în baza de date după adresa de e-mail (unică).
    // Rulează SQL-ul: "SELECT * FROM app_user WHERE mail = :mail"
    @EntityGraph(attributePaths = {"rol", "stareCont"})
    Optional<User> findByMail(String mail);

    // 3. Caută toți utilizatorii care au o anumită stare a contului (folosind denumirea stării din tabela asociată stare_cont).
    // Relația dintre User și StareCont este de tip Join. 
    // Spring Data JPA știe să facă automat JOIN între tabele pe baza numelui: StareCont (obiectul) + Denumire (câmpul din StareCont).
    // Rulează SQL-ul: "SELECT u.* FROM app_user u JOIN stari_cont s ON u.id_stare_cont = s.id WHERE s.denumire = :denumire"
    @EntityGraph(attributePaths = {"rol", "stareCont"})
    List<User> findByStareCont_Denumire(String denumire);

    long countByStareCont_Denumire(String denumire);

    @Query("SELECT COUNT(u) FROM User u WHERE u.stareCont.denumire = :denumire AND (u.rol IS NULL OR u.rol.denumire <> 'ADMIN')")
    long countNonAdminByStareCont_Denumire(@Param("denumire") String denumire);

    @Query("SELECT COUNT(u) FROM User u WHERE u.rol IS NULL OR u.rol.denumire <> 'ADMIN'")
    long countNonAdminAll();

    @Override
    @EntityGraph(attributePaths = {"rol", "stareCont"})
    java.util.List<User> findAll();

    // 4. Caută utilizatorii în funcție de o listă de ID-uri Keycloak (folosit pentru maparea în masă la audit-log).
    @EntityGraph(attributePaths = {"rol", "stareCont"})
    List<User> findByIdKeycloakIn(List<String> idKeycloakList);
}
