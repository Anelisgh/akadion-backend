package com.example.akadion.quiz.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.akadion.quiz.dto.FinalizeazaQuizRequestDto;
import com.example.akadion.quiz.dto.IncercareQuizDetailDto;
import com.example.akadion.quiz.dto.IncercareQuizSummaryDto;
import com.example.akadion.quiz.dto.QuizFinalizatResponseDto;
import com.example.akadion.quiz.dto.QuizGenerateRequestDto;
import com.example.akadion.quiz.dto.QuizGenerateResponseDto;
import com.example.akadion.quiz.dto.QuizQuestionFeedbackDto;
import com.example.akadion.quiz.dto.QuizQuestionProjectionDto;
import com.example.akadion.quiz.dto.RaspunsIntrebareDto;
import com.example.akadion.curs.entity.Curs;
import com.example.akadion.curs.entity.Document;
import com.example.akadion.quiz.entity.IncercareQuiz;
import com.example.akadion.curs.entity.Saptamana;
import com.example.akadion.quiz.entity.IncercareQuizStatus;
import com.example.akadion.common.entity.User;
import com.example.akadion.exception.ForbiddenOperationException;
import com.example.akadion.exception.IncercareQuizActivaException;
import com.example.akadion.exception.IncercareQuizFinalizataException;
import com.example.akadion.exception.RagChatException;
import com.example.akadion.exception.ResursaNegasitaException;
import com.example.akadion.curs.repository.CursRepository;
import com.example.akadion.curs.repository.DocumentRepository;
import com.example.akadion.quiz.repository.IncercareQuizRepository;
import com.example.akadion.common.repository.UserRepository;
import com.example.akadion.akychat.service.RagChatService;
import com.example.akadion.curs.service.StudentCursService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class StudentQuizService {

    private static final String FIELD_INTREBARE = "intrebare";
    private static final String FIELD_INDEX = "index";
    private static final String FIELD_OPTIUNI = "optiuni";
    private static final String ERR_INCERCARE_NOT_FOUND = "Încercarea de quiz nu a fost găsită.";

    private final UserRepository userRepository;
    private final CursRepository cursRepository;
    private final DocumentRepository documentRepository;
    private final IncercareQuizRepository incercareQuizRepository;
    private final RagChatService ragChatService;
    private final StudentCursService studentCursService;
    private final QuizGradingService quizGradingService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // Field injection intenționată: un service nu se poate auto-injecta prin constructor
    // (dependință circulară) fără @Lazy, iar Lombok @RequiredArgsConstructor nu suportă @Lazy per-parametru.
    // Necesar pentru ca apelurile interne prin self.xxx() să treacă prin proxy-ul @Transactional.
    @SuppressWarnings("java:S6813")
    @Autowired
    @Lazy
    private StudentQuizService self;

    public QuizGenerateResponseDto genereazaQuiz(Long studentId, Long cursId, QuizGenerateRequestDto request) {
        studentCursService.verificaRateLimitAky(studentId);
        int maxSaptamana = studentCursService.determinaSaptamanaParcursaMax(studentId, cursId);
        Long documentId = request != null ? request.documentId() : null;
        Integer nrIntrebari = request != null && request.nrIntrebari() != null ? request.nrIntrebari() : 5;
        String dificultate = request != null && request.dificultate() != null && !request.dificultate().isBlank()
                ? request.dificultate().trim()
                : "MEDIU";

        Document documentRef = validateAndResolveDocument(documentId, cursId, maxSaptamana);

        List<Map<String, Object>> rawQuestions = ragChatService.genereazaQuiz(cursId, maxSaptamana, documentId, nrIntrebari, dificultate);
        if (rawQuestions == null || rawQuestions.isEmpty()) {
            throw new RagChatException("Serviciul RAG a returnat o listă vidă de întrebări.");
        }

        List<Map<String, Object>> storedQuestions = new ArrayList<>();
        List<QuizQuestionProjectionDto> projections = new ArrayList<>();

        for (int i = 0; i < rawQuestions.size(); i++) {
            Map<String, Object> sanitizedQuestion = quizGradingService.sanitizeQuizQuestion(rawQuestions.get(i), i);
            storedQuestions.add(sanitizedQuestion);
            projections.add(new QuizQuestionProjectionDto(
                    quizGradingService.readInteger(sanitizedQuestion.get(FIELD_INDEX), i),
                    quizGradingService.readString(sanitizedQuestion.get(FIELD_INTREBARE)),
                    quizGradingService.normalizeQuizOptions(sanitizedQuestion.get(FIELD_OPTIUNI))
            ));
        }

        IncercareQuiz incercare;
        try {
            incercare = self.salveazaIncercareQuizGenerata(
                    studentId,
                    cursId,
                    documentRef != null ? documentRef.getId() : null,
                    storedQuestions
            );
        } catch (DataIntegrityViolationException e) {
            throw new IncercareQuizActivaException("Aveți deja o încercare de quiz în desfășurare pentru acest curs. Finalizați-o înainte de a genera una nouă.");
        }

        return new QuizGenerateResponseDto(incercare.getId(), projections);
    }

    private Document validateAndResolveDocument(Long documentId, Long cursId, int maxSaptamana) {
        if (documentId == null) {
            return null;
        }

        Document document = documentRepository.findWithSaptamanaAndCursAndProfesorById(documentId)
                .orElseThrow(() -> new IllegalArgumentException("Documentul nu a fost găsit."));

        if (!document.isActiv()) {
            throw new ForbiddenOperationException("Documentul nu este activ.");
        }

        Saptamana saptamana = document.getSaptamana();
        if (saptamana == null || saptamana.getCurs() == null || !saptamana.getCurs().getId().equals(cursId)) {
            throw new ForbiddenOperationException("Documentul nu aparține acestui curs.");
        }

        Integer nrSaptamana = saptamana.getNrSaptamana();
        if (nrSaptamana == null || nrSaptamana > maxSaptamana) {
            throw new ForbiddenOperationException("Documentul nu este accesibil încă.");
        }

        return document;
    }

    @Transactional
    public IncercareQuiz salveazaIncercareQuizGenerata(Long studentId, Long cursId, Long documentId, List<Map<String, Object>> questions) {
        User student = userRepository.findById(studentId)
                .orElseThrow(() -> new ResursaNegasitaException("Utilizatorul cu id=" + studentId + " nu a fost gasit."));
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
                .status(IncercareQuizStatus.GENERATA)
                .nrIntrebari(questions.size())
                .detaliiJson(detaliiJson)
                .build();

        return incercareQuizRepository.save(incercare);
    }

    @Transactional
    public QuizFinalizatResponseDto finalizeazaQuiz(Long studentId, Long incercareId, FinalizeazaQuizRequestDto request) {
        IncercareQuiz incercare = incercareQuizRepository.findByIdForUpdate(incercareId)
                .orElseThrow(() -> new ResursaNegasitaException(ERR_INCERCARE_NOT_FOUND));

        if (!incercare.getStudent().getId().equals(studentId)) {
            throw new ForbiddenOperationException("Nu aveți acces la această încercare de quiz.");
        }

        studentCursService.determinaSaptamanaParcursaMax(studentId, incercare.getCurs().getId());

        if (incercare.getStatus() == IncercareQuizStatus.FINALIZATA) {
            throw new IncercareQuizFinalizataException("Această încercare de quiz a fost deja finalizată.");
        }

        List<RaspunsIntrebareDto> raspunsuriTrimise = request != null && request.raspunsuri() != null ? request.raspunsuri() : List.of();
        int nrIntrebari = incercare.getNrIntrebari();
        if (raspunsuriTrimise.size() != nrIntrebari) {
            throw new IllegalArgumentException("Trebuie să trimiteți un număr de răspunsuri egal cu numărul de întrebări (" + nrIntrebari + ").");
        }

        Map<Integer, String> raspunsuriPeIndex = mapeazaRaspunsuriPeIndex(raspunsuriTrimise, nrIntrebari);
        List<Map<String, Object>> detaliiSalvate = deserializeazaDetalii(incercare.getDetaliiJson(), incercareId);
        ScoringResult rezultat = calculeazaScorSiFeedback(detaliiSalvate, raspunsuriPeIndex);
        serializeazaDetaliiActualizate(incercare, rezultat.detaliiActualizate());

        incercare.setStatus(IncercareQuizStatus.FINALIZATA);
        incercare.setScor(rezultat.scor());
        incercareQuizRepository.save(incercare);

        double procentaj = nrIntrebari > 0 ? Math.round(((double) rezultat.scor() / nrIntrebari) * 10000.0) / 100.0 : 0.0;

        return new QuizFinalizatResponseDto(
                incercare.getId(),
                rezultat.scor(),
                nrIntrebari,
                procentaj,
                rezultat.feedback()
        );
    }

    private Map<Integer, String> mapeazaRaspunsuriPeIndex(List<RaspunsIntrebareDto> raspunsuriTrimise, int nrIntrebari) {
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
        return raspunsuriPeIndex;
    }

    private List<Map<String, Object>> deserializeazaDetalii(String detaliiJson, Long incercareId) {
        try {
            return objectMapper.readValue(detaliiJson, new TypeReference<List<Map<String, Object>>>() {});
        } catch (Exception e) {
            log.error("Eroare la deserializarea detaliilor quiz-ului {}", incercareId, e);
            throw new IllegalStateException("Detaliile quiz-ului sunt corupte.", e);
        }
    }

    private record ScoringResult(int scor, List<Map<String, Object>> detaliiActualizate, List<QuizQuestionFeedbackDto> feedback) {
    }

    private ScoringResult calculeazaScorSiFeedback(List<Map<String, Object>> detaliiSalvate, Map<Integer, String> raspunsuriPeIndex) {
        int scor = 0;
        List<QuizQuestionFeedbackDto> feedback = new ArrayList<>();

        for (int i = 0; i < detaliiSalvate.size(); i++) {
            Map<String, Object> intrebare = detaliiSalvate.get(i);
            Integer index = quizGradingService.readInteger(intrebare.get(FIELD_INDEX), i);
            Map<String, Object> optiuni = quizGradingService.normalizeQuizOptions(intrebare.get(FIELD_OPTIUNI));
            String raspunsCorect = quizGradingService.readString(intrebare.get("raspuns_corect"));
            String raspunsStudent = raspunsuriPeIndex.get(index);
            boolean esteCorect = quizGradingService.isQuizAnswerCorrect(raspunsStudent, raspunsCorect, optiuni);

            if (esteCorect) {
                scor++;
            }

            intrebare.put(FIELD_INDEX, index);
            intrebare.put(FIELD_OPTIUNI, optiuni);
            intrebare.put("raspuns_student", raspunsStudent);
            intrebare.put("este_corect", esteCorect);

            feedback.add(new QuizQuestionFeedbackDto(
                    index,
                    quizGradingService.readString(intrebare.get(FIELD_INTREBARE)),
                    optiuni,
                    raspunsStudent,
                    esteCorect,
                    raspunsCorect,
                    quizGradingService.readString(intrebare.get("explicatie"))
            ));
        }

        return new ScoringResult(scor, detaliiSalvate, feedback);
    }

    private void serializeazaDetaliiActualizate(IncercareQuiz incercare, List<Map<String, Object>> detaliiActualizate) {
        try {
            incercare.setDetaliiJson(objectMapper.writeValueAsString(detaliiActualizate));
        } catch (Exception e) {
            throw new IllegalStateException("Nu am putut salva rezultatul final al quiz-ului.", e);
        }
    }

    @Transactional(readOnly = true)
    public Page<IncercareQuizSummaryDto> getIstoricQuizStudent(Long studentId, Long cursId, Pageable pageable) {
        Page<IncercareQuiz> page = cursId != null
                ? incercareQuizRepository.findByStudentIdAndStatusAndCursIdOrderByCreatedAtDesc(studentId, IncercareQuizStatus.FINALIZATA, cursId, pageable)
                : incercareQuizRepository.findByStudentIdAndStatusOrderByCreatedAtDesc(studentId, IncercareQuizStatus.FINALIZATA, pageable);

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
                .orElseThrow(() -> new ResursaNegasitaException(ERR_INCERCARE_NOT_FOUND));

        if (!incercare.getStudent().getId().equals(studentId)) {
            throw new ForbiddenOperationException("Nu aveți acces la această încercare de quiz.");
        }

        List<QuizQuestionFeedbackDto> feedback = new ArrayList<>();
        if (incercare.getDetaliiJson() != null) {
            try {
                List<Map<String, Object>> detalii = objectMapper.readValue(incercare.getDetaliiJson(), new TypeReference<List<Map<String, Object>>>() {});
                for (int i = 0; i < detalii.size(); i++) {
                    Map<String, Object> intrebare = detalii.get(i);
                    feedback.add(new QuizQuestionFeedbackDto(
                            quizGradingService.readInteger(intrebare.get(FIELD_INDEX), i),
                            quizGradingService.readString(intrebare.get(FIELD_INTREBARE)),
                            quizGradingService.normalizeQuizOptions(intrebare.get(FIELD_OPTIUNI)),
                            quizGradingService.readString(intrebare.get("raspuns_student")),
                            quizGradingService.readBoolean(intrebare.get("este_corect")),
                            quizGradingService.readString(intrebare.get("raspuns_corect")),
                            quizGradingService.readString(intrebare.get("explicatie"))
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
                .orElseThrow(() -> new ResursaNegasitaException(ERR_INCERCARE_NOT_FOUND));

        if (!incercare.getStudent().getId().equals(studentId)) {
            throw new ForbiddenOperationException("Nu aveți permisiunea de a șterge această încercare de quiz.");
        }

        incercareQuizRepository.delete(incercare);
    }
}
