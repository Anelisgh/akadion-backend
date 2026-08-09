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
import org.springframework.stereotype.Component;

import java.util.List;

// Acest fișier se ocupă de inserarea datelor de bază (obligatorii) în baza de date la prima pornire a aplicației.
// Folosim CommandLineRunner pentru a-i spune sistemului Spring: "Rulează acest cod automat imediat după ce ai pornit serverul."
@Slf4j
@Component
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

    // Injectăm depozitele (Repository-urile) pentru a putea salva date în tabelele corespunzătoare.
    private final RolRepository rolRepository;
    private final StareContRepository stareContRepository;
    private final UserRepository userRepository;

    @Override
    public void run(String... args) {
        // La pornire, apelăm metodele de populare date.
        seedRoluri();
        seedStariCont();
        seedDefaultAdmin();
    }

    // Această metodă adaugă rolurile fundamentale de utilizatori în baza de date.
    private void seedRoluri() {
        // Pasul 1: Verificăm dacă tabela de Roluri este complet goală (numărând rândurile: count() == 0).
        if (rolRepository.count() == 0) {
            // Pasul 2: Dacă e goală, luăm lista de roluri dorită: ADMIN, PROFESOR, STUDENT.
            List.of("ADMIN", "PROFESOR", "STUDENT").forEach(denumire -> {
                Rol rol = new Rol();
                rol.setDenumire(denumire); // Setăm numele rolului
                rolRepository.save(rol);   // Îl salvăm în baza de date (INSERT)
            });
            log.info("S-au inserat {} roluri de bază în DB.", rolRepository.count());
        } else {
            // Pasul 3: Dacă tabela are deja date, nu mai adăugăm nimic ca să nu le duplicăm.
            log.info("Rolurile sunt deja în baza de date, se sare peste inserare.");
        }
    }

    // Această metodă adaugă stările posibile în care se poate afla un cont.
    private void seedStariCont() {
        // Pasul 1: Verificăm dacă tabela de Stări Cont este goală.
        if (stareContRepository.count() == 0) {
            // Pasul 2: Dacă e goală, inserăm stările necesare fluxului nostru de aprobări.
            List.of("INCOMPLET", "PENDING", "ACTIV", "INACTIV", "RESPINS").forEach(denumire -> {
                StareCont stareCont = new StareCont();
                stareCont.setDenumire(denumire); // Setăm denumirea stării
                stareContRepository.save(stareCont); // O salvăm în DB (INSERT)
            });
            log.info("S-au inserat {} stări de cont în DB.", stareContRepository.count());
        } else {
            // Pasul 3: Dacă existau deja stări salvate anterior, se sare peste pas.
            log.info("Stările de cont sunt deja populate în DB, se sare peste.");
        }
    }

    // Această metodă adaugă contul de administrator implicit cu profil complet (stare ACTIV)
    // astfel încât colegii care rulează aplicația local să nu fie redirecționați la completare profil.
    private void seedDefaultAdmin() {
        String adminEmail = "admin@akadion.com";
        if (userRepository.findByMail(adminEmail).isEmpty()) {
            Rol adminRol = rolRepository.findByDenumire("ADMIN").orElse(null);
            StareCont activStare = stareContRepository.findByDenumire("ACTIV").orElse(null);

            if (adminRol == null || activStare == null) {
                log.warn("Nu s-a putut insera admin-ul implicit: rolul ADMIN sau starea ACTIV nu au fost găsite în DB.");
                return;
            }

            User user = User.builder()
                    .idKeycloak("5186b9ef-a846-45b6-b640-6ab114fb8dfa") // (potrivit cu cel din keycloak-import)
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
