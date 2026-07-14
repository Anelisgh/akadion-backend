package com.example.akadion.repository;

import com.example.akadion.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

// Această interfață este responsabilă cu toate interogările făcute pe tabela de Utilizatori (app_user).
// Extinde JpaRepository<User, Long>, însemnând că lucrează cu entitatea 'User' care are ID-ul de tip 'Long'.
public interface UserRepository extends JpaRepository<User, Long> {

    // 1. Caută utilizatorul în baza de date după adresa de e-mail (unică).
    // Rulează SQL-ul: "SELECT * FROM app_user WHERE mail = :mail"
    Optional<User> findByMail(String mail);

    // 2. Caută utilizatorul după UUID-ul unic pe care l-a primit de la Keycloak (idKeycloak).
    // Rulează SQL-ul: "SELECT * FROM app_user WHERE id_keycloak = :idKeycloak"
    Optional<User> findByIdKeycloak(String idKeycloak);

    // 3. Caută toți utilizatorii care au o anumită stare a contului (folosind denumirea stării din tabela asociată stare_cont).
    // Relația dintre User și StareCont este de tip Join. 
    // Spring Data JPA știe să facă automat JOIN între tabele pe baza numelui: StareCont (obiectul) + Denumire (câmpul din StareCont).
    // Rulează SQL-ul: "SELECT u.* FROM app_user u JOIN stari_cont s ON u.id_stare_cont = s.id WHERE s.denumire = :denumire"
    List<User> findByStareCont_Denumire(String denumire);
}
