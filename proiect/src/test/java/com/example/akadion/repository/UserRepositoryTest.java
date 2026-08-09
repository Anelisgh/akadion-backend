package com.example.akadion.repository;

import com.example.akadion.entity.Rol;
import com.example.akadion.entity.StareCont;
import com.example.akadion.entity.User;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.PersistenceUnitUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Test
    void findByIdKeycloakLoadsRoleAndAccountStateInSameQuery() {
        Rol rol = new Rol();
        rol.setDenumire("TEST_ROLE_USER_REPOSITORY");
        entityManager.persist(rol);

        StareCont stareCont = new StareCont();
        stareCont.setDenumire("TEST_STATE_REPO");
        entityManager.persist(stareCont);

        entityManager.flush();
        entityManager.createNativeQuery("""
                INSERT INTO app_user (id_keycloak, mail, nume, prenume, id_stare_cont, id_rol, nr_respingeri)
                VALUES (:idKeycloak, :mail, :nume, :prenume, :idStareCont, :idRol, :nrRespingeri)
                """)
                .setParameter("idKeycloak", "sub-admin")
                .setParameter("mail", "admin@akadion.test")
                .setParameter("nume", "Admin")
                .setParameter("prenume", "Test")
                .setParameter("idStareCont", stareCont.getId())
                .setParameter("idRol", rol.getId())
                .setParameter("nrRespingeri", 0)
                .executeUpdate();
        entityManager.clear();

        User foundUser = userRepository.findByIdKeycloak("sub-admin").orElseThrow();

        PersistenceUnitUtil persistenceUnitUtil = entityManagerFactory.getPersistenceUnitUtil();
        assertThat(persistenceUnitUtil.isLoaded(foundUser, "rol")).isTrue();
        assertThat(persistenceUnitUtil.isLoaded(foundUser, "stareCont")).isTrue();
        assertThat(foundUser.getRol().getDenumire()).isEqualTo("TEST_ROLE_USER_REPOSITORY");
        assertThat(foundUser.getStareCont().getDenumire()).isEqualTo("TEST_STATE_REPO");
    }
}
