package com.example.akadion.config;

import com.example.akadion.entity.Rol;
import com.example.akadion.entity.StareCont;
import com.example.akadion.entity.User;
import com.example.akadion.repository.RolRepository;
import com.example.akadion.repository.StareContRepository;
import com.example.akadion.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Seeder pentru date demo, activat doar prin profilul "demo".
 * Rulează după DataSeeder pentru a adăuga ~10 cereri de înregistrare PENDING
 * cu profilele deja completate (nume/prenume/rol/facultate populate) și
 * ID-uri Keycloak fictive (UUID-uri random), permițând testarea panoului de admin.
 */
@Slf4j
@Component
@Profile("demo")
@RequiredArgsConstructor
public class DemoDataSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final RolRepository rolRepository;
    private final StareContRepository stareContRepository;

    @Override
    public void run(String... args) {
        log.info("Rulare DemoDataSeeder pentru populare cereri de test PENDING...");
        
        StareCont pending = stareContRepository.findByDenumire("PENDING")
                .orElseThrow(() -> new IllegalStateException("Starea PENDING lipsește la rularea demo"));
        
        Rol student = rolRepository.findByDenumire("STUDENT")
                .orElseThrow(() -> new IllegalStateException("Rolul STUDENT lipsește la rularea demo"));
        
        Rol profesor = rolRepository.findByDenumire("PROFESOR")
                .orElseThrow(() -> new IllegalStateException("Rolul PROFESOR lipsește la rularea demo"));

        // Adăugăm utilizatori demo cu ID-uri Keycloak generate random
        creeazaUserDemoDacaNuExista("Popescu", "Ana", "ana.popescu@student.test", "Facultatea de Informatică", student, pending);
        creeazaUserDemoDacaNuExista("Ionescu", "Mihai", "mihai.ionescu@profesor.test", "Facultatea de Matematică", profesor, pending);
        creeazaUserDemoDacaNuExista("Vasilescu", "Elena", "elena.vasilescu@student.test", "Facultatea de Informatică", student, pending);
        creeazaUserDemoDacaNuExista("Georgescu", "Dan", "dan.georgescu@profesor.test", "Facultatea de Fizică", profesor, pending);
        creeazaUserDemoDacaNuExista("Radu", "Laura", "laura.radu@student.test", "Facultatea de Informatică", student, pending);
        creeazaUserDemoDacaNuExista("Stoica", "Andrei", "andrei.stoica@student.test", "Facultatea de Matematică", student, pending);
        creeazaUserDemoDacaNuExista("Nistor", "Carmen", "carmen.nistor@profesor.test", "Facultatea de Chimie", profesor, pending);
        creeazaUserDemoDacaNuExista("Marin", "Vlad", "vlad.marin@student.test", "Facultatea de Fizică", student, pending);
        creeazaUserDemoDacaNuExista("Dinu", "Cristina", "cristina.dinu@student.test", "Facultatea de Biologie", student, pending);
        creeazaUserDemoDacaNuExista("Stan", "Gheorghe", "gheorghe.stan@profesor.test", "Facultatea de Geografie", profesor, pending);
        
        log.info("DemoDataSeeder finalizat cu succes.");
    }

    private void creeazaUserDemoDacaNuExista(String nume, String prenume, String mail, String facultate, Rol rol, StareCont stare) {
        if (userRepository.findByMail(mail).isEmpty()) {
            User user = new User();
            user.setNume(nume);
            user.setPrenume(prenume);
            user.setMail(mail);
            user.setFacultate(facultate);
            user.setRol(rol);
            user.setStareCont(stare);
            // Generăm un UUID fictiv Keycloak random pentru demo
            user.setIdKeycloak(UUID.randomUUID().toString());
            user.setNrRespingeri(0);
            
            userRepository.save(user);
            log.info("Creat user demo PENDING: {} {} cu idKeycloak fictiv", prenume, nume);
        }
    }
}
