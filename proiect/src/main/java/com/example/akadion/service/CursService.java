package com.example.akadion.service;

import com.example.akadion.dto.CursRequestDto;
import com.example.akadion.dto.CursResponseDto;
import com.example.akadion.dto.ProfesorDetaliiResponseDto;
import com.example.akadion.dto.StudentCursDto;
import com.example.akadion.entity.Curs;
import com.example.akadion.entity.User;
import com.example.akadion.entity.UserCurs;
import com.example.akadion.exception.AccesInterzisException;
import com.example.akadion.exception.UserNotFoundException;
import com.example.akadion.repository.CursRepository;
import com.example.akadion.repository.SaptamanaRepository;
import com.example.akadion.repository.UserCursRepository;
import com.example.akadion.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class CursService {

    private final CursRepository cursRepository;
    private final SaptamanaRepository saptamanaRepository;
    private final UserCursRepository userCursRepository;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;

    public List<CursResponseDto> listaCursuriProprii(Long profesorId) {
        return cursRepository.findByProfesorId(profesorId).stream()
                .map(this::toResponseDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CursResponseDto> listaToateCursurile() {
        return cursRepository.findAll().stream()
                .map(this::toResponseDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public long countCursuri(boolean activ) {
        return cursRepository.countByActiv(activ);
    }

    @Transactional(readOnly = true)
    public CursResponseDto getCursById(Long cursId, Long callerId, String callerRole) {
        Curs curs = cursRepository.findById(cursId)
                .orElseThrow(() -> new IllegalArgumentException("Cursul cu ID-ul " + cursId + " nu a fost găsit."));

        if (!"ADMIN".equals(callerRole)) {
            if (!curs.getProfesor().getId().equals(callerId)) {
                throw new AccesInterzisException("Nu aveți acces la acest curs.");
            }
        }
        return toResponseDto(curs);
    }

    // Returnează studenții cu înscrierea activă și cont ACTIV pentru cursul dat.
    // Verifică că profesorul logat este owner-ul cursului (sau ADMIN).
    @Transactional(readOnly = true)
    public List<StudentCursDto> listaStudentiActivi(Long cursId, Long callerId, String callerRole) {
        Curs curs = cursRepository.findById(cursId)
                .orElseThrow(() -> new IllegalArgumentException("Cursul cu ID-ul " + cursId + " nu a fost găsit."));

        if (!"ADMIN".equals(callerRole) && !curs.getProfesor().getId().equals(callerId)) {
            throw new AccesInterzisException("Nu aveți acces la lista de studenți a acestui curs.");
        }

        return userCursRepository.findStudentiActivi(cursId).stream()
                .map(uc -> toStudentDto(uc.getStudent()))
                .toList();
    }

    @Transactional
    public void dezactiveazaToateCursurileProfesorului(Long profesorId) {
        List<Curs> cursuri = cursRepository.findByProfesorId(profesorId);
        for (Curs curs : cursuri) {
            if (Boolean.TRUE.equals(curs.getActiv())) {
                curs.setActiv(false);
                cursRepository.save(curs);
                log.info("Curs dezactivat automat prin cascadă: cursId={}", curs.getId());
            }
            List<UserCurs> inscrieri = userCursRepository.findByCursId(curs.getId());
            for (UserCurs userCurs : inscrieri) {
                if (Boolean.TRUE.equals(userCurs.getActiv())) {
                    userCurs.setActiv(false);
                    userCursRepository.save(userCurs);
                }
            }
        }
    }

    @Transactional
    public CursResponseDto creazaCurs(Long profesorId, CursRequestDto dto) {
        User profesor = userRepository.findById(profesorId)
                .orElseThrow(() -> new UserNotFoundException(profesorId));

        Curs curs = Curs.builder()
                .profesor(profesor)
                .denumire(dto.denumire())
                .descriere(dto.descriere())
                .dataInceput(dto.dataInceput())
                .dataSfarsit(null)
                .activ(true)
                .build();

        Curs savedCurs = cursRepository.save(curs);
        
        auditLogService.inregistreaza(
                "curs",
                savedCurs.getId(),
                "CREARE",
                null,
                Map.of("denumire", savedCurs.getDenumire(), "dataInceput", savedCurs.getDataInceput() != null ? savedCurs.getDataInceput().toString() : "")
        );
        
        log.info("Curs creat: cursId={}, de profesorId={}", savedCurs.getId(), profesorId);
        return toResponseDto(savedCurs);
    }

    @Transactional
    public CursResponseDto modificaCurs(Long cursId, Long profesorId, CursRequestDto dto) {
        Curs curs = cursRepository.findById(cursId)
                .orElseThrow(() -> new IllegalArgumentException("Cursul cu ID-ul " + cursId + " nu a fost găsit."));

        if (!curs.getProfesor().getId().equals(profesorId)) {
            throw new AccesInterzisException("Nu aveți permisiunea de a modifica acest curs.");
        }

        String oldDenumire = curs.getDenumire();
        String oldDescriere = curs.getDescriere();

        curs.setDenumire(dto.denumire());
        curs.setDescriere(dto.descriere());

        LocalDate vecheaDataInceput = curs.getDataInceput();
        LocalDate nouaDataInceput = dto.dataInceput();

        boolean dataInceputSchimbata = (vecheaDataInceput == null && nouaDataInceput != null)
                || (vecheaDataInceput != null && !vecheaDataInceput.equals(nouaDataInceput));

        if (dataInceputSchimbata) {
            curs.setDataInceput(nouaDataInceput);
            if (nouaDataInceput == null) {
                curs.setDataSfarsit(null);
            } else {
                recalculeazaDataSfarsit(curs);
            }
        }

        Curs savedCurs = cursRepository.save(curs);
        
        auditLogService.inregistreaza(
                "curs",
                savedCurs.getId(),
                "EDITARE",
                Map.of("denumire", oldDenumire, "descriere", oldDescriere, "dataInceput", vecheaDataInceput != null ? vecheaDataInceput.toString() : ""),
                Map.of("denumire", savedCurs.getDenumire(), "descriere", savedCurs.getDescriere(), "dataInceput", savedCurs.getDataInceput() != null ? savedCurs.getDataInceput().toString() : "")
        );
        
        log.info("Curs modificat: cursId={}", savedCurs.getId());
        return toResponseDto(savedCurs);
    }

    @Transactional
    public void dezactiveazaCurs(Long cursId, Long profesorId) {
        Curs curs = cursRepository.findById(cursId)
                .orElseThrow(() -> new IllegalArgumentException("Cursul cu ID-ul " + cursId + " nu a fost găsit."));

        if (!curs.getProfesor().getId().equals(profesorId)) {
            throw new AccesInterzisException("Nu aveți permisiunea de a modifica acest curs.");
        }

        if (Boolean.FALSE.equals(curs.getActiv())) {
            return;
        }

        curs.setActiv(false);
        cursRepository.save(curs);

        List<UserCurs> inscrieri = userCursRepository.findByCursId(cursId);
        for (UserCurs userCurs : inscrieri) {
            if (Boolean.TRUE.equals(userCurs.getActiv())) {
                userCurs.setActiv(false);
                userCursRepository.save(userCurs);
            }
        }
        
        auditLogService.inregistreaza(
                "curs",
                cursId,
                "DEZACTIVARE",
                Map.of("activ", true),
                Map.of("activ", false)
        );
        
        log.info("Curs dezactivat de profesor: cursId={}", cursId);
    }

    @Transactional
    public void activeazaCurs(Long cursId, Long profesorId) {
        Curs curs = cursRepository.findById(cursId)
                .orElseThrow(() -> new IllegalArgumentException("Cursul cu ID-ul " + cursId + " nu a fost găsit."));

        if (!curs.getProfesor().getId().equals(profesorId)) {
            throw new AccesInterzisException("Nu aveți permisiunea de a modifica acest curs.");
        }

        if (Boolean.TRUE.equals(curs.getActiv())) {
            return;
        }

        curs.setActiv(true);
        cursRepository.save(curs);
        
        auditLogService.inregistreaza(
                "curs",
                cursId,
                "ACTIVARE",
                Map.of("activ", false),
                Map.of("activ", true)
        );
        
        log.info("Curs activat de profesor: cursId={}", cursId);
    }

    public void recalculeazaDataSfarsit(Curs curs) {
        if (curs.getDataInceput() == null) {
            return;
        }
        long saptamaniCount = saptamanaRepository.countByCursId(curs.getId());
        if (saptamaniCount == 0) {
            curs.setDataSfarsit(null);
        } else {
            curs.setDataSfarsit(curs.getDataInceput().plusDays(saptamaniCount * 7L - 1));
        }
    }

    @Transactional(readOnly = true)
    public ProfesorDetaliiResponseDto getDetaliiProfesorCurs(Long cursId) {
        Curs curs = cursRepository.findById(cursId)
                .orElseThrow(() -> new IllegalArgumentException("Cursul cu ID-ul " + cursId + " nu a fost găsit."));
        User profesor = curs.getProfesor();
        if (profesor == null) {
            throw new IllegalArgumentException("Cursul nu are un profesor asociat.");
        }
        return new ProfesorDetaliiResponseDto(
                profesor.getId(),
                profesor.getNume(),
                profesor.getPrenume(),
                profesor.getMail(),
                profesor.getFacultate()
        );
    }

    private CursResponseDto toResponseDto(Curs curs) {
        int nrSaptamaniCurente = (int) saptamanaRepository.countByCursId(curs.getId());
        int nrStudentiInscrisi = (int) userCursRepository.countByCursIdAndActivTrue(curs.getId());
        User profesor = curs.getProfesor();
        return new CursResponseDto(
                curs.getId(),
                curs.getDenumire(),
                curs.getDescriere(),
                curs.getDataInceput(),
                curs.getDataSfarsit(),
                curs.getActiv(),
                nrSaptamaniCurente,
                profesor != null ? profesor.getNume() : null,
                profesor != null ? profesor.getPrenume() : null,
                nrStudentiInscrisi
        );
    }

    private StudentCursDto toStudentDto(com.example.akadion.entity.User student) {
        return new StudentCursDto(
                student.getId(),
                student.getNume(),
                student.getPrenume(),
                student.getFacultate(),
                student.getMail()
        );
    }
}
