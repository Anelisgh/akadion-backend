package com.example.akadion.admin.controller;

import com.example.akadion.admin.dto.AdminQuizNotaDto;
import com.example.akadion.admin.dto.AuditLogDto;
import com.example.akadion.admin.dto.DashboardStatsDto;
import com.example.akadion.admin.service.AuditLogService;
import com.example.akadion.auth.dto.ActionResponseDto;
import com.example.akadion.curs.dto.CursResponseDto;
import com.example.akadion.curs.dto.DocumentResponseDto;
import com.example.akadion.curs.dto.ProfesorDetaliiResponseDto;
import com.example.akadion.curs.dto.SaptamanaResponseDto;
import com.example.akadion.curs.dto.StudentCursDto;
import com.example.akadion.auth.dto.UserPendingDto;
import com.example.akadion.auth.service.AdminUserService;
import com.example.akadion.common.entity.NumeRol;
import com.example.akadion.common.entity.NumeStareCont;
import com.example.akadion.curs.service.CursService;
import com.example.akadion.curs.service.DocumentService;
import com.example.akadion.curs.service.SaptamanaService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminUserService adminUserService;
    private final CursService cursService;
    private final SaptamanaService saptamanaService;
    private final DocumentService documentService;
    private final AuditLogService auditLogService;

    @GetMapping("/users")
    @PreAuthorize("hasRole('ADMIN')")
    public List<UserPendingDto> listaUseri(
            @RequestParam(defaultValue = "PENDING") String stare) {
        return adminUserService.listaUtilizatori(stare);
    }

    @PatchMapping("/users/{id}/approve")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasRole('ADMIN')")
    public ActionResponseDto approveUser(@PathVariable Long id) {
        return adminUserService.approveUser(id);
    }

    @PatchMapping("/users/{id}/reject")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasRole('ADMIN')")
    public ActionResponseDto rejectUser(@PathVariable Long id) {
        return adminUserService.rejectUser(id);
    }

    @PostMapping("/users/{id}/deactivate")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasRole('ADMIN')")
    public void dezactiveazaUser(@PathVariable Long id) {
        adminUserService.dezactiveazaUser(id);
    }

    @PostMapping("/users/{id}/activate")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasRole('ADMIN')")
    public void activeazaUser(@PathVariable Long id) {
        adminUserService.activeazaUser(id);
    }

    @GetMapping("/cursuri")
    @PreAuthorize("hasRole('ADMIN')")
    public List<CursResponseDto> listaToateCursurile() {
        return cursService.listaToateCursurile();
    }

    @GetMapping("/stats")
    @PreAuthorize("hasRole('ADMIN')")
    public DashboardStatsDto getStats() {
        long cursuriActive = cursService.countCursuri(true);
        long cursuriInactive = cursService.countCursuri(false);
        long utilizatoriActivi = adminUserService.countUtilizatori(NumeStareCont.ACTIV.name());
        long utilizatoriPending = adminUserService.countUtilizatori(NumeStareCont.PENDING.name());

        return new DashboardStatsDto(cursuriActive, cursuriInactive, utilizatoriActivi, utilizatoriPending);
    }

    @GetMapping("/cursuri/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public CursResponseDto getCurs(@PathVariable Long id) {
        return cursService.getCursById(id, null, NumeRol.ADMIN.name());
    }

    @GetMapping("/cursuri/{id}/saptamani")
    @PreAuthorize("hasRole('ADMIN')")
    public List<SaptamanaResponseDto> listaSaptamani(@PathVariable Long id) {
        return saptamanaService.listaSaptamani(id, null, NumeRol.ADMIN.name());
    }

    @GetMapping("/saptamani/{saptamanaId}/documente")
    @PreAuthorize("hasRole('ADMIN')")
    public List<DocumentResponseDto> listaDocumente(@PathVariable Long saptamanaId) {
        return documentService.listaDocumente(saptamanaId, null, NumeRol.ADMIN.name());
    }

    @GetMapping("/cursuri/{id}/studenti")
    @PreAuthorize("hasRole('ADMIN')")
    public List<StudentCursDto> listaStudentiInscrisi(@PathVariable Long id) {
        return cursService.listaStudentiActivi(id, null, NumeRol.ADMIN.name());
    }

    @GetMapping("/cursuri/{cursId}/profesor")
    @PreAuthorize("hasRole('ADMIN')")
    public ProfesorDetaliiResponseDto detaliiProfesorCurs(@PathVariable Long cursId) {
        return cursService.getDetaliiProfesorCurs(cursId);
    }

    @GetMapping("/audit-log")
    @PreAuthorize("hasRole('ADMIN')")
    public Slice<AuditLogDto> getAuditLog(Pageable pageable) {
        return auditLogService.getAuditLog(pageable);
    }

    @GetMapping("/cursuri/{cursId}/quiz-note")
    @PreAuthorize("hasRole('ADMIN')")
    public Page<AdminQuizNotaDto> getNoteQuizCurs(
            @PathVariable Long cursId,
            Pageable pageable) {
        return cursService.getNoteQuizCurs(cursId, pageable);
    }
}
