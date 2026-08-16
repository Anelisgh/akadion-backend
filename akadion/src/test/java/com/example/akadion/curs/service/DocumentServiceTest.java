package com.example.akadion.curs.service;

import com.example.akadion.admin.service.AuditLogService;
import com.example.akadion.auth.service.RateLimiterService;
import com.example.akadion.curs.entity.Curs;
import com.example.akadion.curs.entity.Document;
import com.example.akadion.curs.entity.DocumentStatusIndex;
import com.example.akadion.curs.entity.Saptamana;
import com.example.akadion.common.entity.User;
import com.example.akadion.curs.repository.DocumentRepository;
import com.example.akadion.curs.repository.SaptamanaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;
import com.example.akadion.exception.DocumentDuplicatException;
import org.springframework.dao.DataIntegrityViolationException;
import org.hibernate.exception.ConstraintViolationException;
import java.sql.SQLException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DocumentServiceTest {

    @Mock
    private DocumentRepository documentRepository;
    @Mock
    private SaptamanaRepository saptamanaRepository;
    @Mock
    private MinioStorageService minioStorageService;
    @Mock
    private RagIngestService ragIngestService;
    @Mock
    private AuditLogService auditLogService;
    @Mock
    private CursOwnershipValidator cursOwnershipValidator;
    @Mock
    private DocumentUrlBuilder documentUrlBuilder;
    @Mock
    private RateLimiterService rateLimiterService;

    @InjectMocks
    private DocumentService documentService;

    private Saptamana mockSaptamana;
    private final Long saptamanaId = 1L;
    private final Long profesorId = 100L;

    @BeforeEach
    void setUp() {
        User profesor = new User();
        profesor.setId(profesorId);

        Curs curs = new Curs();
        curs.setId(10L);
        curs.setProfesor(profesor);

        mockSaptamana = new Saptamana();
        mockSaptamana.setId(saptamanaId);
        mockSaptamana.setCurs(curs);

        ReflectionTestUtils.setField(documentService, "self", documentService);
    }

    @Test
    void validateFile_ValidPdf_Passes() {
        when(saptamanaRepository.findWithCursAndProfesorById(saptamanaId)).thenReturn(Optional.of(mockSaptamana));
        when(minioStorageService.uploadFile(any(), anyLong(), anyLong())).thenReturn("path/to/file.pdf");
        
        Document savedDoc = new Document();
        savedDoc.setId(99L);
        savedDoc.setTitlu("Titlu mock");
        savedDoc.setStatusIndex(DocumentStatusIndex.PRELUAT);
        when(documentRepository.saveAndFlush(any())).thenReturn(savedDoc);
        when(documentRepository.save(any())).thenReturn(savedDoc);
        when(documentRepository.findById(99L)).thenReturn(Optional.of(savedDoc));
        
        when(ragIngestService.trimiteLaIngest(any(), any(), any())).thenReturn(true);
        
        byte[] pdfMagicBytes = new byte[] {0x25, 0x50, 0x44, 0x46, 0x2D, 0x31, 0x2E, 0x34}; // %PDF-1.4
        MockMultipartFile validPdf = new MockMultipartFile("file", "test.pdf", "application/pdf", pdfMagicBytes);

        // Act
        documentService.adaugaDocument(saptamanaId, profesorId, validPdf, "Titlu PDF");
        
        // Assert
        verify(minioStorageService, times(1)).uploadFile(any(), anyLong(), anyLong());
    }

    @Test
    void validateFile_TextRenamedAsPdf_ThrowsException() {
        when(saptamanaRepository.findWithCursAndProfesorById(saptamanaId)).thenReturn(Optional.of(mockSaptamana));

        byte[] fakePdfBytes = "Acesta este un text simplu.".getBytes();
        MockMultipartFile fakePdf = new MockMultipartFile("file", "fake.pdf", "application/pdf", fakePdfBytes);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> 
            documentService.adaugaDocument(saptamanaId, profesorId, fakePdf, "Titlu Fals")
        );

        assertTrue(exception.getMessage().contains("Fișierul pare corupt sau are extensia greșită"));
        verifyNoInteractions(minioStorageService);
    }

    @Test
    void validateFile_ZipRenamedAsDocx_ThrowsException() throws IOException {
        when(saptamanaRepository.findWithCursAndProfesorById(saptamanaId)).thenReturn(Optional.of(mockSaptamana));

        // Create a real ZIP in memory
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            ZipEntry entry = new ZipEntry("test.txt");
            zos.putNextEntry(entry);
            zos.write("Salut".getBytes());
            zos.closeEntry();
        }
        byte[] zipBytes = baos.toByteArray();

        // Pass it as DOCX
        MockMultipartFile zipAsDocx = new MockMultipartFile("file", "malicious.docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", zipBytes);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> 
            documentService.adaugaDocument(saptamanaId, profesorId, zipAsDocx, "Titlu ZIP")
        );

        assertTrue(exception.getMessage().contains("Fișierul pare corupt sau are extensia greșită"));
        verifyNoInteractions(minioStorageService);
    }

    @Test
    void validateFile_IOExceptionOnRead_ThrowsException() throws IOException {
        when(saptamanaRepository.findWithCursAndProfesorById(saptamanaId)).thenReturn(Optional.of(mockSaptamana));

        MultipartFile badFile = mock(MultipartFile.class);
        when(badFile.getOriginalFilename()).thenReturn("test.pdf");
        when(badFile.isEmpty()).thenReturn(false);
        when(badFile.getBytes()).thenThrow(new IOException("Simulated IO Exception"));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> 
            documentService.adaugaDocument(saptamanaId, profesorId, badFile, "Titlu Eroare IO")
        );

        assertTrue(exception.getMessage().contains("eroare la procesarea fișierului"));
        verifyNoInteractions(minioStorageService);
    }

    @Test
    void validateFile_UnpermittedExtension_ThrowsException() {
        when(saptamanaRepository.findWithCursAndProfesorById(saptamanaId)).thenReturn(Optional.of(mockSaptamana));

        byte[] exeBytes = "MZ".getBytes(); // executable magic bytes
        MockMultipartFile exeFile = new MockMultipartFile("file", "virus.exe", "application/octet-stream", exeBytes);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> 
            documentService.adaugaDocument(saptamanaId, profesorId, exeFile, "Titlu EXE")
        );

        assertTrue(exception.getMessage().contains("Tip de fișier nepermis"));
        verifyNoInteractions(minioStorageService);
    }

    @Test
    void adaugaDocument_DuplicateHash_ThrowsException() {
        when(saptamanaRepository.findWithCursAndProfesorById(saptamanaId)).thenReturn(Optional.of(mockSaptamana));
        
        byte[] pdfMagicBytes = new byte[] {0x25, 0x50, 0x44, 0x46, 0x2D, 0x31, 0x2E, 0x34};
        MockMultipartFile validPdf = new MockMultipartFile("file", "test.pdf", "application/pdf", pdfMagicBytes);

        // Pre-check returneaza true (hash duplicat in DB)
        when(documentRepository.existsBySaptamanaIdAndHashContinutAndActivTrue(anyLong(), anyString())).thenReturn(true);

        DocumentDuplicatException exception = assertThrows(DocumentDuplicatException.class, () -> 
            documentService.adaugaDocument(saptamanaId, profesorId, validPdf, "Titlu Duplicat")
        );

        assertTrue(exception.getMessage().contains("deja încărcat"));
        verifyNoInteractions(minioStorageService);
    }

    @Test
    void modificaDocument_DuplicateHash_ThrowsException() {
        Document mockDoc = new Document();
        mockDoc.setId(99L);
        mockDoc.setSaptamana(mockSaptamana);
        when(documentRepository.findWithSaptamanaAndCursAndProfesorById(99L)).thenReturn(Optional.of(mockDoc));

        byte[] pdfMagicBytes = new byte[] {0x25, 0x50, 0x44, 0x46, 0x2D, 0x31, 0x2E, 0x34};
        MockMultipartFile validPdf = new MockMultipartFile("file", "test.pdf", "application/pdf", pdfMagicBytes);

        when(documentRepository.existsBySaptamanaIdAndHashContinutAndIdNotAndActivTrue(anyLong(), anyString(), anyLong())).thenReturn(true);

        DocumentDuplicatException exception = assertThrows(DocumentDuplicatException.class, () -> 
            documentService.modificaDocument(99L, profesorId, "Nou Titlu", validPdf)
        );

        assertTrue(exception.getMessage().contains("deja încărcat"));
        verifyNoInteractions(minioStorageService);
    }

    @Test
    void adaugaDocument_RaceCondition_DeletesMinioAndThrows() {
        when(saptamanaRepository.findWithCursAndProfesorById(saptamanaId)).thenReturn(Optional.of(mockSaptamana));
        String pathNou = "path/to/new.pdf";
        when(minioStorageService.uploadFile(any(), anyLong(), anyLong())).thenReturn(pathNou);
        
        // Simulam exceptia DB care vine la race condition
        ConstraintViolationException cve = new ConstraintViolationException("Violare", new SQLException(), "uq_documente_hash_saptamana");
        DataIntegrityViolationException dbError = new DataIntegrityViolationException("Conflict hash", cve);
        
        when(documentRepository.saveAndFlush(any())).thenThrow(dbError);

        byte[] pdfMagicBytes = new byte[] {0x25, 0x50, 0x44, 0x46, 0x2D, 0x31, 0x2E, 0x34};
        MockMultipartFile validPdf = new MockMultipartFile("file", "test.pdf", "application/pdf", pdfMagicBytes);

        DocumentDuplicatException exception = assertThrows(DocumentDuplicatException.class, () -> 
            documentService.adaugaDocument(saptamanaId, profesorId, validPdf, "Titlu Cursa")
        );

        assertTrue(exception.getMessage().contains("deja încărcat"));
        
        // Verifica stergerea din minio a fisierului nou creat
        verify(minioStorageService, times(1)).deleteFile(pathNou);
    }

    @Test
    void modificaDocument_RaceCondition_DeletesNewMinioAndKeepsOld() {
        Document mockDoc = new Document();
        mockDoc.setId(99L);
        mockDoc.setSaptamana(mockSaptamana);
        String pathVechi = "path/to/old.pdf";
        mockDoc.setPathMinio(pathVechi);
        
        when(documentRepository.findWithSaptamanaAndCursAndProfesorById(99L)).thenReturn(Optional.of(mockDoc));
        
        String pathNou = "path/to/new.pdf";
        when(minioStorageService.uploadFile(any(), anyLong(), anyLong())).thenReturn(pathNou);
        
        ConstraintViolationException cve = new ConstraintViolationException("Violare", new SQLException(), "uq_documente_hash_saptamana");
        DataIntegrityViolationException dbError = new DataIntegrityViolationException("Conflict hash", cve);
        
        when(documentRepository.saveAndFlush(any())).thenThrow(dbError);

        byte[] pdfMagicBytes = new byte[] {0x25, 0x50, 0x44, 0x46, 0x2D, 0x31, 0x2E, 0x34};
        MockMultipartFile validPdf = new MockMultipartFile("file", "test.pdf", "application/pdf", pdfMagicBytes);

        DocumentDuplicatException exception = assertThrows(DocumentDuplicatException.class, () -> 
            documentService.modificaDocument(99L, profesorId, "Nou Titlu Cursa", validPdf)
        );

        assertTrue(exception.getMessage().contains("deja încărcat"));
        
        // Fisierul NOU a fost sters
        verify(minioStorageService, times(1)).deleteFile(pathNou);
        // Fisierul VECHI a ramas neatins
        verify(minioStorageService, never()).deleteFile(pathVechi);
    }
}
