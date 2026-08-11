package com.example.akadion.service;

import com.example.akadion.dto.*;
import com.example.akadion.entity.*;
import com.example.akadion.exception.*;
import com.example.akadion.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
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
    private final IncercareQuizRepository incercareQuizRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();


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

    @Transactional
    public QuizGenerateResponseDto genereazaQuiz(Long studentId, Long cursId, QuizGenerateRequestDto request) {

        checkRateLimit(studentId);
        int maxSaptamana = determinaSaptamanaParcursaMax(studentId, cursId);
        Long documentId = request != null ? request.documentId() : null;
        Integer nrIntrebari = request != null && request.nrIntrebari() != null ? request.nrIntrebari() : 5;

        if (nrIntrebari < 1 || nrIntrebari > 20) {
            throw new IllegalArgumentException("Numărul de întrebări trebuie să fie între 1 și 20.");
        }

        Document documentRef = null;
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
            documentRef = document;
        }

        List<Map<String, Object>> rawQuestions = ragChatService.genereazaQuiz(cursId, maxSaptamana, documentId, nrIntrebari);
        if (rawQuestions == null || rawQuestions.isEmpty()) {
            throw new RagChatException("Serviciul RAG a returnat o listă vidă de întrebări.");
        }

        List<Map<String, Object>> indexedQuestions = new java.util.ArrayList<>();
        List<QuizQuestionProjectionDto> projections = new java.util.ArrayList<>();

        for (int i = 0; i < rawQuestions.size(); i++) {
            Map<String, Object> raw = new java.util.HashMap<>(rawQuestions.get(i));
            raw.put("index", i);
            indexedQuestions.add(raw);

            String intrebare = (String) raw.get("intrebare");
            @SuppressWarnings("unchecked")
            Map<String, Object> optiuni = (Map<String, Object>) raw.get("optiuni");

            projections.add(new QuizQuestionProjectionDto(i, intrebare, optiuni));
        }

        IncercareQuiz incercare = self.salveazaIncercareQuizGenerata(studentId, cursId, documentRef != null ? documentRef.getId() : null, indexedQuestions);

        return new QuizGenerateResponseDto(incercare.getId(), projections);
    }

    @Transactional
    public IncercareQuiz salveazaIncercareQuizGenerata(Long studentId, Long cursId, Long documentId, List<Map<String, Object>> questions) {
        User student = userRepository.findById(studentId)
                .orElseThrow(() -> new UserNotFoundException(studentId));
        Curs curs = cursRepository.findById(cursId)
                .orElseThrow(() -> new IllegalArgumentException("Cursul nu a fost găsit."));
        Document document = documentId != null ? documentRepository.findById(documentId).orElse(null) : null;

        String detaliiJsonStr;
        try {
            detaliiJsonStr = objectMapper.writeValueAsString(questions);
        } catch (Exception e) {
            log.error("Eroare la serializarea JSON pentru detalii quiz", e);
            throw new IllegalArgumentException("Format incorect pentru detaliile quiz-ului.", e);
        }

        IncercareQuiz incercare = IncercareQuiz.builder()
                .student(student)
                .curs(curs)
                .document(document)
                .status(StatusIncercareQuiz.GENERATA)
                .nrIntrebari(questions.size())
                .detaliiJson(detaliiJsonStr)
                .build();

        return incercareQuizRepository.save(incercare);
    }

    @Transactional
    public QuizFinalizatResponseDto finalizeazaQuiz(Long studentId, Long incercareId, FinalizeazaQuizRequestDto request) {
        IncercareQuiz incercare = incercareQuizRepository.findByIdForUpdate(incercareId)
                .orElseThrow(() -> new ResursaNegasitaException("Încercarea de quiz nu a fost găsită."));

        if (!incercare.getStudent().getId().equals(studentId)) {
            throw new AccesInterzisException("Nu aveți acces la această încercare de quiz.");
        }

        determinaSaptamanaParcursaMax(studentId, incercare.getCurs().getId());

        if (incercare.getStatus() == StatusIncercareQuiz.FINALIZATA) {
            throw new IncercareQuizFinalizataException("Această încercare de quiz a fost deja finalizată.");
        }

        List<RaspunsIntrebareDto> raspunsuriTrimise = request != null && request.raspunsuri() != null ? request.raspunsuri() : List.of();
        int nrIntrebari = incercare.getNrIntrebari();

        if (raspunsuriTrimise.size() != nrIntrebari) {
            throw new IllegalArgumentException("Trebuie să trimiteți un număr de răspunsuri egal cu numărul de întrebări (" + nrIntrebari + ").");
        }

        java.util.Set<Integer> indecsiVazuti = new java.util.HashSet<>();
        Map<Integer, String> mapaRaspunsuri = new java.util.HashMap<>();
        for (RaspunsIntrebareDto r : raspunsuriTrimise) {
            if (r.index() == null || r.index() < 0 || r.index() >= nrIntrebari) {
                throw new IllegalArgumentException("Index invalid de întrebare: " + r.index());
            }
            if (!indecsiVazuti.add(r.index())) {
                throw new IllegalArgumentException("Index duplicat trimis în răspunsuri: " + r.index());
            }
            mapaRaspunsuri.put(r.index(), r.raspunsStudent());
        }

        List<Map<String, Object>> detaliiJsonList;
        try {
            detaliiJsonList = objectMapper.readValue(incercare.getDetaliiJson(), new com.fasterxml.jackson.core.type.TypeReference<List<Map<String, Object>>>() {});
        } catch (Exception e) {
            log.error("Eroare deserializare JSON la finalizare quiz", e);
            throw new IllegalStateException("Detaliile quiz-ului din baza de date sunt corupte.", e);
        }

        int scor = 0;
        List<QuizQuestionFeedbackDto> detaliiFeedback = new java.util.ArrayList<>();

        for (Map<String, Object> item : detaliiJsonList) {
            Integer index = (Integer) item.get("index");
            String intrebare = (String) item.get("intrebare");
            @SuppressWarnings("unchecked")
            Map<String, Object> optiuni = (Map<String, Object>) item.get("optiuni");
            String raspunsCorect = (String) item.get("raspuns_corect");
            String explicatie = (String) item.get("explicatie");

            String raspunsStudent = mapaRaspunsuri.get(index);
            boolean esteCorect = raspunsStudent != null && !raspunsStudent.isBlank() && raspunsStudent.trim().equalsIgnoreCase(raspunsCorect != null ? raspunsCorect.trim() : "");

            if (esteCorect) {
                scor++;
            }

            item.put("raspuns_student", raspunsStudent);
            item.put("este_corect", esteCorect);

            detaliiFeedback.add(new QuizQuestionFeedbackDto(
                    index,
                    intrebare,
                    optiuni,
                    raspunsStudent,
                    esteCorect,
                    raspunsCorect,
                    explicatie
            ));
        }

        try {
            incercare.setDetaliiJson(objectMapper.writeValueAsString(detaliiJsonList));
        } catch (Exception e) {
            throw new IllegalStateException("Eroare la serializarea noului JSON de detalii.", e);
        }

        incercare.setStatus(StatusIncercareQuiz.FINALIZATA);
        incercare.setScor(scor);
        incercareQuizRepository.save(incercare);

        double procentaj = nrIntrebari > 0 ? Math.round(((double) scor / nrIntrebari) * 10000.0) / 100.0 : 0.0;

        return new QuizFinalizatResponseDto(
                incercare.getId(),
                scor,
                nrIntrebari,
                procentaj,
                detaliiFeedback
        );
    }

    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<IncercareQuizSummaryDto> getIstoricQuizStudent(Long studentId, Long cursId, org.springframework.data.domain.Pageable pageable) {
        org.springframework.data.domain.Page<IncercareQuiz> page;
        if (cursId != null) {
            page = incercareQuizRepository.findByStudentIdAndStatusAndCursIdOrderByCreatedAtDesc(studentId, StatusIncercareQuiz.FINALIZATA, cursId, pageable);
        } else {
            page = incercareQuizRepository.findByStudentIdAndStatusOrderByCreatedAtDesc(studentId, StatusIncercareQuiz.FINALIZATA, pageable);
        }

        return page.map(incercare -> {
            int nr = incercare.getNrIntrebari();
            int scor = incercare.getScor() != null ? incercare.getScor() : 0;
            double procentaj = nr > 0 ? Math.round(((double) scor / nr) * 10000.0) / 100.0 : 0.0;
            String cursDenumire = incercare.getCurs() != null ? incercare.getCurs().getDenumire() : null;
            Long docId = incercare.getDocument() != null ? incercare.getDocument().getId() : null;
            String docTitlu = incercare.getDocument() != null ? incercare.getDocument().getTitlu() : null;

            return new IncercareQuizSummaryDto(
                    incercare.getId(),
                    incercare.getCurs().getId(),
                    cursDenumire,
                    docId,
                    docTitlu,
                    scor,
                    nr,
                    procentaj,
                    incercare.getCreatedAt()
            );
        });
    }

    @Transactional(readOnly = true)
    public IncercareQuizDetailDto getDetaliuQuizStudent(Long studentId, Long incercareId) {
        IncercareQuiz incercare = incercareQuizRepository.findById(incercareId)
                .orElseThrow(() -> new ResursaNegasitaException("Încercarea de quiz nu a fost găsită."));

        if (!incercare.getStudent().getId().equals(studentId)) {
            throw new AccesInterzisException("Nu aveți acces la această încercare de quiz.");
        }

        List<QuizQuestionFeedbackDto> detaliiFeedback = new java.util.ArrayList<>();
        if (incercare.getDetaliiJson() != null) {
            try {
                List<Map<String, Object>> list = objectMapper.readValue(incercare.getDetaliiJson(), new com.fasterxml.jackson.core.type.TypeReference<List<Map<String, Object>>>() {});
                for (Map<String, Object> item : list) {
                    Integer index = (Integer) item.get("index");
                    String intrebare = (String) item.get("intrebare");
                    @SuppressWarnings("unchecked")
                    Map<String, Object> optiuni = (Map<String, Object>) item.get("optiuni");
                    String raspunsStudent = (String) item.get("raspuns_student");
                    Boolean esteCorect = (Boolean) item.get("este_corect");
                    String raspunsCorect = (String) item.get("raspuns_corect");
                    String explicatie = (String) item.get("explicatie");

                    detaliiFeedback.add(new QuizQuestionFeedbackDto(
                            index, intrebare, optiuni, raspunsStudent, esteCorect, raspunsCorect, explicatie
                    ));
                }
            } catch (Exception e) {
                log.error("Eroare la parsarea JSON pentru detaliul quiz-ului", e);
            }
        }

        int nr = incercare.getNrIntrebari();
        int scor = incercare.getScor() != null ? incercare.getScor() : 0;
        double procentaj = nr > 0 ? Math.round(((double) scor / nr) * 10000.0) / 100.0 : 0.0;
        String cursDenumire = incercare.getCurs() != null ? incercare.getCurs().getDenumire() : null;
        Long docId = incercare.getDocument() != null ? incercare.getDocument().getId() : null;
        String docTitlu = incercare.getDocument() != null ? incercare.getDocument().getTitlu() : null;

        return new IncercareQuizDetailDto(
                incercare.getId(),
                incercare.getCurs().getId(),
                cursDenumire,
                docId,
                docTitlu,
                scor,
                nr,
                procentaj,
                incercare.getStatus(),
                detaliiFeedback,
                incercare.getCreatedAt(),
                incercare.getUpdatedAt()
        );
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
