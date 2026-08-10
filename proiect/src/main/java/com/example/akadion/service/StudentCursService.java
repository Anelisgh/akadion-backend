package com.example.akadion.service;

import com.example.akadion.dto.*;
import com.example.akadion.entity.*;
import com.example.akadion.exception.AccesInterzisException;
import com.example.akadion.exception.UserNotFoundException;
import com.example.akadion.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class StudentCursService {

    private final UserRepository userRepository;
    private final CursRepository cursRepository;
    private final UserCursRepository userCursRepository;
    private final SaptamanaRepository saptamanaRepository;
    private final ParcursRepository parcursRepository;
    private final DocumentRepository documentRepository;
    private final MinioStorageService minioStorageService;
    private final RagChatService ragChatService;
    private final AuditLogService auditLogService;

    @Autowired
    @Lazy
    private StudentCursService self;

    @Transactional
    public void inscriereCurs(Long studentId, Long cursId) {
        Curs curs = cursRepository.findById(cursId)
                .orElseThrow(() -> new IllegalArgumentException("Cursul cu ID-ul " + cursId + " nu a fost găsit."));

        if (!Boolean.TRUE.equals(curs.getActiv())) {
            throw new AccesInterzisException("Nu vă puteți înscrie la un curs inactiv.");
        }

        User student = userRepository.findById(studentId)
                .orElseThrow(() -> new UserNotFoundException(studentId));

        UserCurs enrollment = userCursRepository.findByStudentIdAndCursId(studentId, cursId)
                .orElse(null);

        if (enrollment != null) {
            if (Boolean.TRUE.equals(enrollment.getActiv())) {
                throw new IllegalArgumentException("Sunteți deja înrolat la acest curs.");
            }
            enrollment.setActiv(true);
            userCursRepository.save(enrollment);
            
            auditLogService.inregistreaza(
                    "user_curs",
                    enrollment.getId(),
                    "INSCRIERE",
                    Map.of("cursId", cursId, "activ", false),
                    Map.of("cursId", cursId, "activ", true)
            );
            log.info("Înrolarea studentului {} la cursul {} a fost reactivată.", studentId, cursId);
        } else {
            UserCurs newEnrollment = UserCurs.builder()
                    .student(student)
                    .curs(curs)
                    .activ(true)
                    .build();
            userCursRepository.save(newEnrollment);
            
            auditLogService.inregistreaza(
                    "user_curs",
                    newEnrollment.getId(),
                    "INSCRIERE",
                    null,
                    Map.of("cursId", cursId, "activ", true)
            );
            log.info("Înrolare nouă creată pentru studentul {} la cursul {}.", studentId, cursId);
        }
    }

    @Transactional
    public void retragereCurs(Long studentId, Long cursId) {
        UserCurs enrollment = userCursRepository.findByStudentIdAndCursId(studentId, cursId)
                .orElseThrow(() -> new IllegalArgumentException("Nu sunteți înrolat la acest curs."));

        if (!Boolean.TRUE.equals(enrollment.getActiv())) {
            throw new IllegalArgumentException("Sunteți deja retras din acest curs.");
        }

        enrollment.setActiv(false);
        userCursRepository.save(enrollment);
        
        auditLogService.inregistreaza(
                "user_curs",
                enrollment.getId(),
                "RETRAGERE",
                Map.of("cursId", cursId, "activ", true),
                Map.of("cursId", cursId, "activ", false)
        );
        log.info("Studentul {} s-a retras de la cursul {}.", studentId, cursId);
    }

    @Transactional(readOnly = true)
    public List<CursDisponibilResponseDto> listaCursuriDisponibile(Long studentId) {
        return cursRepository.findCursuriDisponibilePentruStudent(studentId).stream()
                .map(c -> {
                    int nrSaptamani = (int) saptamanaRepository.countByCursId(c.getId());
                    return new CursDisponibilResponseDto(
                            c.getId(),
                            c.getDenumire(),
                            c.getDescriere(),
                            c.getProfesor().getNume(),
                            c.getProfesor().getPrenume(),
                            c.getDataInceput(),
                            c.getDataSfarsit(),
                            nrSaptamani
                    );
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CursInrolatResponseDto> listaCursuriInrolate(Long studentId) {
        return userCursRepository.findCursuriInrolatePentruStudent(studentId).stream()
                .map(uc -> {
                    Curs c = uc.getCurs();
                    long totalSaptamani = saptamanaRepository.countByCursId(c.getId());
                    double progres = totalSaptamani > 0
                            ? (parcursRepository.countCompletedSaptamani(studentId, c.getId()) * 100.0) / totalSaptamani
                            : 0.0;
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

    @Transactional(readOnly = true)
    public List<SaptamanaStudentResponseDto> listaSaptamaniCurs(Long studentId, Long cursId) {
        UserCurs enrollment = userCursRepository.findByStudentIdAndCursId(studentId, cursId)
                .orElseThrow(() -> new AccesInterzisException("Nu aveți acces la acest curs."));

        if (!Boolean.TRUE.equals(enrollment.getActiv())) {
            throw new AccesInterzisException("Nu aveți o înrolare activă la acest curs.");
        }

        List<Saptamana> saptamani = saptamanaRepository.findByCursIdOrderByNrSaptamana(cursId);
        List<Long> completedIds = parcursRepository.findCompletedSaptamaniIds(studentId, cursId);

        return saptamani.stream()
                .map(s -> new SaptamanaStudentResponseDto(
                        s.getId(),
                        s.getNrSaptamana(),
                        s.getDescriere(),
                        completedIds.contains(s.getId())
                ))
                .toList();
    }

    @Transactional
    public void bifeazaSaptamana(Long studentId, Long saptamanaId) {
        Saptamana saptamana = saptamanaRepository.findWithCursAndProfesorById(saptamanaId)
                .orElseThrow(() -> new IllegalArgumentException("Săptămâna nu a fost găsită."));

        Long cursId = saptamana.getCurs().getId();
        UserCurs enrollment = userCursRepository.findByStudentIdAndCursId(studentId, cursId)
                .orElseThrow(() -> new AccesInterzisException("Nu sunteți înrolat la acest curs."));

        if (!Boolean.TRUE.equals(enrollment.getActiv())) {
            throw new AccesInterzisException("Nu aveți o înrolare activă la acest curs.");
        }

        try {
            self.executeBifareTranzactionala(enrollment, saptamana);
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
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
                .orElseThrow(() -> new IllegalArgumentException("Săptămâna nu a fost găsită."));

        Long cursId = saptamana.getCurs().getId();
        UserCurs enrollment = userCursRepository.findByStudentIdAndCursId(studentId, cursId)
                .orElseThrow(() -> new AccesInterzisException("Nu aveți înrolare la acest curs."));

        if (!Boolean.TRUE.equals(enrollment.getActiv())) {
            throw new AccesInterzisException("Nu aveți o înrolare activă la acest curs.");
        }

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
                .orElseThrow(() -> new IllegalArgumentException("Săptămâna nu a fost găsită."));

        Long cursId = saptamana.getCurs().getId();
        UserCurs enrollment = userCursRepository.findByStudentIdAndCursId(studentId, cursId)
                .orElseThrow(() -> new AccesInterzisException("Nu aveți acces la documentele acestui curs."));

        if (!Boolean.TRUE.equals(enrollment.getActiv())) {
            throw new AccesInterzisException("Nu aveți o înrolare activă la acest curs.");
        }

        return documentRepository.findBySaptamanaIdAndActivTrue(saptamanaId).stream()
                .map(doc -> {
                    String urlVizualizare = minioStorageService.getPresignedPreviewUrl(doc.getPathMinio());
                    String urlDescarcare = minioStorageService.getPresignedDownloadUrl(doc.getPathMinio());
                    return new DocumentStudentResponseDto(
                            doc.getId(),
                            doc.getTitlu(),
                            urlVizualizare,
                            urlDescarcare
                    );
                })
                .toList();
    }

    /**
     * Returnează detaliile profesorului unui curs.
     * Studentul trebuie să fie înrolat activ la cursul respectiv.
     * Accesibil din contextul cursului: GET /cursuri/{cursId}/profesor
     */
    @Transactional(readOnly = true)
    public ProfesorDetaliiResponseDto detaliiProfesorCurs(Long studentId, Long cursId) {
        UserCurs enrollment = userCursRepository.findByStudentIdAndCursId(studentId, cursId)
                .orElseThrow(() -> new AccesInterzisException("Nu sunteți înrolat la acest curs."));

        if (!Boolean.TRUE.equals(enrollment.getActiv())) {
            throw new AccesInterzisException("Nu aveți o înrolare activă la acest curs.");
        }

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

    private final java.util.concurrent.ConcurrentHashMap<Long, List<Long>> rateLimitMap = new java.util.concurrent.ConcurrentHashMap<>();

    private void checkRateLimit(Long studentId) {
        long now = System.currentTimeMillis();
        long windowStart = now - 60000; // 1 minut

        rateLimitMap.compute(studentId, (id, timestamps) -> {
            List<Long> list = timestamps != null ? timestamps : new java.util.ArrayList<>();
            list.removeIf(ts -> ts < windowStart);
            if (list.size() >= 10) {
                throw new com.example.akadion.exception.TooManyRequestsException("Ai depășit limita de 10 întrebări pe minut. Te rugăm să aștepți puțin.");
            }
            list.add(now);
            return list;
        });
    }

    /**
     * Interogare Chatbot Aky pentru un curs specific.
     * Verifică înrolarea studentului, parcursul maxim de săptămâni și aplică rate limiting.
     */
    @Transactional(readOnly = true)
    public AkyChatResponseDto intreabaAky(Long studentId, Long cursId, AkyChatRequestDto request) {
        checkRateLimit(studentId);

        UserCurs enrollment = userCursRepository.findByStudentIdAndCursId(studentId, cursId)
                .orElseThrow(() -> new AccesInterzisException("Nu aveți acces la acest curs."));

        if (!Boolean.TRUE.equals(enrollment.getActiv())) {
            throw new AccesInterzisException("Nu aveți o înrolare activă la acest curs.");
        }

        return ragChatService.intreabaAky(studentId, cursId, request);
    }

    @Transactional(readOnly = true)
    public Integer determinaSaptamanaParcursaMax(Long studentId, Long cursId) {
        verificaStudentActivInrolat(studentId, cursId);

        List<Saptamana> saptamani = saptamanaRepository.findByCursIdOrderByNrSaptamana(cursId);
        List<Long> completedIds = parcursRepository.findCompletedSaptamaniIds(studentId, cursId);

        int maxWeek = 1;
        for (Saptamana s : saptamani) {
            if (completedIds.contains(s.getId())) {
                if (s.getNrSaptamana() != null && s.getNrSaptamana() >= maxWeek) {
                    maxWeek = s.getNrSaptamana() + 1;
                }
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
        int maxSaptamana = determinaSaptamanaParcursaMax(studentId, cursId);

        return saptamanaRepository.findByCursIdOrderByNrSaptamana(cursId).stream()
                .filter(saptamana -> saptamana.getNrSaptamana() != null && saptamana.getNrSaptamana() <= maxSaptamana)
                .flatMap(saptamana -> documentRepository.findBySaptamanaIdAndActivTrue(saptamana.getId()).stream())
                .map(document -> new AkySursaDocumentDto(document.getId(), document.getTitlu()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> genereazaQuiz(Long studentId, Long cursId, QuizGenerateRequestDto request) {
        checkRateLimit(studentId);
        int maxSaptamana = determinaSaptamanaParcursaMax(studentId, cursId);
        Long documentId = request != null ? request.documentId() : null;
        Integer nrIntrebari = request != null && request.nrIntrebari() != null ? request.nrIntrebari() : 5;

        if (nrIntrebari < 1 || nrIntrebari > 20) {
            throw new IllegalArgumentException("Numărul de întrebări trebuie să fie între 1 și 20.");
        }

        if (documentId != null) {
            Document document = documentRepository.findWithSaptamanaAndCursAndProfesorById(documentId)
                    .orElseThrow(() -> new IllegalArgumentException("Documentul nu a fost găsit."));

            if (!Boolean.TRUE.equals(document.getActiv())) {
                throw new AccesInterzisException("Documentul nu este activ.");
            }

            Saptamana saptamana = document.getSaptamana();
            if (saptamana == null || saptamana.getCurs() == null || !saptamana.getCurs().getId().equals(cursId)) {
                throw new AccesInterzisException("Documentul nu aparține acestui curs.");
            }

            Integer nrSaptamana = saptamana.getNrSaptamana();
            if (nrSaptamana == null || nrSaptamana > maxSaptamana) {
                throw new AccesInterzisException("Documentul nu este accesibil încă.");
            }
        }

        return ragChatService.genereazaQuiz(cursId, maxSaptamana, documentId, nrIntrebari);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> genereazaFlashcards(Long studentId, Long cursId, FlashcardGenerateRequestDto request) {
        checkRateLimit(studentId);
        int maxSaptamana = determinaSaptamanaParcursaMax(studentId, cursId);
        Long documentId = request != null ? request.documentId() : null;
        Integer nrFlashcards = request != null && request.nrFlashcards() != null ? request.nrFlashcards() : 5;

        if (nrFlashcards < 1 || nrFlashcards > 20) {
            throw new IllegalArgumentException("Numărul de flashcard-uri trebuie să fie între 1 și 20.");
        }

        if (documentId != null) {
            Document document = documentRepository.findWithSaptamanaAndCursAndProfesorById(documentId)
                    .orElseThrow(() -> new IllegalArgumentException("Documentul nu a fost găsit."));

            if (!Boolean.TRUE.equals(document.getActiv())) {
                throw new AccesInterzisException("Documentul nu este activ.");
            }

            Saptamana saptamana = document.getSaptamana();
            if (saptamana == null || saptamana.getCurs() == null || !saptamana.getCurs().getId().equals(cursId)) {
                throw new AccesInterzisException("Documentul nu aparține acestui curs.");
            }

            Integer nrSaptamana = saptamana.getNrSaptamana();
            if (nrSaptamana == null || nrSaptamana > maxSaptamana) {
                throw new AccesInterzisException("Documentul nu este accesibil încă.");
            }
        }

        return ragChatService.genereazaFlashcards(cursId, maxSaptamana, documentId, nrFlashcards);
    }

    private UserCurs verificaStudentActivInrolat(Long studentId, Long cursId) {
        UserCurs enrollment = userCursRepository.findByStudentIdAndCursId(studentId, cursId)
                .orElseThrow(() -> new AccesInterzisException("Nu aveți acces la acest curs."));

        if (!Boolean.TRUE.equals(enrollment.getActiv())) {
            throw new AccesInterzisException("Nu aveți o înrolare activă la acest curs.");
        }

        User student = enrollment.getStudent();
        if (student.getStareCont() == null || !"ACTIV".equalsIgnoreCase(student.getStareCont().getDenumire())) {
            throw new AccesInterzisException("Contul de student nu este activ.");
        }

        return enrollment;
    }
}
