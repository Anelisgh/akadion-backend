package com.example.akadion.akychat.service;

import com.example.akadion.akychat.dto.AkyChatRequestDto;
import com.example.akadion.akychat.dto.AkyChatResponseDto;
import com.example.akadion.akychat.dto.AkyMessageDto;
import com.example.akadion.akychat.dto.ConversatieDto;
import com.example.akadion.akychat.dto.ConversatiiPaginateDto;
import com.example.akadion.akychat.dto.IstoricMesajeDto;
import com.example.akadion.akychat.dto.MesajChatDto;
import com.example.akadion.akychat.entity.Conversatie;
import com.example.akadion.curs.entity.Curs;
import com.example.akadion.akychat.entity.MesajChat;
import com.example.akadion.common.entity.NumeRol;
import com.example.akadion.akychat.entity.RolMesaj;
import com.example.akadion.common.entity.User;
import com.example.akadion.exception.ForbiddenOperationException;
import com.example.akadion.exception.ResursaNegasitaException;
import com.example.akadion.akychat.repository.ConversatieRepository;
import com.example.akadion.curs.repository.CursRepository;
import com.example.akadion.akychat.repository.MesajChatRepository;
import com.example.akadion.curs.repository.UserCursRepository;
import com.example.akadion.common.repository.UserRepository;
import com.example.akadion.auth.service.RateLimiterService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConversatieService {

    private final ConversatieRepository conversatieRepository;
    private final MesajChatRepository mesajChatRepository;
    private final UserRepository userRepository;
    private final CursRepository cursRepository;
    private final UserCursRepository userCursRepository;
    private final RagChatService ragChatService;
    private final RateLimiterService rateLimiterService;

    private static final int MAX_MESSAGES_PER_MINUTE = 10;
    private static final String ERR_UTILIZATOR_NOT_FOUND = "Utilizatorul nu a fost găsit.";
    private static final String ERR_CONVERSATIE_NOT_FOUND = "Conversația nu a fost găsită.";

    @Transactional
    public MesajChat salveazaIntrebare(Long conversatieId, Long userId, Long cursIdParam, String intrebare) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResursaNegasitaException(ERR_UTILIZATOR_NOT_FOUND));
        
        Conversatie conversatie;
        Curs curs;

        if (conversatieId == null) {
            // Conversatie noua
            curs = cursRepository.findById(cursIdParam)
                    .orElseThrow(() -> new ResursaNegasitaException("Cursul nu a fost găsit."));
            verificaAcces(user, curs, true);
            verificaRateLimit(userId);

            conversatie = new Conversatie();
            conversatie.setUser(user);
            conversatie.setCurs(curs);
            
            // Titlu generat automat din primul mesaj (maxim 40 caractere)
            String titlu = intrebare.replaceAll("\\s+", " ").trim();
            if (titlu.length() > 40) {
                titlu = titlu.substring(0, 37) + "...";
            }
            conversatie.setTitlu(titlu);
            conversatie = conversatieRepository.save(conversatie);
        } else {
            // Conversatie existenta
            conversatie = conversatieRepository.findById(conversatieId)
                    .orElseThrow(() -> new ResursaNegasitaException(ERR_CONVERSATIE_NOT_FOUND));
            
            if (!conversatie.getUser().getId().equals(userId)) {
                throw new ForbiddenOperationException("Nu sunteți proprietarul acestei conversații.");
            }
            if (!conversatie.isActiv()) {
                throw new IllegalArgumentException("Conversația a fost ștearsă.");
            }
            
            curs = conversatie.getCurs();
            verificaAcces(user, curs, true);
            verificaRateLimit(userId);
            
            conversatie.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
            conversatieRepository.save(conversatie);
        }

        MesajChat mesaj = new MesajChat();
        mesaj.setConversatie(conversatie);
        mesaj.setRol(RolMesaj.UTILIZATOR);
        mesaj.setContinut(intrebare);
        return mesajChatRepository.save(mesaj);
    }

    public AkyChatResponseDto obtineRaspunsRag(Long conversatieId, Long userId, String intrebare) {
        Conversatie conversatie = conversatieRepository.findById(conversatieId)
                .orElseThrow(() -> new ResursaNegasitaException(ERR_CONVERSATIE_NOT_FOUND));

        List<MesajChat> last10 = mesajChatRepository.findTop10ByConversatieIdOrderByCreatedAtDesc(conversatieId);
        // Excludem intrebarea curenta din istoric (e in ultimul, care tocmai a fost salvat)
        // RAG are nevoie de istoric STRICT FARA intrebarea curenta pe care o punem acum.
        // Daca intrebarea curenta e in DB, e prima din lista DESC.
        List<MesajChat> istoricPtRag = new ArrayList<>(last10);
        if (!istoricPtRag.isEmpty() && istoricPtRag.get(0).getContinut().equals(intrebare) && istoricPtRag.get(0).getRol() == RolMesaj.UTILIZATOR) {
            istoricPtRag.remove(0);
        }

        List<AkyMessageDto> istoricMapped = istoricPtRag.reversed().stream()
                .map(m -> new AkyMessageDto(
                        m.getRol() == RolMesaj.UTILIZATOR ? "user" : "assistant",
                        m.getContinut()
                ))
                .toList();

        AkyChatRequestDto requestDto = new AkyChatRequestDto(intrebare, istoricMapped);
        return ragChatService.intreabaAky(userId, conversatie.getCurs().getId(), requestDto);
    }

    @Transactional
    public MesajChat salveazaRaspuns(Long mesajUtilizatorId, AkyChatResponseDto raspuns) {
        MesajChat mesajUtilizator = mesajChatRepository.findById(mesajUtilizatorId)
                .orElseThrow(() -> new ResursaNegasitaException("Mesajul utilizatorului nu a fost găsit."));
        
        mesajUtilizator.setAreRaspuns(true);
        mesajChatRepository.save(mesajUtilizator);

        Conversatie conversatie = mesajUtilizator.getConversatie();
        conversatie.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        conversatieRepository.save(conversatie);

        String surseCsv = null;
        if (raspuns.surseFolosite() != null && !raspuns.surseFolosite().isEmpty()) {
            surseCsv = raspuns.surseFolosite().stream()
                    .filter(s -> s.documentId() != null)
                    .map(s -> s.documentId().toString() + "|" + (s.numeFisier() != null ? s.numeFisier().replace(",", "").replace("|", "") : "Document"))
                    .collect(Collectors.joining(","));
        }

        MesajChat mesaj = new MesajChat();
        mesaj.setConversatie(conversatie);
        mesaj.setRol(RolMesaj.ASISTENT);
        mesaj.setContinut(raspuns.raspuns());
        mesaj.setSurseFolosite(surseCsv);
        mesaj.setAreRaspuns(true); // Mesajul asistentului este automat "răspuns" implicit

        return mesajChatRepository.save(mesaj);
    }

    public AkyChatResponseDto retryMesaj(Long mesajId, Long userId) {
        MesajChat mesaj = mesajChatRepository.findById(mesajId)
                .orElseThrow(() -> new ResursaNegasitaException("Mesajul nu a fost găsit."));

        Conversatie conversatie = mesaj.getConversatie();

        if (!conversatie.getUser().getId().equals(userId)) {
            throw new ForbiddenOperationException("Nu sunteți proprietarul acestei conversații.");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResursaNegasitaException(ERR_UTILIZATOR_NOT_FOUND));

        // Verificam acces curs (scriere)
        verificaAcces(user, conversatie.getCurs(), true);
        
        // Verificam rate limit pe user
        verificaRateLimit(userId);

        // Apelam RAG. Refolosim logica de extragere a ultimelor mesaje (pentru context) 
        // pe baza conversatiei curente.
        return obtineRaspunsRag(conversatie.getId(), userId, mesaj.getContinut());
    }

    private void verificaAcces(User user, Curs curs, boolean scriere) {
        String rol = user.getRolDenumire();
        boolean acces;
        if (NumeRol.PROFESOR.name().equals(rol)) {
            acces = (curs.getProfesor() != null && curs.getProfesor().getId().equals(user.getId())) || curs.isActiv();
        } else if (NumeRol.STUDENT.name().equals(rol)) {
            acces = scriere
                    ? userCursRepository.existsByStudentIdAndCursIdAndActivTrue(user.getId(), curs.getId())
                    : userCursRepository.existsByStudentIdAndCursId(user.getId(), curs.getId());
        } else {
            acces = false;
        }
        if (!acces) throw new ForbiddenOperationException("Nu aveți acces la acest curs.");
    }

    private void verificaRateLimit(Long userId) {
        rateLimiterService.verificaLimita("conversatie:" + userId, MAX_MESSAGES_PER_MINUTE, Duration.ofMinutes(1));
    }

    @Transactional(readOnly = true)
    public ConversatiiPaginateDto obtineConversatiiActive(Long userId, Long cursId, int page, int size) {
        Curs curs = cursRepository.findById(cursId)
                .orElseThrow(() -> new ResursaNegasitaException("Cursul nu a fost găsit."));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResursaNegasitaException(ERR_UTILIZATOR_NOT_FOUND));
        
        verificaAcces(user, curs, false);
        Pageable pageable = PageRequest.of(page, size);
        Slice<Conversatie> slice = conversatieRepository.findByUserIdAndCursIdAndActivTrueOrderByUpdatedAtDesc(userId, cursId, pageable);
        List<ConversatieDto> dtos = slice.getContent().stream()
                .map(c -> new ConversatieDto(c.getId(), c.getCurs().getId(), c.getTitlu(), c.getCreatedAt()))
                .toList();
        return new ConversatiiPaginateDto(dtos, slice.hasNext());
    }

    @Transactional(readOnly = true)
    public IstoricMesajeDto obtineIstoric(Long userId, Long conversatieId, Long inainteDe, int limit) {
        Conversatie conversatie = conversatieRepository.findById(conversatieId)
                .orElseThrow(() -> new ResursaNegasitaException(ERR_CONVERSATIE_NOT_FOUND));
        if (!conversatie.getUser().getId().equals(userId)) {
            throw new ForbiddenOperationException("Nu sunteți proprietarul acestei conversații.");
        }

        Long cursor = (inainteDe != null) ? inainteDe : Long.MAX_VALUE;
        Pageable pageable = PageRequest.of(0, limit + 1);
        List<MesajChat> rawBatch = mesajChatRepository.findByConversatieIdAndIdLessThanOrderByIdDesc(conversatieId, cursor, pageable);

        boolean areMaiMulte = rawBatch.size() > limit;
        List<MesajChat> resultList = new ArrayList<>(areMaiMulte ? rawBatch.subList(0, limit) : rawBatch).reversed();

        Long celMaiVechiIdIncarcat = resultList.isEmpty() ? null : resultList.get(0).getId();

        List<MesajChatDto> dtos = resultList.stream()
                .map(m -> new MesajChatDto(m.getId(), m.getRol(), m.getContinut(), m.getSurseFolosite(), m.getCreatedAt(), m.isAreRaspuns()))
                .toList();

        return new IstoricMesajeDto(dtos, areMaiMulte, celMaiVechiIdIncarcat);
    }

    @Transactional(readOnly = true)
    public ConversatiiPaginateDto obtineToateConversatiileActive(Long userId, int page, int size) {
        if (!userRepository.existsById(userId)) {
            throw new ResursaNegasitaException(ERR_UTILIZATOR_NOT_FOUND);
        }
        Pageable pageable = PageRequest.of(page, size);
        Slice<Conversatie> slice = conversatieRepository.findByUserIdAndActivTrueOrderByUpdatedAtDesc(userId, pageable);
        List<ConversatieDto> dtos = slice.getContent().stream()
                .map(c -> new ConversatieDto(c.getId(), c.getCurs().getId(), c.getTitlu(), c.getCreatedAt()))
                .toList();
        return new ConversatiiPaginateDto(dtos, slice.hasNext());
    }

    @Transactional
    public void stergeConversatie(Long userId, Long conversatieId) {
        Conversatie conversatie = conversatieRepository.findById(conversatieId)
                .orElseThrow(() -> new ResursaNegasitaException(ERR_CONVERSATIE_NOT_FOUND));
        if (!conversatie.getUser().getId().equals(userId)) {
            throw new ForbiddenOperationException("Nu sunteți proprietarul acestei conversații.");
        }
        conversatie.setActiv(false);
        conversatieRepository.save(conversatie);
    }
}
