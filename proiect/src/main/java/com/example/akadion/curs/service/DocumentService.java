package com.example.akadion.curs.service;

import com.example.akadion.admin.entity.NumeTabelAudit;
import com.example.akadion.admin.entity.OperatieAudit;
import com.example.akadion.admin.service.AuditLogService;
import com.example.akadion.auth.service.RateLimiterService;
import com.example.akadion.curs.dto.DocumentResponseDto;
import com.example.akadion.curs.entity.Curs;
import com.example.akadion.curs.entity.Document;
import com.example.akadion.curs.entity.DocumentStatusIndex;
import com.example.akadion.curs.entity.Saptamana;
import com.example.akadion.exception.DocumentDuplicatException;
import com.example.akadion.curs.repository.DocumentRepository;
import com.example.akadion.curs.repository.SaptamanaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentService {

    private static final Tika TIKA = new Tika();
    private static final int MAX_UPLOADURI_PE_MINUT = 20;
    private static final String TITLU_KEY = "titlu";
    private static final String ERR_DOCUMENT_NOT_FOUND = "Documentul nu a fost găsit.";

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
    private final AuditLogService auditLogService;
    private final CursOwnershipValidator cursOwnershipValidator;
    private final DocumentUrlBuilder documentUrlBuilder;
    private final RateLimiterService rateLimiterService;

    // Field injection intenționată: un service nu se poate auto-injecta prin constructor
    // (dependință circulară) fără @Lazy, iar Lombok @RequiredArgsConstructor nu suportă @Lazy per-parametru.
    // Necesar pentru ca apelurile interne prin self.xxx() să treacă prin proxy-ul @Transactional.
    @SuppressWarnings("java:S6813")
    @Autowired
    @Lazy
    private DocumentService self;

    public List<DocumentResponseDto> listaDocumente(Long saptamanaId, Long callerId, String callerRole) {
        Saptamana saptamana = saptamanaRepository.findWithCursAndProfesorById(saptamanaId)
                .orElseThrow(() -> new IllegalArgumentException("Săptămâna nu a fost găsită."));

        cursOwnershipValidator.verificaProprietarSauAdmin(saptamana.getCurs(), callerId, callerRole, "Nu aveți acces la documentele acestei săptămâni.");

        return documentRepository.findBySaptamanaIdAndActivTrue(saptamanaId).stream()
                .map(this::toResponseDto)
                .toList();
    }

    public Document getAccessibleDocument(Long documentId, Long callerId, String callerRole) {
        Document document = documentRepository.findWithSaptamanaAndCursAndProfesorById(documentId)
                .orElseThrow(() -> new IllegalArgumentException(ERR_DOCUMENT_NOT_FOUND));

        if (!document.isActiv()) {
            throw new IllegalArgumentException(ERR_DOCUMENT_NOT_FOUND);
        }

        cursOwnershipValidator.verificaProprietarSauAdmin(document.getSaptamana().getCurs(), callerId, callerRole, "Nu aveți acces la acest document.");

        return document;
    }

    public DocumentResponseDto adaugaDocument(Long saptamanaId, Long profesorId, MultipartFile file, String titlu) {
        rateLimiterService.verificaLimita("document-upload:" + profesorId, MAX_UPLOADURI_PE_MINUT, Duration.ofMinutes(1));

        Saptamana saptamana = saptamanaRepository.findWithCursAndProfesorById(saptamanaId)
                .orElseThrow(() -> new IllegalArgumentException("Săptămâna nu a fost găsită."));

        Curs curs = saptamana.getCurs();
        cursOwnershipValidator.verificaProprietar(curs, profesorId, "Nu aveți permisiunea de a adăuga un document în această săptămână.");

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

        document = salveazaCuRollbackOrfan(document, path);

        boolean succes = ragIngestService.trimiteLaIngest(document, saptamana, curs);
        Document savedDocument = self.finalizeazaUploadSiAuditeaza(document.getId(), succes);

        log.info("Document adăugat cu succes: docId={}, statusIndex={}", savedDocument.getId(), savedDocument.getStatusIndex());
        return toResponseDto(savedDocument);
    }

    @Transactional
    public Document finalizeazaUploadSiAuditeaza(Long documentId, boolean ragSuccess) {
        Document document = documentRepository.findById(documentId).orElseThrow();
        document.setStatusIndex(ragSuccess ? DocumentStatusIndex.TRIMIS : DocumentStatusIndex.ERONAT);
        Document savedDocument = documentRepository.save(document);

        auditLogService.inregistreaza(
                NumeTabelAudit.DOCUMENT,
                documentId,
                OperatieAudit.UPLOAD,
                null,
                Map.of(TITLU_KEY, savedDocument.getTitlu(), "statusIndex", savedDocument.getStatusIndex().name())
        );
        return savedDocument;
    }

    public DocumentResponseDto modificaDocument(Long documentId, Long profesorId, String titlu, MultipartFile fisierNou) {
        Document document = documentRepository.findWithSaptamanaAndCursAndProfesorById(documentId)
                .orElseThrow(() -> new IllegalArgumentException(ERR_DOCUMENT_NOT_FOUND));

        Saptamana saptamana = document.getSaptamana();
        Curs curs = saptamana.getCurs();
        cursOwnershipValidator.verificaProprietar(curs, profesorId, "Nu aveți permisiunea de a modifica acest document.");

        String vechiulTitlu = document.getTitlu();

        if (fisierNou != null) {
            rateLimiterService.verificaLimita("document-upload:" + profesorId, MAX_UPLOADURI_PE_MINUT, Duration.ofMinutes(1));
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

            document = salveazaCuRollbackOrfan(document, pathNou);

            minioStorageService.deleteFile(pathVechi);

            boolean succes = ragIngestService.trimiteLaIngest(document, saptamana, curs);
            document = self.finalizeazaModificareSiAuditeaza(document.getId(), succes, vechiulTitlu);

        } else if (titlu != null) {
            document.setTitlu(titlu);
            document = documentRepository.save(document);

            boolean succes = ragIngestService.trimiteLaIngest(document, saptamana, curs);
            document = self.finalizeazaModificareSiAuditeaza(document.getId(), succes, vechiulTitlu);
        }

        log.info("Document modificat cu succes: docId={}, statusIndex={}", document.getId(), document.getStatusIndex());
        return toResponseDto(document);
    }

    @Transactional
    public Document finalizeazaModificareSiAuditeaza(Long documentId, boolean ragSuccess, String vechiulTitlu) {
        Document document = documentRepository.findById(documentId).orElseThrow();
        document.setStatusIndex(ragSuccess ? DocumentStatusIndex.TRIMIS : DocumentStatusIndex.ERONAT);
        Document savedDocument = documentRepository.save(document);

        auditLogService.inregistreaza(
                NumeTabelAudit.DOCUMENT,
                documentId,
                OperatieAudit.INLOCUIRE,
                Map.of(TITLU_KEY, vechiulTitlu),
                Map.of(TITLU_KEY, savedDocument.getTitlu(), "statusIndex", savedDocument.getStatusIndex().name())
        );
        return savedDocument;
    }

    public void stergeDocument(Long documentId, Long profesorId) {
        Document document = documentRepository.findWithSaptamanaAndCursAndProfesorById(documentId)
                .orElseThrow(() -> new IllegalArgumentException(ERR_DOCUMENT_NOT_FOUND));

        cursOwnershipValidator.verificaProprietar(document.getSaptamana().getCurs(), profesorId, "Nu aveți permisiunea de a șterge acest document.");

        Document savedDocument = self.finalizeazaStergereSiAuditeaza(documentId);

        if (savedDocument.getPathMinio() != null && !savedDocument.getPathMinio().isBlank()) {
            minioStorageService.deleteFile(savedDocument.getPathMinio());
        }

        ragIngestService.stergeDinIngest(documentId);
        log.info("Document șters cu succes: docId={}", documentId);
    }

    @Transactional
    public Document finalizeazaStergereSiAuditeaza(Long documentId) {
        Document document = documentRepository.findById(documentId).orElseThrow();
        String vechiulTitlu = document.getTitlu();
        document.setActiv(false);
        Document savedDocument = documentRepository.save(document);

        auditLogService.inregistreaza(
                NumeTabelAudit.DOCUMENT,
                documentId,
                OperatieAudit.STERGERE,
                Map.of("activ", true, TITLU_KEY, vechiulTitlu),
                Map.of("activ", false)
        );
        return savedDocument;
    }

    public DocumentResponseDto reincearcaIngest(Long documentId, Long profesorId) {
        Document document = documentRepository.findWithSaptamanaAndCursAndProfesorById(documentId)
                .orElseThrow(() -> new IllegalArgumentException(ERR_DOCUMENT_NOT_FOUND));

        cursOwnershipValidator.verificaProprietar(document.getSaptamana().getCurs(), profesorId, "Nu aveți permisiunea de a reîncerca indexarea pentru acest document.");

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
        for (byte currentByte : hash) {
            String hex = Integer.toHexString(0xff & currentByte);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }

    private DocumentResponseDto toResponseDto(Document document) {
        String urlVizualizare = documentUrlBuilder.previewUrl(document);
        String urlDescarcare = documentUrlBuilder.downloadUrl(document);
        return new DocumentResponseDto(
                document.getId(),
                document.getTitlu(),
                document.getStatusIndex(),
                document.isActiv(),
                urlVizualizare,
                urlDescarcare
        );
    }

    private Document salveazaCuRollbackOrfan(Document document, String pathMinioOrfan) {
        try {
            return documentRepository.saveAndFlush(document);
        } catch (Exception e) {
            log.error("Eroare la salvarea documentului în DB local. Ștergem fișierul orfan din MinIO.", e);
            minioStorageService.deleteFile(pathMinioOrfan);

            Throwable rootCause = e.getCause();
            if (rootCause instanceof ConstraintViolationException cve) {
                log.info("Constraint Name gasit: {}", cve.getConstraintName());
                if ("uq_documente_hash_saptamana".equals(cve.getConstraintName())) {
                    throw new DocumentDuplicatException("Acest fișier a fost deja încărcat în această săptămână.");
                }
            }
            throw e;
        }
    }
}
