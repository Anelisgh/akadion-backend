package com.example.akadion.service;

import com.example.akadion.dto.DocumentResponseDto;
import com.example.akadion.entity.Curs;
import com.example.akadion.entity.Document;
import com.example.akadion.entity.DocumentStatusIndex;
import com.example.akadion.entity.Saptamana;
import com.example.akadion.exception.AccesInterzisException;
import com.example.akadion.repository.DocumentRepository;
import com.example.akadion.repository.SaptamanaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.apache.tika.Tika;
import org.springframework.web.multipart.MultipartFile;
import com.example.akadion.exception.DocumentDuplicatException;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentService {

    private static final Tika TIKA = new Tika();

    private static final Map<String, Set<String>> EXPECTED_MIME_TYPES = Map.of(
            "pdf", Set.of("application/pdf"),
            "docx", Set.of("application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
            "pptx", Set.of("application/vnd.openxmlformats-officedocument.presentationml.presentation"),
            "zip", Set.of("application/zip")
    );

    private final DocumentRepository documentRepository;
    private final SaptamanaRepository saptamanaRepository;
    private final MinioStorageService minioStorageService;
    private final RagIngestService ragIngestService;

    public List<DocumentResponseDto> listaDocumente(Long saptamanaId, Long callerId, String callerRole) {
        Saptamana saptamana = saptamanaRepository.findWithCursAndProfesorById(saptamanaId)
                .orElseThrow(() -> new IllegalArgumentException("Săptămâna nu a fost găsită."));

        if (!"ADMIN".equals(callerRole)) {
            if (!saptamana.getCurs().getProfesor().getId().equals(callerId)) {
                throw new AccesInterzisException("Nu aveți acces la documentele acestei săptămâni.");
            }
        }

        return documentRepository.findBySaptamanaIdAndActivTrue(saptamanaId).stream()
                .map(this::toResponseDto)
                .toList();
    }

    public DocumentResponseDto adaugaDocument(Long saptamanaId, Long profesorId, MultipartFile file, String titlu) {
        Saptamana saptamana = saptamanaRepository.findWithCursAndProfesorById(saptamanaId)
                .orElseThrow(() -> new IllegalArgumentException("Săptămâna nu a fost găsită."));

        Curs curs = saptamana.getCurs();
        if (!curs.getProfesor().getId().equals(profesorId)) {
            throw new AccesInterzisException("Nu aveți permisiunea de a adăuga un document în această săptămână.");
        }

        byte[] fileBytes = validateFile(file);
        String hashContinut = calculeazaHash(fileBytes);

        if (documentRepository.existsBySaptamanaIdAndHashContinutAndActivTrue(saptamana.getId(), hashContinut)) {
            throw new DocumentDuplicatException("Acest fișier a fost deja încărcat în această săptămână.");
        }

        String path = minioStorageService.uploadFile(file, curs.getId(), saptamana.getId());

        Document document = Document.builder()
                .saptamana(saptamana)
                .titlu(titlu)
                .pathMinio(path)
                .hashContinut(hashContinut)
                .statusIndex(DocumentStatusIndex.PRELUAT)
                .activ(true)
                .build();

        try {
            document = documentRepository.saveAndFlush(document);
        } catch (Exception e) {
            log.error("Eroare la salvarea documentului în DB local. Ștergem fișierul orfan din MinIO.", e);
            minioStorageService.deleteFile(path);
            
            Throwable rootCause = e.getCause();
            if (rootCause instanceof org.hibernate.exception.ConstraintViolationException cve) {
                log.info("Constraint Name gasit: {}", cve.getConstraintName());
                if ("uq_documente_hash_saptamana".equals(cve.getConstraintName())) {
                    throw new DocumentDuplicatException("Acest fișier a fost deja încărcat în această săptămână.");
                }
            }
            throw e;
        }

        boolean succes = ragIngestService.trimiteLaIngest(document, saptamana, curs);

        document.setStatusIndex(succes ? DocumentStatusIndex.TRIMIS : DocumentStatusIndex.ERONAT);
        Document savedDocument = documentRepository.save(document);

        log.info("Document adăugat cu succes: docId={}, statusIndex={}", savedDocument.getId(), savedDocument.getStatusIndex());
        return toResponseDto(savedDocument);
    }

    public DocumentResponseDto modificaDocument(Long documentId, Long profesorId, String titlu, MultipartFile fisierNou) {
        Document document = documentRepository.findWithSaptamanaAndCursAndProfesorById(documentId)
                .orElseThrow(() -> new IllegalArgumentException("Documentul nu a fost găsit."));

        Saptamana saptamana = document.getSaptamana();
        Curs curs = saptamana.getCurs();
        if (!curs.getProfesor().getId().equals(profesorId)) {
            throw new AccesInterzisException("Nu aveți permisiunea de a modifica acest document.");
        }

        if (fisierNou != null) {
            byte[] fileBytes = validateFile(fisierNou);
            String hashNou = calculeazaHash(fileBytes);

            if (documentRepository.existsBySaptamanaIdAndHashContinutAndIdNotAndActivTrue(saptamana.getId(), hashNou, documentId)) {
                throw new DocumentDuplicatException("Acest fișier a fost deja încărcat în această săptămână.");
            }

            String pathVechi = document.getPathMinio();
            String pathNou = minioStorageService.uploadFile(fisierNou, curs.getId(), saptamana.getId());

            document.setPathMinio(pathNou);
            document.setHashContinut(hashNou);
            document.setStatusIndex(DocumentStatusIndex.PRELUAT);
            if (titlu != null) {
                document.setTitlu(titlu);
            }

            try {
                document = documentRepository.saveAndFlush(document);
            } catch (Exception e) {
                log.error("Eroare la salvarea documentului. Ștergem orfanul MinIO.", e);
                minioStorageService.deleteFile(pathNou);
                
                Throwable rootCause = e.getCause();
                if (rootCause instanceof org.hibernate.exception.ConstraintViolationException cve) {
                    log.info("Constraint Name gasit: {}", cve.getConstraintName());
                    if ("uq_documente_hash_saptamana".equals(cve.getConstraintName())) {
                        throw new DocumentDuplicatException("Acest fișier a fost deja încărcat în această săptămână.");
                    }
                }
                throw e;
            }

            minioStorageService.deleteFile(pathVechi);

            boolean succes = ragIngestService.trimiteLaIngest(document, saptamana, curs);
            document.setStatusIndex(succes ? DocumentStatusIndex.TRIMIS : DocumentStatusIndex.ERONAT);
            document = documentRepository.save(document);

        } else if (titlu != null) {
            document.setTitlu(titlu);
            document = documentRepository.save(document);

            boolean succes = ragIngestService.trimiteLaIngest(document, saptamana, curs);
            document.setStatusIndex(succes ? DocumentStatusIndex.TRIMIS : DocumentStatusIndex.ERONAT);
            document = documentRepository.save(document);
        }

        log.info("Document modificat cu succes: docId={}, statusIndex={}", document.getId(), document.getStatusIndex());
        return toResponseDto(document);
    }

    public void stergeDocument(Long documentId, Long profesorId) {
        Document document = documentRepository.findWithSaptamanaAndCursAndProfesorById(documentId)
                .orElseThrow(() -> new IllegalArgumentException("Documentul nu a fost găsit."));

        if (!document.getSaptamana().getCurs().getProfesor().getId().equals(profesorId)) {
            throw new AccesInterzisException("Nu aveți permisiunea de a șterge acest document.");
        }

        document.setActiv(false);
        documentRepository.save(document);

        if (document.getPathMinio() != null && !document.getPathMinio().isBlank()) {
            minioStorageService.deleteFile(document.getPathMinio());
        }

        ragIngestService.stergeDinIngest(documentId);
        log.info("Document șters cu succes: docId={}", documentId);
    }

    public DocumentResponseDto reincearcaIngest(Long documentId, Long profesorId) {
        Document document = documentRepository.findWithSaptamanaAndCursAndProfesorById(documentId)
                .orElseThrow(() -> new IllegalArgumentException("Documentul nu a fost găsit."));

        if (!document.getSaptamana().getCurs().getProfesor().getId().equals(profesorId)) {
            throw new AccesInterzisException("Nu aveți permisiunea de a reîncerca indexarea pentru acest document.");
        }

        if (document.getStatusIndex() == DocumentStatusIndex.TRIMIS) {
            throw new IllegalArgumentException("Documentul este deja indexat cu succes.");
        }

        boolean succes = ragIngestService.trimiteLaIngest(document, document.getSaptamana(), document.getSaptamana().getCurs());
        document.setStatusIndex(succes ? DocumentStatusIndex.TRIMIS : DocumentStatusIndex.ERONAT);
        Document savedDocument = documentRepository.save(document);

        log.info("Reîncercare indexare finalizată: docId={}, statusIndex={}", savedDocument.getId(), savedDocument.getStatusIndex());
        return toResponseDto(savedDocument);
    }

    /**
     * Validarea cu Tika se asigură că extensia fișierului corespunde formatului real 
     * (previne mascarea executabilelor în .pdf etc).
     * NU scanează de malware/viruși.
     */
    private byte[] validateFile(MultipartFile file) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("Fișierul încărcat este gol.");
        }
        String originalName = file.getOriginalFilename();
        String ext = (originalName != null && originalName.contains(".")) 
                ? originalName.substring(originalName.lastIndexOf(".") + 1).toLowerCase() 
                : "";
                
        Set<String> validMimes = EXPECTED_MIME_TYPES.get(ext);
        if (validMimes == null) {
            throw new IllegalArgumentException("Tip de fișier nepermis. Sunt permise doar: pdf, docx, pptx, zip.");
        }

        try {
            byte[] bytes = file.getBytes();
            String detectedType = TIKA.detect(bytes);
            if (!validMimes.contains(detectedType)) {
                log.warn("Tentativă de încărcare fișier invalid. Tika a detectat: {}, Extensie declarată: {}", detectedType, ext);
                throw new IllegalArgumentException("Fișierul pare corupt sau are extensia greșită. Vă rugăm să verificați fișierul original.");
            }
            return bytes;
        } catch (IOException e) {
            log.error("Eroare la citirea conținutului fișierului pentru validarea Tika.", e);
            throw new IllegalArgumentException("A apărut o eroare la procesarea fișierului. Vă rugăm să încercați din nou.");
        }
    }

    private String calculeazaHash(byte[] continut) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encodedhash = digest.digest(continut);
            return bytesToHex(encodedhash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Eroare la calcularea hash-ului", e);
        }
    }
    
    private String bytesToHex(byte[] hash) {
        StringBuilder hexString = new StringBuilder(2 * hash.length);
        for (int i = 0; i < hash.length; i++) {
            String hex = Integer.toHexString(0xff & hash[i]);
            if(hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }

    private DocumentResponseDto toResponseDto(Document document) {
        String urlVizualizare = minioStorageService.getPresignedPreviewUrl(document.getPathMinio());
        String urlDescarcare = minioStorageService.getPresignedDownloadUrl(document.getPathMinio());
        return new DocumentResponseDto(
                document.getId(),
                document.getTitlu(),
                document.getStatusIndex().name(),
                document.getActiv(),
                urlVizualizare,
                urlDescarcare
        );
    }
}
