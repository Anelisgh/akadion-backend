package com.example.akadion.controller;

import com.example.akadion.dto.UserPendingDto;
import com.example.akadion.service.AdminUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")  // Toate endpoint-urile din acest controller cer rol ADMIN
public class AdminController {

    private final AdminUserService adminUserService;

    /**
     * Listează cereri filtrate după stare.
     * Exemplu: GET /api/admin/users?stare=PENDING
     */
    @GetMapping("/users")
    public List<UserPendingDto> listaUseri(
            @RequestParam(defaultValue = "PENDING") String stare) {
        // Singura stare implementată în acest plan — extindere pentru alte stări în iterații viitoare
        return adminUserService.listaCereriPending();
    }

    /** Acceptă cererea de înregistrare — creează userul în Keycloak și trimite email de parolă. */
    @PostMapping("/users/{id}/accept")
    @ResponseStatus(HttpStatus.OK)
    public void acceptaUser(@PathVariable Long id) {
        adminUserService.acceptaUser(id);
    }

    /** Respinge cererea — STARE_CONT → RESPINS, fără apel Keycloak. */
    @PostMapping("/users/{id}/reject")
    @ResponseStatus(HttpStatus.OK)
    public void respingeUser(@PathVariable Long id) {
        adminUserService.respingeUser(id);
    }

    /** Dezactivează un utilizator ACTIV — STARE_CONT → INACTIV + dezactivare cont Keycloak. */
    @PostMapping("/users/{id}/deactivate")
    @ResponseStatus(HttpStatus.OK)
    public void dezactiveazaUser(@PathVariable Long id) {
        adminUserService.dezactiveazaUser(id);
    }

    /** Activează (reactivează) un utilizator INACTIV — STARE_CONT → ACTIV + activare cont Keycloak. */
    @PostMapping("/users/{id}/activate")
    @ResponseStatus(HttpStatus.OK)
    public void activeazaUser(@PathVariable Long id) {
        adminUserService.activeazaUser(id);
    }
}
