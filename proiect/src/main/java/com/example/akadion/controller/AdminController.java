package com.example.akadion.controller;

import com.example.akadion.dto.ActionResponseDto;
import com.example.akadion.dto.CursResponseDto;
import com.example.akadion.dto.DashboardStatsDto;
import com.example.akadion.dto.DocumentResponseDto;
import com.example.akadion.dto.ProfesorDetaliiResponseDto;
import com.example.akadion.dto.SaptamanaResponseDto;
import com.example.akadion.dto.StudentCursDto;
import com.example.akadion.dto.UserPendingDto;
import com.example.akadion.service.AdminUserService;
import com.example.akadion.service.CursService;
import com.example.akadion.service.DocumentService;
import com.example.akadion.service.SaptamanaService;
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
    private final CursService cursService;
    private final SaptamanaService saptamanaService;
    private final DocumentService documentService;

    // 1. Listează utilizatorii din sistem în funcție de starea lor.
    // Apel implicit (doar cei în așteptare): GET /api/admin/users?stare=PENDING
    // Apel pentru absolut toți utilizatorii: GET /api/admin/users?stare=ALL
    // Apel pentru cei activi: GET /api/admin/users?stare=ACTIV
    @GetMapping("/users")
    public List<UserPendingDto> listaUseri(
            @RequestParam(defaultValue = "PENDING") String stare) {
        return adminUserService.listaUtilizatori(stare);
    }

    // 2. Aprobă un utilizator aflat în starea PENDING.
    // Calea URL conține ID-ul utilizatorului pe care îl aprobăm (ex: /api/admin/users/5/accept).
    @PatchMapping("/users/{id}/approve")
    @ResponseStatus(HttpStatus.OK) // Răspundem cu succes (200 OK) dacă totul a mers bine
    public ActionResponseDto approveUser(@PathVariable Long id) { // @PathVariable preia valoarea '{id}' direct din link
        return adminUserService.approveUser(id);
    }

    // 3. Respinge cererea unui utilizator PENDING.
    // Schimbă starea acestuia în RESPINS în baza de date, fără să șteargă contul din Keycloak.

    // 3. Respinge cererea unui utilizator PENDING.
    // Schimbă starea acestuia în RESPINS în baza de date, fără să șteargă contul din Keycloak.
    @PatchMapping("/users/{id}/reject")
    @ResponseStatus(HttpStatus.OK)
    public ActionResponseDto rejectUser(@PathVariable Long id) {
        return adminUserService.rejectUser(id);
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

    // 6. Listează toate cursurile din sistem (active și inactive, ale tuturor profesorilor).
    @GetMapping("/cursuri")
    public List<CursResponseDto> listaToateCursurile() {
        return cursService.listaToateCursurile();
    }

    @GetMapping("/stats")
    public DashboardStatsDto getStats() {
        long cursuriActive = cursService.countCursuri(true);
        long cursuriInactive = cursService.countCursuri(false);
        long utilizatoriActivi = adminUserService.countUtilizatori("ACTIV");
        long utilizatoriPending = adminUserService.countUtilizatori("PENDING");
        
        return new DashboardStatsDto(cursuriActive, cursuriInactive, utilizatoriActivi, utilizatoriPending);
    }

    @GetMapping("/cursuri/{id}")
    public CursResponseDto getCurs(@PathVariable Long id) {
        return cursService.getCursById(id, null, "ADMIN");
    }

    @GetMapping("/cursuri/{id}/saptamani")
    public List<SaptamanaResponseDto> listaSaptamani(@PathVariable Long id) {
        return saptamanaService.listaSaptamani(id, null, "ADMIN");
    }

    @GetMapping("/saptamani/{saptamanaId}/documente")
    public List<DocumentResponseDto> listaDocumente(@PathVariable Long saptamanaId) {
        return documentService.listaDocumente(saptamanaId, null, "ADMIN");
    }

    @GetMapping("/cursuri/{id}/studenti")
    public List<StudentCursDto> listaStudentiInscrisi(@PathVariable Long id) {
        return cursService.listaStudentiActivi(id, null, "ADMIN");
    }

    @GetMapping("/cursuri/{cursId}/profesor")
    public ProfesorDetaliiResponseDto detaliiProfesorCurs(@PathVariable Long cursId) {
        return cursService.getDetaliiProfesorCurs(cursId);
    }
}
