package com.example.akadion.service;

import com.example.akadion.dto.SaptamanaRequestDto;
import com.example.akadion.dto.SaptamanaResponseDto;
import com.example.akadion.entity.Curs;
import com.example.akadion.entity.Document;
import com.example.akadion.entity.Saptamana;
import com.example.akadion.exception.AccesInterzisException;
import com.example.akadion.exception.SaptamanaConcurentaException;
import com.example.akadion.repository.CursRepository;
import com.example.akadion.repository.DocumentRepository;
import com.example.akadion.repository.ParcursRepository;
import com.example.akadion.repository.SaptamanaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SaptamanaService {

    private final SaptamanaRepository saptamanaRepository;
    private final CursRepository cursRepository;
    private final DocumentRepository documentRepository;
    private final ParcursRepository parcursRepository;
    private final CursService cursService;
    private final MinioStorageService minioStorageService;
    private final RagIngestService ragIngestService;

    public List<SaptamanaResponseDto> listaSaptamani(Long cursId, Long callerId, String callerRole) {
        Curs curs = cursRepository.findById(cursId)
                .orElseThrow(() -> new IllegalArgumentException("Cursul cu ID-ul " + cursId + " nu a fost găsit."));

        if (!"ADMIN".equals(callerRole)) {
            if (!curs.getProfesor().getId().equals(callerId)) {
                throw new AccesInterzisException("Nu aveți acces la săptămânile acestui curs.");
            }
        }

        return saptamanaRepository.findByCursIdOrderByNrSaptamana(cursId).stream()
                .map(this::toResponseDto)
                .toList();
    }

    @Transactional
    public SaptamanaResponseDto adaugaSaptamana(Long cursId, Long profesorId, SaptamanaRequestDto dto) {
        Curs curs = cursRepository.findById(cursId)
                .orElseThrow(() -> new IllegalArgumentException("Cursul cu ID-ul " + cursId + " nu a fost găsit."));

        if (!curs.getProfesor().getId().equals(profesorId)) {
            throw new AccesInterzisException("Nu aveți permisiunea de a adăuga o săptămână la acest curs.");
        }

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

        log.info("Săptămână adăugată: saptamanaId={}, nrSaptamana={}, la cursId={}", 
                saptamana.getId(), nrSaptamana, cursId);
        return toResponseDto(saptamana);
    }

    @Transactional
    public SaptamanaResponseDto modificaSaptamana(Long saptamanaId, Long profesorId, SaptamanaRequestDto dto) {
        Saptamana saptamana = saptamanaRepository.findWithCursAndProfesorById(saptamanaId)
                .orElseThrow(() -> new IllegalArgumentException("Săptămâna nu a fost găsită."));

        if (!saptamana.getCurs().getProfesor().getId().equals(profesorId)) {
            throw new AccesInterzisException("Nu aveți permisiunea de a modifica această săptămână.");
        }

        saptamana.setDescriere(dto.descriere());
        Saptamana savedSaptamana = saptamanaRepository.save(saptamana);
        log.info("Săptămână modificată: saptamanaId={}", saptamanaId);
        return toResponseDto(savedSaptamana);
    }

    @Transactional
    public void stergeUltimaSaptamana(Long saptamanaId, Long profesorId) {
        Saptamana saptamana = saptamanaRepository.findWithCursAndProfesorById(saptamanaId)
                .orElseThrow(() -> new IllegalArgumentException("Săptămâna nu a fost găsită."));

        Curs curs = saptamana.getCurs();
        if (!curs.getProfesor().getId().equals(profesorId)) {
            throw new AccesInterzisException("Nu aveți permisiunea de a șterge această săptămână.");
        }

        Saptamana topSaptamana = saptamanaRepository.findTopByCursIdOrderByNrSaptamanaDesc(curs.getId())
                .orElseThrow(() -> new IllegalStateException("Nu s-a găsit nicio săptămână pentru acest curs."));

        if (!saptamana.getNrSaptamana().equals(topSaptamana.getNrSaptamana())) {
            throw new IllegalArgumentException("Doar ultima săptămână a cursului poate fi ștearsă.");
        }

        // 3. Colectează cheile MinIO și ID-urile documentelor
        List<Document> documente = documentRepository.findAllBySaptamanaId(saptamanaId);
        List<String> cheiMinio = documente.stream().map(Document::getPathMinio).toList();
        List<Long> idDocumente = documente.stream().map(Document::getId).toList();

        // 4. DB first
        parcursRepository.deleteAll(parcursRepository.findBySaptamanaId(saptamanaId));
        documentRepository.deleteAll(documente);
        saptamanaRepository.delete(saptamana);

        cursService.recalculeazaDataSfarsit(curs);
        cursRepository.save(curs);

        // 5. După commit/local delete (best-effort)
        minioStorageService.deleteFiles(cheiMinio);

        // 6. După MinIO (best-effort)
        for (Long docId : idDocumente) {
            ragIngestService.stergeDinIngest(docId);
        }
        log.info("Săptămână ștearsă (ultima): saptamanaId={}, nrSaptamana={}, la cursId={}", 
                saptamanaId, saptamana.getNrSaptamana(), curs.getId());
    }

    private SaptamanaResponseDto toResponseDto(Saptamana saptamana) {
        return new SaptamanaResponseDto(
                saptamana.getId(),
                saptamana.getNrSaptamana(),
                saptamana.getDescriere()
        );
    }
}
