package com.example.akadion.curs.service;

import com.example.akadion.common.dto.AkySursaDocumentDto;
import com.example.akadion.curs.dto.CursDisponibilResponseDto;
import com.example.akadion.curs.dto.CursInrolatResponseDto;
import com.example.akadion.curs.dto.DocumentStudentResponseDto;
import com.example.akadion.curs.dto.ProfesorDetaliiResponseDto;
import com.example.akadion.curs.dto.SaptamanaStudentResponseDto;
import com.example.akadion.curs.entity.Curs;
import com.example.akadion.curs.entity.Document;
import com.example.akadion.common.entity.NumeStareCont;
import com.example.akadion.curs.entity.Parcurs;
import com.example.akadion.curs.entity.Saptamana;
import com.example.akadion.common.entity.User;
import com.example.akadion.curs.entity.UserCurs;
import com.example.akadion.exception.ForbiddenOperationException;
import com.example.akadion.exception.ResursaNegasitaException;
import com.example.akadion.curs.repository.CursRepository;
import com.example.akadion.curs.repository.DocumentRepository;
import com.example.akadion.curs.repository.ParcursRepository;
import com.example.akadion.curs.repository.SaptamanaRepository;
import com.example.akadion.curs.repository.UserCursRepository;
import com.example.akadion.common.repository.UserRepository;
import com.example.akadion.auth.service.RateLimiterService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// Nucleul funcționalității de student: înscriere/retragere curs, progres pe săptămâni,
// acces la documente. Generarea/finalizarea quiz-urilor a fost mutată în StudentQuizService,
// iar chat-ul Aky + flashcards în StudentAkyService — ambele reutilizează
// determinaSaptamanaParcursaMax și verificaRateLimitAky de aici.
@Slf4j
@Service
@RequiredArgsConstructor
public class StudentCursService {

    private static final int MAX_CERERI_AKY_PE_MINUT = 10;
    private static final String ERR_SAPTAMANA_NOT_FOUND = "Săptămâna nu a fost găsită.";

    private final UserRepository userRepository;
    private final CursRepository cursRepository;
    private final UserCursRepository userCursRepository;
    private final SaptamanaRepository saptamanaRepository;
    private final ParcursRepository parcursRepository;
    private final DocumentRepository documentRepository;
    private final DocumentUrlBuilder documentUrlBuilder;
    private final RateLimiterService rateLimiterService;

    // Field injection intenționată: un service nu se poate auto-injecta prin constructor
    // (dependință circulară) fără @Lazy, iar Lombok @RequiredArgsConstructor nu suportă @Lazy per-parametru.
    // Necesar pentru ca apelurile interne prin self.xxx() să treacă prin proxy-ul @Transactional.
    @SuppressWarnings("java:S6813")
    @Autowired
    @Lazy
    private StudentCursService self;

