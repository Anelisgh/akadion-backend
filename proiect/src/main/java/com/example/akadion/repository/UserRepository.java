package com.example.akadion.repository;

import com.example.akadion.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    // Caută utilizatorul după adresă de e-mail (unică în DB)
    Optional<User> findByMail(String mail);

    Optional<User> findByIdKeycloak(String idKeycloak);

    // Folosit de AdminUserService pentru listarea cererilor filtrate după starea contului
    List<User> findByStareCont_Denumire(String denumire);
}
