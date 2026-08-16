package com.example.akadion.curs.service;

import com.example.akadion.admin.dto.AdminQuizNotaDto;
import com.example.akadion.admin.entity.NumeTabelAudit;
import com.example.akadion.admin.entity.OperatieAudit;
import com.example.akadion.admin.service.AuditLogService;
import com.example.akadion.curs.dto.CursRequestDto;
import com.example.akadion.curs.dto.CursResponseDto;
import com.example.akadion.curs.dto.ProfesorDetaliiResponseDto;
import com.example.akadion.curs.dto.StudentCursDto;
import com.example.akadion.curs.entity.Curs;
import com.example.akadion.curs.entity.UserCurs;
import com.example.akadion.quiz.entity.IncercareQuiz;
import com.example.akadion.quiz.entity.IncercareQuizStatus;
import com.example.akadion.common.entity.User;
import com.example.akadion.exception.ForbiddenOperationException;
import com.example.akadion.exception.ResursaNegasitaException;
import com.example.akadion.curs.repository.CursRepository;
import com.example.akadion.quiz.repository.IncercareQuizRepository;
import com.example.akadion.curs.repository.SaptamanaRepository;
import com.example.akadion.curs.repository.UserCursRepository;
import com.example.akadion.common.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class CursService {

    private static final String CURS_NOT_FOUND_PREFIX = "Cursul cu ID-ul ";
    private static final String CURS_NOT_FOUND_SUFFIX = " nu a fost găsit.";
    private static final String ERR_FARA_PERMISIUNE_MODIFICARE = "Nu aveți permisiunea de a modifica acest curs.";
    private static final String ACTIV_KEY = "activ";
    private static final String DENUMIRE_KEY = "denumire";
    private static final String DATA_INCEPUT_KEY = "dataInceput";

    private final CursRepository cursRepository;
    private final SaptamanaRepository saptamanaRepository;
    private final UserCursRepository userCursRepository;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;
    private final IncercareQuizRepository incercareQuizRepository;
    private final CursOwnershipValidator cursOwnershipValidator;

    public List<CursResponseDto> listaCursuriProprii(Long profesorId) {
        return toResponseDtoList(cursRepository.findByProfesorId(profesorId));
    }

    @Transactional(readOnly = true)
    public List<CursResponseDto> listaToateCursurile() {
        return toResponseDtoList(cursRepository.findAllWithProfesor());
    }

    @Transactional(readOnly = true)
    public long countCursuri(boolean activ) {
        return cursRepository.countByActiv(activ);
    }

    @Transactional(readOnly = true)
    public CursResponseDto getCursById(Long cursId, Long callerId, String callerRole) {
        Curs curs = cursRepository.findById(cursId)
                .orElseThrow(() -> new IllegalArgumentException(CURS_NOT_FOUND_PREFIX + cursId + CURS_NOT_FOUND_SUFFIX));

        cursOwnershipValidator.verificaProprietarSauAdmin(curs, callerId, callerRole, "Nu aveți acces la acest curs.");
        return toResponseDto(curs);
    }

    // Returnează studenții cu înscrierea activă și cont ACTIV pentru cursul dat.
    // Verifică că profesorul logat este owner-ul cursului (sau ADMIN).
    @Transactional(readOnly = true)
    public List<StudentCursDto> listaStudentiActivi(Long cursId, Long callerId, String callerRole) {
        Curs curs = cursRepository.findById(cursId)
                .orElseThrow(() -> new IllegalArgumentException(CURS_NOT_FOUND_PREFIX + cursId + CURS_NOT_FOUND_SUFFIX));

        cursOwnershipValidator.verificaProprietarSauAdmin(curs, callerId, callerRole, "Nu aveți acces la lista de studenți a acestui curs.");

        return userCursRepository.findActiveStudents(cursId).stream()
                .map(uc -> toStudentDto(uc.getStudent()))
                .toList();
    }

    @Transactional
    public void dezactiveazaToateCursurileProfesorului(Long profesorId) {
        List<Curs> cursuri = cursRepository.findByProfesorId(profesorId);
        for (Curs curs : cursuri) {
            if (curs.isActiv()) {
                curs.setActiv(false);
                cursRepository.save(curs);
                log.info("Curs dezactivat automat prin cascadă: cursId={}", curs.getId());
            }
            List<UserCurs> inscrieri = userCursRepository.findByCursId(curs.getId());
            for (UserCurs userCurs : inscrieri) {
                if (userCurs.isActiv()) {
                    userCurs.setActiv(false);
                    userCursRepository.save(userCurs);
                }
            }
        }
    }

    @Transactional
    public CursResponseDto creazaCurs(Long profesorId, CursRequestDto dto) {
        User profesor = userRepository.findById(profesorId)
                .orElseThrow(() -> new ResursaNegasitaException("Utilizatorul cu id=" + profesorId + " nu a fost gasit."));

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
                NumeTabelAudit.CURS,
                savedCurs.getId(),
                OperatieAudit.CREARE,
                null,
                Map.of(DENUMIRE_KEY, savedCurs.getDenumire(), DATA_INCEPUT_KEY, savedCurs.getDataInceput() != null ? savedCurs.getDataInceput().toString() : "")
        );
        log.info("Curs creat: cursId={}, de profesorId={}", savedCurs.getId(), profesorId);
        return toResponseDto(savedCurs);
    }

    @Transactional
    public CursResponseDto modificaCurs(Long cursId, Long profesorId, CursRequestDto dto) {
        Curs curs = cursRepository.findById(cursId)
                .orElseThrow(() -> new IllegalArgumentException(CURS_NOT_FOUND_PREFIX + cursId + CURS_NOT_FOUND_SUFFIX));

        cursOwnershipValidator.verificaProprietar(curs, profesorId, ERR_FARA_PERMISIUNE_MODIFICARE);

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
                NumeTabelAudit.CURS,
                savedCurs.getId(),
                OperatieAudit.EDITARE,
                Map.of(DENUMIRE_KEY, oldDenumire, "descriere", oldDescriere, DATA_INCEPUT_KEY, vecheaDataInceput != null ? vecheaDataInceput.toString() : ""),
                Map.of(DENUMIRE_KEY, savedCurs.getDenumire(), "descriere", savedCurs.getDescriere(), DATA_INCEPUT_KEY, savedCurs.getDataInceput() != null ? savedCurs.getDataInceput().toString() : "")
        );
        log.info("Curs modificat: cursId={}", savedCurs.getId());
        return toResponseDto(savedCurs);
    }

    @Transactional
    public void dezactiveazaCurs(Long cursId, Long profesorId) {
        Curs curs = cursRepository.findById(cursId)
                .orElseThrow(() -> new IllegalArgumentException(CURS_NOT_FOUND_PREFIX + cursId + CURS_NOT_FOUND_SUFFIX));

        cursOwnershipValidator.verificaProprietar(curs, profesorId, ERR_FARA_PERMISIUNE_MODIFICARE);

        if (!curs.isActiv()) {
            return;
        }

        curs.setActiv(false);
        cursRepository.save(curs);

        List<UserCurs> inscrieri = userCursRepository.findByCursId(cursId);
        for (UserCurs userCurs : inscrieri) {
            if (userCurs.isActiv()) {
                userCurs.setActiv(false);
                userCursRepository.save(userCurs);
            }
        }

        auditLogService.inregistreaza(
                NumeTabelAudit.CURS,
                cursId,
                OperatieAudit.DEZACTIVARE,
                Map.of(ACTIV_KEY, true),
                Map.of(ACTIV_KEY, false)
        );
        log.info("Curs dezactivat de profesor: cursId={}", cursId);
    }

    @Transactional
    public void activeazaCurs(Long cursId, Long profesorId) {
        Curs curs = cursRepository.findById(cursId)
                .orElseThrow(() -> new IllegalArgumentException(CURS_NOT_FOUND_PREFIX + cursId + CURS_NOT_FOUND_SUFFIX));

        cursOwnershipValidator.verificaProprietar(curs, profesorId, ERR_FARA_PERMISIUNE_MODIFICARE);

        if (curs.isActiv()) {
            return;
        }

        curs.setActiv(true);
        cursRepository.save(curs);

        auditLogService.inregistreaza(
                NumeTabelAudit.CURS,
                cursId,
                OperatieAudit.ACTIVARE,
                Map.of(ACTIV_KEY, false),
                Map.of(ACTIV_KEY, true)
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
                .orElseThrow(() -> new IllegalArgumentException(CURS_NOT_FOUND_PREFIX + cursId + CURS_NOT_FOUND_SUFFIX));
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
        return toResponseDto(curs, nrSaptamaniCurente, nrStudentiInscrisi);
    }

    private CursResponseDto toResponseDto(Curs curs, int nrSaptamaniCurente, int nrStudentiInscrisi) {
        User profesor = curs.getProfesor();
        return new CursResponseDto(
                curs.getId(),
                curs.getDenumire(),
                curs.getDescriere(),
                curs.getDataInceput(),
                curs.getDataSfarsit(),
                curs.isActiv(),
                nrSaptamaniCurente,
                profesor != null ? profesor.getNume() : null,
                profesor != null ? profesor.getPrenume() : null,
                nrStudentiInscrisi
        );
    }

    // Varianta pentru liste: preîncarcă numărul de săptămâni + studenți per curs în 2 interogări
    // (în loc de câte 2 interogări per curs, cum ar face toResponseDto(Curs) apelat în buclă).
    private List<CursResponseDto> toResponseDtoList(List<Curs> cursuri) {
        if (cursuri.isEmpty()) {
            return List.of();
        }

        List<Long> cursIds = cursuri.stream().map(Curs::getId).toList();
        Map<Long, Long> nrSaptamaniPerCurs = toCountMap(saptamanaRepository.countByCursIdIn(cursIds));
        Map<Long, Long> nrStudentiPerCurs = toCountMap(userCursRepository.countActiveByCursIdIn(cursIds));

        return cursuri.stream()
                .map(curs -> toResponseDto(
                        curs,
                        nrSaptamaniPerCurs.getOrDefault(curs.getId(), 0L).intValue(),
                        nrStudentiPerCurs.getOrDefault(curs.getId(), 0L).intValue()
                ))
                .toList();
    }

    private Map<Long, Long> toCountMap(List<Object[]> rows) {
        Map<Long, Long> map = new HashMap<>();
        for (Object[] row : rows) {
            map.put((Long) row[0], (Long) row[1]);
        }
        return map;
    }

    private StudentCursDto toStudentDto(User student) {
        return new StudentCursDto(
                student.getId(),
                student.getNume(),
                student.getPrenume(),
                student.getFacultate(),
                student.getMail()
        );
    }

    @Transactional(readOnly = true)
    public Page<AdminQuizNotaDto> getNoteQuizCurs(Long cursId, Pageable pageable) {
        Curs curs = cursRepository.findById(cursId)
                .orElseThrow(() -> new ResursaNegasitaException(CURS_NOT_FOUND_PREFIX + cursId + CURS_NOT_FOUND_SUFFIX));

        Page<IncercareQuiz> page = incercareQuizRepository.findByCursIdAndStatusOrderByCreatedAtDesc(
                curs.getId(),
                IncercareQuizStatus.FINALIZATA,
                pageable
        );

        return page.map(incercare -> {
            User student = incercare.getStudent();
            int nr = incercare.getNrIntrebari();
            int scor = incercare.getScor() != null ? incercare.getScor() : 0;
            double procentaj = nr > 0 ? Math.round(((double) scor / nr) * 10000.0) / 100.0 : 0.0;

            return new AdminQuizNotaDto(
                    incercare.getId(),
                    student != null ? student.getId() : null,
                    student != null ? student.getNume() : null,
                    student != null ? student.getPrenume() : null,
                    student != null ? student.getMail() : null,
                    scor,
                    nr,
                    procentaj,
                    incercare.getUpdatedAt() != null ? incercare.getUpdatedAt() : incercare.getCreatedAt()
            );
        });
    }
}
