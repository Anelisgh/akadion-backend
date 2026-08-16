package com.example.akadion.config;

import com.example.akadion.common.entity.Rol;
import com.example.akadion.common.entity.StareCont;
import com.example.akadion.common.entity.User;
import com.example.akadion.common.repository.RolRepository;
import com.example.akadion.common.repository.StareContRepository;
import com.example.akadion.common.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.example.akadion.common.entity.NumeRol;
import com.example.akadion.common.entity.NumeStareCont;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.Arrays;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

    private final RolRepository rolRepository;
    private final StareContRepository stareContRepository;
    private final UserRepository userRepository;

    @Value("${app.seed.admin-email:admin@akadion.com}")
    private String adminEmail;

    @Value("${app.seed.admin-uuid:5186b9ef-a846-45b6-b640-6ab114fb8dfa}")
    private String adminUuid;

    @Override
    public void run(String... args) {
        // La pornire, apelăm metodele de populare date.
        seedRoluri();
        seedStariCont();
        seedDefaultAdmin();
    }

    private void seedRoluri() {
        if (rolRepository.count() == 0) {
            Arrays.stream(NumeRol.values()).forEach(rolEnum -> {
                Rol rol = new Rol();
                rol.setDenumire(rolEnum.name());
                rolRepository.save(rol);
            });
            log.info("S-au inserat {} roluri de bază în DB.", rolRepository.count());
        } else {
            log.info("Rolurile sunt deja în baza de date, se sare peste inserare.");
        }
    }

    private void seedStariCont() {
        if (stareContRepository.count() == 0) {
            Arrays.stream(NumeStareCont.values()).forEach(stareEnum -> {
                StareCont stareCont = new StareCont();
                stareCont.setDenumire(stareEnum.name());
                stareContRepository.save(stareCont);
            });
            log.info("S-au inserat {} stări de cont în DB.", stareContRepository.count());
        } else {
            log.info("Stările de cont sunt deja populate în DB, se sare peste.");
        }
    }

    private void seedDefaultAdmin() {
        if (userRepository.findByMail(adminEmail).isEmpty()) {
            Rol adminRol = rolRepository.findByDenumire(NumeRol.ADMIN.name()).orElse(null);
            StareCont activStare = stareContRepository.findByDenumire(NumeStareCont.ACTIV.name()).orElse(null);

            if (adminRol == null || activStare == null) {
                log.warn("Nu s-a putut insera admin-ul implicit: rolul ADMIN sau starea ACTIV nu au fost găsite în DB.");
                return;
            }

            User user = User.builder()
                    .idKeycloak(adminUuid)
                    .mail(adminEmail)
                    .nume("Admin")
                    .prenume("Principal")
                    .facultate("Administrare")
                    .rol(adminRol)
                    .stareCont(activStare)
                    .nrRespingeri(0)
                    .build();

            userRepository.save(user);
            log.info("Admin-ul implicit ({}) a fost creat automat cu profil complet și stare ACTIV.", adminEmail);
        } else {
            log.info("Admin-ul implicit ({}) există deja în DB.", adminEmail);
        }
    }
}
