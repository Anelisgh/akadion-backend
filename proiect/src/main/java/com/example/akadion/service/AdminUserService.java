package com.example.akadion.service;

import com.example.akadion.dto.ActionResponseDto;
import com.example.akadion.dto.UserPendingDto;
import com.example.akadion.entity.StareCont;
import com.example.akadion.entity.User;
import com.example.akadion.exception.ForbiddenOperationException;
import com.example.akadion.exception.InvalidUserStateException;
import com.example.akadion.exception.UserNotFoundException;
import com.example.akadion.repository.StareContRepository;
import com.example.akadion.repository.UserCursRepository;
import com.example.akadion.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

// Acest serviciu conține toate regulile de afaceri (business logic) aplicate utilizatorilor de către administrator.
// Aici se face listarea cererilor, aprobarea, respingerea, dezactivarea și reactivarea conturilor.
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminUserService {

    private static final String ADMIN_ROLE = "ADMIN";
    private static final String PENDING_STATE = "PENDING";
    private static final String ACTIVE_STATE = "ACTIV";
    private static final String REJECTED_STATE = "RESPINS";
    private static final String INACTIVE_STATE = "INACTIV";

    private final UserRepository userRepository;
    private final StareContRepository stareContRepository;
    private final UserCursRepository userCursRepository;
    private final KeycloakAdminService keycloakAdminService; // Conexiunea cu Keycloak
    private final CursService cursService;

    @org.springframework.beans.factory.annotation.Autowired
    @org.springframework.context.annotation.Lazy
    private AdminUserService self;

    // 1. Listează utilizatorii din sistem.
    // Dacă parametrul stare este "ALL", returnează absolut toți utilizatorii din DB.
    // Altfel, filtrează utilizatorii după starea primită (ex: "ACTIV", "PENDING", "INACTIV", "RESPINS", "INCOMPLET").
    // @Transactional(readOnly = true) îi spune bazei de date că doar citim informații (ceea ce este mai rapid și sigur).
    @Transactional(readOnly = true)
    public List<UserPendingDto> listaUtilizatori(String stare) {
        List<User> list;
        if ("ALL".equalsIgnoreCase(stare)) {
            list = userRepository.findAll();
        } else {
            list = userRepository.findByStareCont_Denumire(stare.toUpperCase());
        }
        return list.stream()
                // Eliminăm conturile de ADMIN din listă (nu au ce căuta aici)
                .filter(user -> user.getRol() == null || !ADMIN_ROLE.equals(user.getRol().getDenumire()))
                // Transformăm fiecare utilizator din baza de date într-un obiect simplu de trimis (DTO)
                .map(user -> new UserPendingDto(
                        user.getId(),
                        user.getNume(),
                        user.getPrenume(),
                        user.getMail(),
                        user.getFacultate(),
                        // Dacă utilizatorul nu are rol, punem null
                        user.getRol() != null ? user.getRol().getDenumire() : null,
                        // Citim numărul de respingeri anterioare (dacă este null, punem implicit 0)
                        user.getNrRespingeri() != null ? user.getNrRespingeri() : 0,
                        // Adăugăm și starea curentă în DTO pentru ca React să știe în ce categorie se află utilizatorul
                        user.getStareCont() != null ? user.getStareCont().getDenumire() : "INCOMPLET",
                        user.getCreatedAt()
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public long countUtilizatori(String stare) {
        if ("ALL".equalsIgnoreCase(stare)) {
            return userRepository.countNonAdminAll();
        }
        return userRepository.countNonAdminByStareCont_Denumire(stare.toUpperCase());
    }

    // 2. Aprobă (acceptă) contul unui utilizator aflat în starea PENDING.
    @Transactional
    public ActionResponseDto approveUser(Long userId) {
        // Căutăm utilizatorul după ID-ul primit
        User user = getUser(userId);
        assertNotAdminTarget(user);

        // Regula A: Nu putem aproba decât utilizatori care au starea PENDING.
        if (!PENDING_STATE.equals(user.getStareCont().getDenumire())) {
            throw new InvalidUserStateException(
                    "Utilizatorul " + userId + " nu are starea PENDING (starea curentă: "
                    + user.getStareCont().getDenumire() + ")");
        }

        // Căutăm starea "ACTIV" în DB
        StareCont activ = stareContRepository.findByDenumire(ACTIVE_STATE)
                .orElseThrow(() -> new IllegalStateException("STARE_CONT 'ACTIV' lipsă din DB — verifică DataSeeder"));

        // Modificăm starea contului în ACTIV (rolul a fost deja setat în Complete-Profile).
        // Decizia KISS: Nu mai facem niciun apel către Keycloak pentru roluri; baza de date este sursa de adevăr!
        user.setStareCont(activ);
        userRepository.save(user);

        log.info("User acceptat în DB: userId={}, mail={}, rol={}", 
                userId, user.getMail(), user.getRol() != null ? user.getRol().getDenumire() : "FĂRĂ ROL");
        return new ActionResponseDto("Utilizatorul a fost aprobat și activat.");
    }

    // 3. Respinge contul unui utilizator aflat în starea PENDING.
    @Transactional
    public ActionResponseDto rejectUser(Long userId) {
        User user = getUser(userId);
        assertNotAdminTarget(user);

        // Regula B: Doar utilizatorii PENDING pot fi respinși.
        if (!PENDING_STATE.equals(user.getStareCont().getDenumire())) {
            throw new InvalidUserStateException(
                    "Utilizatorul " + userId + " nu are starea PENDING — nu poate fi respins.");
        }

        // Căutăm starea "RESPINS" în DB
        StareCont respins = stareContRepository.findByDenumire(REJECTED_STATE)
                .orElseThrow(() -> new IllegalStateException("STARE_CONT 'RESPINS' lipsă din DB — verifică DataSeeder"));

        // Modificăm starea contului în RESPINS și incrementăm numărul de respingeri cu +1.
        // Utilizatorul se va putea loga la loc în Keycloak dar filtrul îl va bloca și îl va trimite să își corecteze datele.
        user.setStareCont(respins);
        user.setNrRespingeri((user.getNrRespingeri() != null ? user.getNrRespingeri() : 0) + 1);
        userRepository.save(user);

        log.info("Cerere respinsă în DB: userId={}, mail={}, nrRespingeriCurent={}", 
                userId, user.getMail(), user.getNrRespingeri());
        return new ActionResponseDto("Utilizatorul a fost respins.");
    }

    // 4. Dezactivează definitiv un utilizator care în prezent este ACTIV.

    // 4. Dezactivează definitiv un utilizator care în prezent este ACTIV (orchestrator netranzacțional)
    public void dezactiveazaUser(Long userId) {
        User user = self.executeLocalDeactivation(userId);
        try {
            keycloakAdminService.dezactiveazaUser(user.getIdKeycloak());
        } catch (Exception e) {
            log.warn("Eroare la dezactivarea utilizatorului {} în Keycloak (continuăm local): {}", userId, e.getMessage());
        }
        log.info("User dezactivat local și în Keycloak (best-effort): userId={}, sub={}", userId, user.getIdKeycloak());
    }

    @Transactional
    public User executeLocalDeactivation(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        // Nu permitem dezactivarea unui administrator!
        assertNotAdminTarget(user);

        // Regula C: Doar utilizatorii în starea ACTIV pot fi dezactivați.
        if (!"ACTIV".equals(user.getStareCont().getDenumire())) {
            throw new InvalidUserStateException(
                    "Doar utilizatorii cu starea ACTIV pot fi dezactivați.");
        }

        // Căutăm starea "INACTIV" în DB
        StareCont inactiv = stareContRepository.findByDenumire(INACTIVE_STATE)
                .orElseThrow(() -> new IllegalStateException("STARE_CONT 'INACTIV' lipsă din DB — verifică DataSeeder"));
        
        // Modificăm starea locală a contului în INACTIV
        user.setStareCont(inactiv);
        User savedUser = userRepository.save(user);

        // Cascadă dezactivare profesor vs student
        if (savedUser.getRol() != null) {
            String rol = savedUser.getRol().getDenumire();
            if ("PROFESOR".equals(rol)) {
                cursService.dezactiveazaToateCursurileProfesorului(userId);
            } else if ("STUDENT".equals(rol)) {
                userCursRepository.dezactiveazaInrolariStudent(userId);
            }
        }

        return savedUser;
    }

    // 5. Reactivează (activează) un utilizator care fusese marcat anterior ca INACTIV (orchestrator netranzacțional)
    public void activeazaUser(Long userId) {
        User user = self.executeLocalReactivation(userId);
        try {
            keycloakAdminService.reactiveazaUser(user.getIdKeycloak());
        } catch (Exception e) {
            log.warn("Eroare la reactivarea utilizatorului {} în Keycloak (continuăm local): {}", userId, e.getMessage());
        }
        log.info("User reactivat local și în Keycloak (best-effort): userId={}, sub={}", userId, user.getIdKeycloak());
    }

    @Transactional
    public User executeLocalReactivation(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        // Regula D: Doar conturile marcate INACTIV pot fi reactivate.
        if (!INACTIVE_STATE.equals(user.getStareCont().getDenumire())) {
            throw new InvalidUserStateException(
                    "Doar utilizatorii cu starea INACTIV pot fi reactivați.");
        }

        // Căutăm starea "ACTIV" în DB
        StareCont activ = stareContRepository.findByDenumire(ACTIVE_STATE)
                .orElseThrow(() -> new IllegalStateException("STARE_CONT 'ACTIV' lipsă din DB — verifică DataSeeder"));
        
        // Redăm starea ACTIV contului local
        user.setStareCont(activ);
        User savedUser = userRepository.save(user);

        // Regulă de simetrie: Cursurile profesorului reactivat RĂMÂN inactive (nu se reactivează automat).
        if (savedUser.getRol() != null && "STUDENT".equals(savedUser.getRol().getDenumire())) {
            userCursRepository.reactiveazaInrolariStudent(userId);
        }
        
        return savedUser;
    }

    private User getUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));
    }

    private void assertNotAdminTarget(User user) {
        if (user.getRol() != null && ADMIN_ROLE.equals(user.getRol().getDenumire())) {
            throw new ForbiddenOperationException("Conturile ADMIN nu pot fi aprobate sau respinse din acest flux.");
        }
    }
}
