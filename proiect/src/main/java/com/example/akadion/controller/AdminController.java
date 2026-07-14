package com.example.akadion.controller;

import com.example.akadion.dto.UserPendingDto;
import com.example.akadion.service.AdminUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// Acest controller pune la dispoziție funcții (endpoint-uri) pe care doar administratorul le poate apela.
// Folosim RestController pentru a-i spune lui Spring că această clasă returnează date în format JSON (nu pagini HTML întregi).
@RestController
@RequestMapping("/api/admin") // Toate link-urile din acest controller vor începe cu "/api/admin"
@RequiredArgsConstructor // Lombok generează automat constructorul cu argumentele necesare pentru injectare
@PreAuthorize("hasRole('ADMIN')")  // Regulă de fier: Nimeni nu poate apela nimic de aici dacă nu are rolul de 'ADMIN' în baza de date
public class AdminController {

    private final AdminUserService adminUserService;

    // 1. Listează cererile de înregistrare trimise de utilizatori care sunt în starea PENDING.
    // Apel: GET /api/admin/users?stare=PENDING
    @GetMapping("/users")
    public List<UserPendingDto> listaUseri(
            @RequestParam(defaultValue = "PENDING") String stare) {
        // În acest plan listăm doar utilizatorii care așteaptă aprobarea (PENDING)
        return adminUserService.listaCereriPending();
    }

    // 2. Aprobă un utilizator aflat în starea PENDING.
    // Calea URL conține ID-ul utilizatorului pe care îl aprobăm (ex: /api/admin/users/5/accept).
    @PostMapping("/users/{id}/accept")
    @ResponseStatus(HttpStatus.OK) // Răspundem cu succes (200 OK) dacă totul a mers bine
    public void acceptaUser(@PathVariable Long id) { // @PathVariable preia valoarea '{id}' direct din link
        adminUserService.acceptaUser(id);
    }

    // 3. Respinge cererea unui utilizator PENDING.
    // Schimbă starea acestuia în RESPINS în baza de date, fără să șteargă contul din Keycloak.
    @PostMapping("/users/{id}/reject")
    @ResponseStatus(HttpStatus.OK)
    public void respingeUser(@PathVariable Long id) {
        adminUserService.respingeUser(id);
    }

    // 4. Dezactivează un utilizator activ (opțiune administrativă pentru a bloca temporar sau permanent accesul).
    // Schimbă starea locală în INACTIV și blochează logarea utilizatorului direct în Keycloak.
    @PostMapping("/users/{id}/deactivate")
    @ResponseStatus(HttpStatus.OK)
    public void dezactiveazaUser(@PathVariable Long id) {
        adminUserService.dezactiveazaUser(id);
    }

    // 5. Reactivează (activează) un cont care fusese dezactivat anterior (INACTIV).
    // Readuce starea în ACTIV și repornește contul în Keycloak.
    @PostMapping("/users/{id}/activate")
    @ResponseStatus(HttpStatus.OK)
    public void activeazaUser(@PathVariable Long id) {
        adminUserService.activeazaUser(id);
    }
}
