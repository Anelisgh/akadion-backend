package com.example.akadion.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.akadion.dto.*;
import com.example.akadion.entity.*;
import com.example.akadion.exception.AccesInterzisException;
import com.example.akadion.exception.IncercareQuizFinalizataException;
import com.example.akadion.exception.RagChatException;
import com.example.akadion.exception.ResursaNegasitaException;
import com.example.akadion.exception.UserNotFoundException;
import com.example.akadion.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
            log.info("Înrolarea studentului {} la cursul {} a fost reactivată.", studentId, cursId);
        } else {
            UserCurs newEnrollment = UserCurs.builder()
                    .student(student)
                    .curs(curs)
                    .activ(true)
                    .build();
            userCursRepository.save(newEnrollment);
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
                    String urlVizualizare = buildDocumentPreviewUrl(doc);
                    String urlDescarcare = buildDocumentDownloadUrl(doc);
                    return new DocumentStudentResponseDto(
                            doc.getId(),
                            doc.getTitlu(),
                            urlVizualizare,
                            urlDescarcare
                    );
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public Document getAccessibleDocument(Long documentId, Long studentId) {
        Document document = documentRepository.findWithSaptamanaAndCursAndProfesorById(documentId)
                .orElseThrow(() -> new IllegalArgumentException("Documentul nu a fost găsit."));

        if (!Boolean.TRUE.equals(document.getActiv())) {
            throw new IllegalArgumentException("Documentul nu a fost găsit.");
        }

        Long cursId = document.getSaptamana().getCurs().getId();
        UserCurs enrollment = userCursRepository.findByStudentIdAndCursId(studentId, cursId)
                .orElseThrow(() -> new AccesInterzisException("Nu aveți acces la acest document."));

        if (!Boolean.TRUE.equals(enrollment.getActiv())) {
            throw new AccesInterzisException("Nu aveți o înrolare activă la acest curs.");
        }

        return document;
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

    private String buildDocumentPreviewUrl(Document document) {
        return "/api/documente/%d/preview/%s".formatted(document.getId(), encodedFilename(document));
    }

    private String buildDocumentDownloadUrl(Document document) {
        return "/api/documente/%d/download/%s".formatted(document.getId(), encodedFilename(document));
    }

    private String encodedFilename(Document document) {
        return UriUtils.encodePathSegment(minioStorageService.extractOriginalFilename(document.getPathMinio()), StandardCharsets.UTF_8);
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
        String dificultate = request != null && request.dificultate() != null && !request.dificultate().isBlank()
                ? request.dificultate().trim()
                : "MEDIU";

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

        List<Map<String, Object>> rawQuestions = ragChatService.genereazaQuiz(cursId, maxSaptamana, documentId, nrIntrebari, dificultate);
        if (rawQuestions == null || rawQuestions.isEmpty()) {
            throw new RagChatException("Serviciul RAG a returnat o listă vidă de întrebări.");
        }

        List<Map<String, Object>> storedQuestions = new ArrayList<>();
        List<QuizQuestionProjectionDto> projections = new ArrayList<>();

        for (int i = 0; i < rawQuestions.size(); i++) {
            Map<String, Object> sanitizedQuestion = sanitizeQuizQuestion(rawQuestions.get(i), i);
            storedQuestions.add(sanitizedQuestion);
            projections.add(new QuizQuestionProjectionDto(
                    readInteger(sanitizedQuestion.get("index"), i),
                    readString(sanitizedQuestion.get("intrebare")),
                    normalizeQuizOptions(sanitizedQuestion.get("optiuni"))
            ));
        }

        IncercareQuiz incercare = self.salveazaIncercareQuizGenerata(
                studentId,
                cursId,
                documentRef != null ? documentRef.getId() : null,
                storedQuestions
        );

        return new QuizGenerateResponseDto(incercare.getId(), projections);
    }

    @Transactional
    public IncercareQuiz salveazaIncercareQuizGenerata(Long studentId, Long cursId, Long documentId, List<Map<String, Object>> questions) {
        User student = userRepository.findById(studentId)
                .orElseThrow(() -> new UserNotFoundException(studentId));
        Curs curs = cursRepository.findById(cursId)
                .orElseThrow(() -> new IllegalArgumentException("Cursul nu a fost găsit."));
        Document document = documentId != null ? documentRepository.findById(documentId).orElse(null) : null;

        String detaliiJson;
        try {
            detaliiJson = objectMapper.writeValueAsString(questions);
        } catch (Exception e) {
            log.error("Eroare la serializarea detaliilor pentru încercarea de quiz", e);
            throw new IllegalStateException("Nu am putut salva detaliile quiz-ului.", e);
        }

        IncercareQuiz incercare = IncercareQuiz.builder()
                .student(student)
                .curs(curs)
                .document(document)
                .status(StatusIncercareQuiz.GENERATA)
                .nrIntrebari(questions.size())
                .detaliiJson(detaliiJson)
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

        Set<Integer> indecsiVazuti = new HashSet<>();
        Map<Integer, String> raspunsuriPeIndex = new HashMap<>();
        for (RaspunsIntrebareDto raspuns : raspunsuriTrimise) {
            Integer index = raspuns.index();
            if (index == null || index < 0 || index >= nrIntrebari) {
                throw new IllegalArgumentException("Index invalid de întrebare: " + index);
            }
            if (!indecsiVazuti.add(index)) {
                throw new IllegalArgumentException("Index duplicat trimis în răspunsuri: " + index);
            }
            raspunsuriPeIndex.put(index, raspuns.raspunsStudent());
        }

        List<Map<String, Object>> detaliiSalvate;
        try {
            detaliiSalvate = objectMapper.readValue(incercare.getDetaliiJson(), new TypeReference<List<Map<String, Object>>>() {});
        } catch (Exception e) {
            log.error("Eroare la deserializarea detaliilor quiz-ului {}", incercareId, e);
            throw new IllegalStateException("Detaliile quiz-ului sunt corupte.", e);
        }

        int scor = 0;
        List<QuizQuestionFeedbackDto> feedback = new ArrayList<>();

        for (int i = 0; i < detaliiSalvate.size(); i++) {
            Map<String, Object> intrebare = detaliiSalvate.get(i);
            Integer index = readInteger(intrebare.get("index"), i);
            Map<String, Object> optiuni = normalizeQuizOptions(intrebare.get("optiuni"));
            String raspunsCorect = readString(intrebare.get("raspuns_corect"));
            String raspunsStudent = raspunsuriPeIndex.get(index);
            boolean esteCorect = isQuizAnswerCorrect(raspunsStudent, raspunsCorect, optiuni);

            if (esteCorect) {
                scor++;
            }

            intrebare.put("index", index);
            intrebare.put("optiuni", optiuni);
            intrebare.put("raspuns_student", raspunsStudent);
            intrebare.put("este_corect", esteCorect);

            feedback.add(new QuizQuestionFeedbackDto(
                    index,
                    readString(intrebare.get("intrebare")),
                    optiuni,
                    raspunsStudent,
                    esteCorect,
                    raspunsCorect,
                    readString(intrebare.get("explicatie"))
            ));
        }

        try {
            incercare.setDetaliiJson(objectMapper.writeValueAsString(detaliiSalvate));
        } catch (Exception e) {
            throw new IllegalStateException("Nu am putut salva rezultatul final al quiz-ului.", e);
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
                feedback
        );
    }

    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<IncercareQuizSummaryDto> getIstoricQuizStudent(Long studentId, Long cursId, org.springframework.data.domain.Pageable pageable) {
        org.springframework.data.domain.Page<IncercareQuiz> page = cursId != null
                ? incercareQuizRepository.findByStudentIdAndStatusAndCursIdOrderByCreatedAtDesc(studentId, StatusIncercareQuiz.FINALIZATA, cursId, pageable)
                : incercareQuizRepository.findByStudentIdAndStatusOrderByCreatedAtDesc(studentId, StatusIncercareQuiz.FINALIZATA, pageable);

        return page.map(incercare -> {
            int nr = incercare.getNrIntrebari();
            int scor = incercare.getScor() != null ? incercare.getScor() : 0;
            double procentaj = nr > 0 ? Math.round(((double) scor / nr) * 10000.0) / 100.0 : 0.0;

            return new IncercareQuizSummaryDto(
                    incercare.getId(),
                    incercare.getCurs().getId(),
                    incercare.getCurs() != null ? incercare.getCurs().getDenumire() : null,
                    incercare.getDocument() != null ? incercare.getDocument().getId() : null,
                    incercare.getDocument() != null ? incercare.getDocument().getTitlu() : null,
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

        List<QuizQuestionFeedbackDto> feedback = new ArrayList<>();
        if (incercare.getDetaliiJson() != null) {
            try {
                List<Map<String, Object>> detalii = objectMapper.readValue(incercare.getDetaliiJson(), new TypeReference<List<Map<String, Object>>>() {});
                for (int i = 0; i < detalii.size(); i++) {
                    Map<String, Object> intrebare = detalii.get(i);
                    feedback.add(new QuizQuestionFeedbackDto(
                            readInteger(intrebare.get("index"), i),
                            readString(intrebare.get("intrebare")),
                            normalizeQuizOptions(intrebare.get("optiuni")),
                            readString(intrebare.get("raspuns_student")),
                            readBoolean(intrebare.get("este_corect")),
                            readString(intrebare.get("raspuns_corect")),
                            readString(intrebare.get("explicatie"))
                    ));
                }
            } catch (Exception e) {
                log.error("Eroare la parsarea detaliilor pentru încercarea de quiz {}", incercareId, e);
            }
        }

        int nr = incercare.getNrIntrebari();
        int scor = incercare.getScor() != null ? incercare.getScor() : 0;
        double procentaj = nr > 0 ? Math.round(((double) scor / nr) * 10000.0) / 100.0 : 0.0;

        return new IncercareQuizDetailDto(
                incercare.getId(),
                incercare.getCurs().getId(),
                incercare.getCurs() != null ? incercare.getCurs().getDenumire() : null,
                incercare.getDocument() != null ? incercare.getDocument().getId() : null,
                incercare.getDocument() != null ? incercare.getDocument().getTitlu() : null,
                scor,
                nr,
                procentaj,
                incercare.getStatus(),
                feedback,
                incercare.getCreatedAt(),
                incercare.getUpdatedAt()
        );
    }

    @Transactional
    public void stergeIncercareQuiz(Long studentId, Long incercareId) {
        IncercareQuiz incercare = incercareQuizRepository.findById(incercareId)
                .orElseThrow(() -> new ResursaNegasitaException("Încercarea de quiz nu a fost găsită."));

        if (!incercare.getStudent().getId().equals(studentId)) {
            throw new AccesInterzisException("Nu aveți permisiunea de a șterge această încercare de quiz.");
        }

        incercareQuizRepository.delete(incercare);
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

    private Map<String, Object> sanitizeQuizQuestion(Map<String, Object> rawQuestion, int index) {
        Map<String, Object> sanitized = new LinkedHashMap<>();
        sanitized.put("index", index);
        sanitized.put("intrebare", readString(rawQuestion != null ? rawQuestion.get("intrebare") : null));
        sanitized.put("optiuni", normalizeQuizOptions(rawQuestion != null ? rawQuestion.get("optiuni") : null));
        sanitized.put("raspuns_corect", readString(rawQuestion != null ? rawQuestion.get("raspuns_corect") : null));
        sanitized.put("explicatie", readString(rawQuestion != null ? rawQuestion.get("explicatie") : null));
        return sanitized;
    }

    private Map<String, Object> normalizeQuizOptions(Object rawOptions) {
        Map<String, Object> normalized = new LinkedHashMap<>();

        if (rawOptions instanceof Map<?, ?> rawMap) {
            for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
                normalized.put(String.valueOf(entry.getKey()), entry.getValue());
            }
            return normalized;
        }

        if (rawOptions instanceof List<?> rawList) {
            for (int i = 0; i < rawList.size(); i++) {
                normalized.put(String.valueOf((char) ('A' + i)), rawList.get(i));
            }
        }

        return normalized;
    }

    private boolean isQuizAnswerCorrect(String raspunsStudent, String raspunsCorect, Map<String, Object> optiuni) {
        String studentNormalizat = normalizeQuizValue(raspunsStudent);
        String corectNormalizat = normalizeQuizValue(raspunsCorect);
        if (studentNormalizat == null || corectNormalizat == null) {
            return false;
        }

        if (studentNormalizat.equals(corectNormalizat)) {
            return true;
        }

        Object valoareOptiune = optiuni.get(raspunsStudent);
        if (valoareOptiune != null && corectNormalizat.equals(normalizeQuizValue(valoareOptiune.toString()))) {
            return true;
        }

        for (Map.Entry<String, Object> entry : optiuni.entrySet()) {
            if (corectNormalizat.equals(normalizeQuizValue(entry.getValue() != null ? entry.getValue().toString() : null))) {
                return studentNormalizat.equals(normalizeQuizValue(entry.getKey()));
            }
        }

        return false;
    }

    private String normalizeQuizValue(String value) {
        if (value == null) {
            return null;
        }

        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed.toUpperCase();
    }

    private String readString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private Integer readInteger(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }

        if (value != null) {
            try {
                return Integer.parseInt(String.valueOf(value));
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }

        return fallback;
    }

    private Boolean readBoolean(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }

        if (value != null) {
            return Boolean.parseBoolean(String.valueOf(value));
        }

        return null;
    }
}
