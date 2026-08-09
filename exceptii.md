# Documentație: Gestiunea Excepțiilor (Pachetul `com.example.akadion.exception`)

Acest document descrie excepțiile custom, utilizarea lor și mecanismul global de `ControllerAdvice` al aplicației.

## Principiul de Bază
`GlobalExceptionHandler` (marcat cu `@RestControllerAdvice`) adună toate erorile propagare din Servicii sau controllere și emite structuri unitare JSON spre client, cu formatele:

### Eroare Simplă
```json
{
  "status": 403,
  "eroare": "Nu aveți acces la acest curs."
}
```

### Eroare de Validare (`MethodArgumentNotValidException`)
Când validările `jakarta.validation` (`@Valid` sau `@NotBlank`, `@Size` de pe DTO-uri) pică:
```json
{
  "status": 400,
  "eroare": "Date invalide",
  "campuri": {
    "denumire": "Denumirea cursului este obligatorie.",
    "intrebare": "Întrebarea nu poate depăși 1000 de caractere."
  }
}
```

## Catalogul Excepțiilor Mapate & Status Codes

### Erori de Client (4xx)
| Clasa Excepției | HTTP Status | Când este aruncată |
| :--- | :--- | :--- |
| `MethodArgumentNotValidException` | `400 Bad Request` | DTO invalid. |
| `IllegalArgumentException` | `400 Bad Request` | Parametri de business incorecți (ex: Extensie document greșită, curs invalid etc). |
| `InvalidUserStateException` | `400 Bad Request` | Contul e într-o stare greșită pentru operațiunea respectivă. (ex: a da 'Approve' pe un cont 'INACTIV', în loc de 'PENDING'). |
| `AccesInterzisException` | `403 Forbidden` | Securitate Business. Utilizatorul e validat ca token, e activ, dar nu este "owner" pe entitate (Nu este profesorul cursului sau documentului, și nici admin). |
| `ForbiddenOperationException` | `403 Forbidden` | Validări specifice de business blocate. (ex: Duplicate de adresă e-mail detectate, tentativă de cerere a rolului de ADMIN la completare profil). |
| `UserNotFoundException` | `404 Not Found` | ID inexistent în DB-ul local (de regulă declanșată la preluarea ID-ului din session-ul Keycloak dacă DB locală are contul șters). |
| `KeycloakConflictException` | `409 Conflict` | Apelurile API de admin către Keycloak semnalează o duplicare. |
| `SaptamanaConcurentaException` | `409 Conflict` | Concurrency: Mai mulți useri randează insert pe aceeași săptămână auto-incrementată. |
| `MaxUploadSizeExceededException` | `413 Payload Too Large` | Fișier prea mare pentru MinIO (max configurat în prop = 50MB). |
| `TooManyRequestsException` | `429 Too Many Requests` | Rate Limiter lovit pe serviciul `StudentCursService` (Asistentul Aky: depășire limita). |

### Erori Server / Erori Integrate (5xx)
| Clasa Excepției | HTTP Status | Când este aruncată |
| :--- | :--- | :--- |
| `KeycloakIntegrationException` | `502 Bad Gateway` | Network issue spre serverul Keycloak. |
| `MinioIntegrationException` | `502 Bad Gateway` | O eroare neprinsă, problemă conexiune IO cu S3/MinIO. |
| `RagChatException` | `502 Bad Gateway` | Serverul FastAPI RAG răspunde cu eroare sau cedează (timeout). |
| *Orice excepție generală (`Exception`)* | `500 Internal Error` | Fallback universal de siguranță în handler. |