    @Transactional
    public void inscriereCurs(Long studentId, Long cursId) {
        Curs curs = cursRepository.findById(cursId)
                .orElseThrow(() -> new IllegalArgumentException("Cursul cu ID-ul " + cursId + " nu a fost găsit."));

        if (!curs.isActiv()) {
            throw new ForbiddenOperationException("Nu vă puteți înscrie la un curs inactiv.");
        }

        User student = userRepository.findById(studentId)
                .orElseThrow(() -> new ResursaNegasitaException("Utilizatorul cu id=" + studentId + " nu a fost gasit."));

        UserCurs enrollment = userCursRepository.findByStudentIdAndCursId(studentId, cursId)
                .orElse(null);

        if (enrollment != null) {
            if (enrollment.isActiv()) {
                throw new IllegalArgumentException("Sunteți deja înrolat la acest curs.");
            }
            enrollment.setActiv(true);
            userCursRepository.save(enrollment);
            log.info("Înrolarea studentului {} la cursul {} a fost reactivată.", studentId, cursId);
            return;
        }

        try {
            self.executeInscriereNoua(student, curs);
            log.info("Înrolare nouă creată pentru studentul {} la cursul {}.", studentId, cursId);
        } catch (DataIntegrityViolationException e) {
            log.info("Studentul {} este deja înrolat la cursul {} (cerere concurentă, idempotent).", studentId, cursId);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void executeInscriereNoua(User student, Curs curs) {
        UserCurs newEnrollment = UserCurs.builder()
                .student(student)
                .curs(curs)
                .activ(true)
                .build();
        userCursRepository.saveAndFlush(newEnrollment);
    }

    @Transactional
    public void retragereCurs(Long studentId, Long cursId) {
        UserCurs enrollment = userCursRepository.findByStudentIdAndCursId(studentId, cursId)
                .orElseThrow(() -> new IllegalArgumentException("Nu sunteți înrolat la acest curs."));

        if (!enrollment.isActiv()) {
            throw new IllegalArgumentException("Sunteți deja retras din acest curs.");
        }

        enrollment.setActiv(false);
        userCursRepository.save(enrollment);
        log.info("Studentul {} s-a retras de la cursul {}.", studentId, cursId);
    }

    @Transactional(readOnly = true)
    public List<CursDisponibilResponseDto> listaCursuriDisponibile(Long studentId) {
        List<Curs> cursuri = cursRepository.findAvailableCoursesForStudent(studentId);
        if (cursuri.isEmpty()) {
            return List.of();
        }

        List<Long> cursIds = cursuri.stream().map(Curs::getId).toList();
        Map<Long, Long> nrSaptamaniPerCurs = toCountMap(saptamanaRepository.countByCursIdIn(cursIds));

        return cursuri.stream()
                .map(c -> new CursDisponibilResponseDto(
                        c.getId(),
                        c.getDenumire(),
                        c.getDescriere(),
                        c.getProfesor().getNume(),
                        c.getProfesor().getPrenume(),
                        c.getDataInceput(),
                        c.getDataSfarsit(),
                        nrSaptamaniPerCurs.getOrDefault(c.getId(), 0L).intValue()
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CursInrolatResponseDto> listaCursuriInrolate(Long studentId) {
        List<UserCurs> inscrieri = userCursRepository.findEnrolledCoursesForStudent(studentId);
        if (inscrieri.isEmpty()) {
            return List.of();
        }

        List<Long> cursIds = inscrieri.stream().map(uc -> uc.getCurs().getId()).toList();
        Map<Long, Long> totalSaptamaniPerCurs = toCountMap(saptamanaRepository.countByCursIdIn(cursIds));
        Map<Long, Long> saptamaniBifatePerCurs = toCountMap(parcursRepository.countCompletedWeeksByCursIdIn(studentId, cursIds));

        return inscrieri.stream()
                .map(uc -> {
                    Curs c = uc.getCurs();
                    long totalSaptamani = totalSaptamaniPerCurs.getOrDefault(c.getId(), 0L);
                    long saptamaniBifate = saptamaniBifatePerCurs.getOrDefault(c.getId(), 0L);
                    double progres = totalSaptamani > 0 ? (saptamaniBifate * 100.0) / totalSaptamani : 0.0;
                    return new CursInrolatResponseDto(
                            c.getId(),
                            c.getDenumire(),
                            c.getDescriere(),
                            c.getDataInceput(),
                            c.getDataSfarsit(),
                            c.getProfesor().getNume(),
                            c.getProfesor().getPrenume(),
                            progres,
                            (int) totalSaptamani
                    );
                })
                .toList();
    }

    private Map<Long, Long> toCountMap(List<Object[]> rows) {
        Map<Long, Long> map = new HashMap<>();
        for (Object[] row : rows) {
            map.put((Long) row[0], (Long) row[1]);
        }
        return map;
    }

    @Transactional(readOnly = true)
    public List<SaptamanaStudentResponseDto> listaSaptamaniCurs(Long studentId, Long cursId) {
        verificaStudentActivInrolat(studentId, cursId);

        List<Saptamana> saptamani = saptamanaRepository.findByCursIdOrderByNrSaptamana(cursId);
        List<Long> completedIds = parcursRepository.findCompletedWeekIds(studentId, cursId);

        return saptamani.stream()
                .map(s -> new SaptamanaStudentResponseDto(
                        s.getId(),
                        s.getNrSaptamana(),
                        s.getDescriere(),
                        completedIds.contains(s.getId())
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public void bifeazaSaptamana(Long studentId, Long saptamanaId) {
        Saptamana saptamana = saptamanaRepository.findWithCursAndProfesorById(saptamanaId)
                .orElseThrow(() -> new IllegalArgumentException(ERR_SAPTAMANA_NOT_FOUND));

        Long cursId = saptamana.getCurs().getId();
        UserCurs enrollment = verificaStudentActivInrolat(studentId, cursId);

        try {
            self.executeBifareTranzactionala(enrollment, saptamana);
        } catch (DataIntegrityViolationException e) {
            log.info("Săptămâna {} a fost deja bifată de studentul {} (Idempotent).", saptamanaId, studentId);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void executeBifareTranzactionala(UserCurs enrollment, Saptamana saptamana) {
        Parcurs parcurs = Parcurs.builder()
                .userCurs(enrollment)
                .saptamana(saptamana)
                .build();
        parcursRepository.saveAndFlush(parcurs);
    }

    @Transactional
    public void debifeazaSaptamana(Long studentId, Long saptamanaId) {
        Saptamana saptamana = saptamanaRepository.findWithCursAndProfesorById(saptamanaId)
                .orElseThrow(() -> new IllegalArgumentException(ERR_SAPTAMANA_NOT_FOUND));

        Long cursId = saptamana.getCurs().getId();
        UserCurs enrollment = verificaStudentActivInrolat(studentId, cursId);

        Parcurs parcurs = parcursRepository.findByUserCursIdAndSaptamanaId(enrollment.getId(), saptamanaId)
                .orElse(null);

        if (parcurs != null) {
            parcursRepository.delete(parcurs);
            log.info("Săptămâna {} a fost debifată de studentul {}.", saptamanaId, studentId);
        }
    }

    @Transactional(readOnly = true)
    public List<DocumentStudentResponseDto> listaDocumenteSaptamana(Long studentId, Long saptamanaId) {
        Saptamana saptamana = saptamanaRepository.findWithCursAndProfesorById(saptamanaId)
                .orElseThrow(() -> new IllegalArgumentException(ERR_SAPTAMANA_NOT_FOUND));

        Long cursId = saptamana.getCurs().getId();
        verificaStudentActivInrolat(studentId, cursId);

        return documentRepository.findBySaptamanaIdAndActivTrue(saptamanaId).stream()
                .map(doc -> new DocumentStudentResponseDto(
                        doc.getId(),
                        doc.getTitlu(),
                        documentUrlBuilder.previewUrl(doc),
                        documentUrlBuilder.downloadUrl(doc)
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public Document getAccessibleDocument(Long documentId, Long studentId) {
        Document document = documentRepository.findWithSaptamanaAndCursAndProfesorById(documentId)
                .orElseThrow(() -> new IllegalArgumentException("Documentul nu a fost găsit."));

        if (!document.isActiv()) {
            throw new IllegalArgumentException("Documentul nu a fost găsit.");
        }

        Long cursId = document.getSaptamana().getCurs().getId();
        verificaStudentActivInrolat(studentId, cursId);

        return document;
    }

    /**
     * Returnează detaliile profesorului unui curs.
     * Studentul trebuie să fie înrolat activ la cursul respectiv.
     * Accesibil din contextul cursului: GET /cursuri/{cursId}/profesor
     */
    @Transactional(readOnly = true)
    public ProfesorDetaliiResponseDto detaliiProfesorCurs(Long studentId, Long cursId) {
        UserCurs enrollment = verificaStudentActivInrolat(studentId, cursId);

        // Curs și profesor sunt lazy — funcționează corect în context @Transactional
        User profesor = enrollment.getCurs().getProfesor();

        return new ProfesorDetaliiResponseDto(
                profesor.getId(),
                profesor.getNume(),
                profesor.getPrenume(),
                profesor.getMail(),
                profesor.getFacultate()
        );
    }

    @Transactional(readOnly = true)
    public Integer determinaSaptamanaParcursaMax(Long studentId, Long cursId) {
        verificaStudentActivInrolat(studentId, cursId);

        List<Saptamana> saptamani = saptamanaRepository.findByCursIdOrderByNrSaptamana(cursId);
        List<Long> completedIds = parcursRepository.findCompletedWeekIds(studentId, cursId);

        int maxWeek = 1;
        for (Saptamana s : saptamani) {
            if (completedIds.contains(s.getId()) && s.getNrSaptamana() != null && s.getNrSaptamana() >= maxWeek) {
                maxWeek = s.getNrSaptamana() + 1;
            }
        }

        int totalSapt = saptamani.size();
        if (maxWeek > totalSapt && totalSapt > 0) {
            maxWeek = totalSapt;
        }
        return maxWeek;
    }

    @Transactional(readOnly = true)
    public List<AkySursaDocumentDto> listaDocumenteAccesibile(Long studentId, Long cursId) {
        int maxSaptamana = self.determinaSaptamanaParcursaMax(studentId, cursId);

        List<Long> saptamaniIds = saptamanaRepository.findByCursIdOrderByNrSaptamana(cursId).stream()
                .filter(saptamana -> saptamana.getNrSaptamana() != null && saptamana.getNrSaptamana() <= maxSaptamana)
                .map(Saptamana::getId)
                .toList();

        if (saptamaniIds.isEmpty()) {
            return List.of();
        }

        return documentRepository.findBySaptamanaIdInAndActivTrue(saptamaniIds).stream()
                .map(document -> new AkySursaDocumentDto(document.getId(), document.getTitlu()))
                .toList();
    }

    // Rate limit comun pentru quiz/flashcards/chat Aky (StudentQuizService + StudentAkyService),
    // exact ca înainte de split — cele trei funcționalități împart aceeași limită per student.
    public void verificaRateLimitAky(Long studentId) {
        rateLimiterService.verificaLimita("student-aky:" + studentId, MAX_CERERI_AKY_PE_MINUT, Duration.ofMinutes(1));
    }

    private UserCurs verificaStudentActivInrolat(Long studentId, Long cursId) {
        UserCurs enrollment = userCursRepository.findByStudentIdAndCursId(studentId, cursId)
                .orElseThrow(() -> new ForbiddenOperationException("Nu aveți acces la acest curs."));

        if (!enrollment.isActiv()) {
            throw new ForbiddenOperationException("Nu aveți o înrolare activă la acest curs.");
        }

        User student = enrollment.getStudent();
        if (student.getStareCont() == null || !NumeStareCont.ACTIV.name().equalsIgnoreCase(student.getStareCont().getDenumire())) {
            throw new ForbiddenOperationException("Contul de student nu este activ.");
        }

        return enrollment;
    }
}
