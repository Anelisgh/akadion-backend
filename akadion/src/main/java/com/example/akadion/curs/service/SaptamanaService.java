package com.example.akadion.curs.service;

import com.example.akadion.admin.entity.NumeTabelAudit;
import com.example.akadion.admin.entity.OperatieAudit;
import com.example.akadion.admin.service.AuditLogService;
import com.example.akadion.curs.dto.SaptamanaRequestDto;
import com.example.akadion.curs.dto.SaptamanaResponseDto;
import com.example.akadion.curs.entity.Curs;
import com.example.akadion.curs.entity.Document;
import com.example.akadion.curs.entity.Saptamana;
import com.example.akadion.exception.SaptamanaConcurentaException;
import com.example.akadion.curs.repository.CursRepository;
import com.example.akadion.curs.repository.DocumentRepository;
import com.example.akadion.curs.repository.ParcursRepository;
import com.example.akadion.curs.repository.SaptamanaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class SaptamanaService {

    private static final String DESCRIERE_KEY = "descriere";

    private final SaptamanaRepository saptamanaRepository;
    private final CursRepository cursRepository;
    private final DocumentRepository documentRepository;
    private final ParcursRepository parcursRepository;
    private final CursService cursService;
    private final MinioStorageService minioStorageService;
    private final RagIngestService ragIngestService;
    private final AuditLogService auditLogService;
    private final CursOwnershipValidator cursOwnershipValidator;

    // Field injection intenționată: un service nu se poate auto-injecta prin constructor
    // (dependință circulară) fără @Lazy, iar Lombok @RequiredArgsConstructor nu suportă @Lazy per-parametru.
    // Necesar pentru ca apelurile interne prin self.xxx() să treacă prin proxy-ul @Transactional.
    @SuppressWarnings("java:S6813")
    @Autowired
    @Lazy
    private SaptamanaService self;

    public List<SaptamanaResponseDto> listaSaptamani(Long cursId, Long callerId, String callerRole) {
        Curs curs = cursRepository.findById(cursId)
                .orElseThrow(() -> new IllegalArgumentException("Cursul cu ID-ul " + cursId + " nu a fost găsit."));

        cursOwnershipValidator.verificaProprietarSauAdmin(curs, callerId, callerRole, "Nu aveți acces la săptămânile acestui curs.");

        return saptamanaRepository.findByCursIdOrderByNrSaptamana(cursId).stream()
                .map(this::toResponseDto)
                .toList();
    }

    @Transactional
    public SaptamanaResponseDto adaugaSaptamana(Long cursId, Long profesorId, SaptamanaRequestDto dto) {
        Curs curs = cursRepository.findById(cursId)
                .orElseThrow(() -> new IllegalArgumentException("Cursul cu ID-ul " + cursId + " nu a fost găsit."));

        cursOwnershipValidator.verificaProprietar(curs, profesorId, "Nu aveți permisiunea de a adăuga o săptămână la acest curs.");

        Saptamana topSaptamana = saptamanaRepository.findTopByCursIdOrderByNrSaptamanaDesc(cursId).orElse(null);
        int nrSaptamana = topSaptamana != null ? topSaptamana.getNrSaptamana() + 1 : 1;

        Saptamana saptamana = Saptamana.builder()
                .curs(curs)
                .nrSaptamana(nrSaptamana)
                .descriere(dto.descriere())
                .build();

        try {
            saptamana = saptamanaRepository.saveAndFlush(saptamana);
        } catch (DataIntegrityViolationException e) {
            log.warn("Tentativă de adăugare concurentă a săptămânii {} la cursul {}", nrSaptamana, cursId);
            throw new SaptamanaConcurentaException("Această săptămână a fost adăugată concurent.");
        }

        cursService.recalculeazaDataSfarsit(curs);
        cursRepository.save(curs);

        auditLogService.inregistreaza(
                NumeTabelAudit.SAPTAMANA,
                saptamana.getId(),
                OperatieAudit.CREARE,
                null,
                Map.of("nrSaptamana", nrSaptamana, DESCRIERE_KEY, saptamana.getDescriere())
        );

        log.info("Săptămână adăugată: saptamanaId={}, nrSaptamana={}, la cursId={}", 
                saptamana.getId(), nrSaptamana, cursId);
        return toResponseDto(saptamana);
    }

    @Transactional
    public SaptamanaResponseDto modificaSaptamana(Long saptamanaId, Long profesorId, SaptamanaRequestDto dto) {
        Saptamana saptamana = saptamanaRepository.findWithCursAndProfesorById(saptamanaId)
                .orElseThrow(() -> new IllegalArgumentException("Săptămâna nu a fost găsită."));

        cursOwnershipValidator.verificaProprietar(saptamana.getCurs(), profesorId, "Nu aveți permisiunea de a modifica această săptămână.");

        String oldDescriere = saptamana.getDescriere();

        saptamana.setDescriere(dto.descriere());
        Saptamana savedSaptamana = saptamanaRepository.save(saptamana);

        auditLogService.inregistreaza(
                NumeTabelAudit.SAPTAMANA,
                saptamanaId,
                OperatieAudit.EDITARE,
                Map.of(DESCRIERE_KEY, oldDescriere == null ? "" : oldDescriere),
                Map.of(DESCRIERE_KEY, savedSaptamana.getDescriere() == null ? "" : savedSaptamana.getDescriere())
        );
        log.info("Săptămână modificată: saptamanaId={}", saptamanaId);
        return toResponseDto(savedSaptamana);
    }

    public void stergeUltimaSaptamana(Long saptamanaId, Long profesorId) {
        Saptamana saptamana = saptamanaRepository.findWithCursAndProfesorById(saptamanaId)
                .orElseThrow(() -> new IllegalArgumentException("Săptămâna nu a fost găsită."));

        Curs curs = saptamana.getCurs();
        cursOwnershipValidator.verificaProprietar(curs, profesorId, "Nu aveți permisiunea de a șterge această săptămână.");

        Saptamana topSaptamana = saptamanaRepository.findTopByCursIdOrderByNrSaptamanaDesc(curs.getId())
                .orElseThrow(() -> new IllegalStateException("Nu s-a găsit nicio săptămână pentru acest curs."));

        if (!saptamana.getNrSaptamana().equals(topSaptamana.getNrSaptamana())) {
            throw new IllegalArgumentException("Doar ultima săptămână a cursului poate fi ștearsă.");
        }

        // Colectează cheile MinIO și ID-urile documentelor
        List<Document> documente = documentRepository.findBySaptamanaId(saptamanaId);
        List<String> cheiMinio = documente.stream().map(Document::getPathMinio).toList();
        List<Long> idDocumente = documente.stream().map(Document::getId).toList();

        // DB first — tranzacție proprie, scurtă
        self.stergeSaptamanaSiAuditeaza(saptamana, curs, documente);

        // După commit (best-effort, în afara tranzacției DB — MinIO/RAG sunt apeluri de rețea)
        minioStorageService.deleteFiles(cheiMinio);
        for (Long docId : idDocumente) {
            ragIngestService.stergeDinIngest(docId);
        }

        log.info("Săptămână ștearsă (ultima): saptamanaId={}, nrSaptamana={}, la cursId={}",
                saptamanaId, saptamana.getNrSaptamana(), curs.getId());
    }

    @Transactional
    public void stergeSaptamanaSiAuditeaza(Saptamana saptamana, Curs curs, List<Document> documente) {
        parcursRepository.deleteAll(parcursRepository.findBySaptamanaId(saptamana.getId()));
        documentRepository.deleteAll(documente);
        saptamanaRepository.delete(saptamana);

        cursService.recalculeazaDataSfarsit(curs);
        cursRepository.save(curs);

        auditLogService.inregistreaza(
                NumeTabelAudit.SAPTAMANA,
                saptamana.getId(),
                OperatieAudit.STERGERE,
                Map.of("nrSaptamana", saptamana.getNrSaptamana(), DESCRIERE_KEY, saptamana.getDescriere(), "nrDocumenteAsociate", documente.size()),
                null
        );
    }

    private SaptamanaResponseDto toResponseDto(Saptamana saptamana) {
        return new SaptamanaResponseDto(
                saptamana.getId(),
                saptamana.getNrSaptamana(),
                saptamana.getDescriere()
        );
    }
}
