package com.example.akadion.service;

import com.example.akadion.exception.MinioIntegrationException;
import io.minio.GetObjectArgs;
import io.minio.GetObjectResponse;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import io.minio.http.Method;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class MinioStorageService {

    private final MinioClient minioClient;

    @Value("${minio.bucket}")
    private String bucket;

    public String uploadFile(MultipartFile file, Long cursId, Long saptamanaId) {
        String originalFilename = file.getOriginalFilename();
        String sanitizedFilename = originalFilename != null 
                ? originalFilename.replaceAll("[^a-zA-Z0-9.-]", "_") 
                : "file_" + UUID.randomUUID().toString().substring(0, 8);
                
        String key = "curs-%d/saptamana-%d/%s-%s".formatted(
                cursId, saptamanaId, UUID.randomUUID(), sanitizedFilename);
        try (InputStream is = file.getInputStream()) {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucket).object(key)
                    .stream(is, file.getSize(), -1)
                    .contentType(file.getContentType())
                    .build());
        } catch (Exception e) {
            throw new MinioIntegrationException("Eroare la upload în MinIO pentru " + key, e);
        }
        return key;
    }

    public void deleteFile(String key) {
        try {
            minioClient.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(key).build());
        } catch (Exception e) {
            // Loghează, nu arunca — o ștergere eșuată de fișier orfan nu trebuie să blocheze restul fluxului
            log.error("Eroare la ștergerea din MinIO a obiectului {}: {}", key, e.getMessage());
        }
    }

    /**
     * Ștergere secvențială a mai multor fișiere (folosită la Etapa 5, cascada de ștergere a unei săptămâni).
     * Refolosește {@link #deleteFile(String)} care are propriul try-catch — un fișier care nu poate fi șters
     * nu oprește ștergerea celorlalte.
     *
     * ⚠️ Alternativa cu {@code removeObjects()} (bulk S3) a fost evitată intenționat: API-ul MinIO
     * returnează un {@code Iterable<Result<DeleteError>>} evaluat LAZY — dacă nu iterezi rezultatele,
     * ștergerile nu se execută efectiv, iar erorile individuale sunt ușor de ratat.
     */
    public void deleteFiles(List<String> keys) {
        for (String key : keys) {
            deleteFile(key); // deleteFile are try-catch propriu — nu aruncă excepție
        }
    }

    public StoredFile getFile(String key) {
        try {
            StatObjectResponse stat = minioClient.statObject(StatObjectArgs.builder()
                    .bucket(bucket)
                    .object(key)
                    .build());
            GetObjectResponse stream = minioClient.getObject(GetObjectArgs.builder()
                    .bucket(bucket)
                    .object(key)
                    .build());
            return new StoredFile(
                    stream,
                    stat.contentType(),
                    stat.size(),
                    extractOriginalFilename(key)
            );
        } catch (Exception e) {
            throw new MinioIntegrationException("Eroare la citirea din MinIO pentru " + key, e);
        }
    }

    public String extractOriginalFilename(String key) {
        String filename = key.substring(key.lastIndexOf('/') + 1);
        if (filename.length() > 37 && filename.charAt(8) == '-' && filename.charAt(13) == '-') {
            return filename.substring(37);
        }
        return filename;
    }

    public String getPresignedPreviewUrl(String key) {
        return getPresignedUrl(key, contentDisposition("inline", key));
    }

    public String getPresignedDownloadUrl(String key) {
        return getPresignedUrl(key, contentDisposition("attachment", key));
    }

    public String getPresignedUrl(String key) {
        return getPresignedDownloadUrl(key);
    }

    private String getPresignedUrl(String key, String contentDisposition) {
        try {
            Map<String, String> reqParams = Map.of("response-content-disposition", contentDisposition);
            return minioClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(Method.GET)
                    .bucket(bucket)
                    .object(key)
                    .extraQueryParams(reqParams)
                    .expiry(1, TimeUnit.HOURS)
                    .build());
        } catch (Exception e) {
            throw new MinioIntegrationException("Eroare la generarea URL-ului presemnat pentru " + key, e);
        }
    }

    private String contentDisposition(String dispositionType, String key) {
        String filename = extractOriginalFilename(key);
        return dispositionType + "; filename=\"" + filename.replace("\"", "_") + "\"";
    }

    public record StoredFile(InputStream stream, String contentType, long contentLength, String filename) {
    }
}
