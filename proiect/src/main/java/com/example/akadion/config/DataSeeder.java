package com.example.akadion.config;

import com.example.akadion.entity.Rol;
import com.example.akadion.entity.StareCont;
import com.example.akadion.repository.RolRepository;
import com.example.akadion.repository.StareContRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

    private final RolRepository rolRepository;
    private final StareContRepository stareContRepository;

    @Override
    public void run(String... args) {
        seedRoluri();
        seedStariCont();
    }

    private void seedRoluri() {
        if (rolRepository.count() == 0) {
            // Valorile corespund cu rolurile din aplicatie
            List.of("ADMIN", "PROFESOR", "STUDENT").forEach(denumire -> {
                Rol rol = new Rol();
                rol.setDenumire(denumire);
                rolRepository.save(rol);
            });
            log.info("Seeded {} roluri.", rolRepository.count());
        } else {
            log.info("Roluri deja populate, se sare seed-ul.");
        }
    }

    private void seedStariCont() {
        if (stareContRepository.count() == 0) {
            // Valorile permise conform noului plan: INCOMPLET, PENDING, ACTIV, INACTIV, RESPINS
            List.of("INCOMPLET", "PENDING", "ACTIV", "INACTIV", "RESPINS").forEach(denumire -> {
                StareCont stareCont = new StareCont();
                stareCont.setDenumire(denumire);
                stareContRepository.save(stareCont);
            });
            log.info("Seeded {} stari_cont.", stareContRepository.count());
        } else {
            log.info("Stari_cont deja populate, se sare seed-ul.");
        }
    }
}
