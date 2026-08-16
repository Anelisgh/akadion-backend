# Documentație: Gestiunea Excepțiilor (Pachetul `com.example.akadion.exception`)

> Actualizat 2026-08-13, după restructurarea package-by-feature și consolidarea excepțiilor. Pachetul `exception` a rămas cross-cutting la rădăcina `com.example.akadion` (nu a fost mutat într-un feature).

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
| `IllegalArgumentException` | `400 Bad Request` | Parametri de business incorecți (ex: Extensie document greșită, curs invalid etc). **Inconsecvență cunoscută, lăsată neatinsă intenționat**: e folosită și pentru „resursă negăsită" în unele servicii (`CursService`, `SaptamanaService`, `DocumentService`), nu doar pentru validare — a schimba asta ar schimba contractul HTTP cu frontend-ul. |
| `InvalidUserStateException` | `400 Bad Request` | Contul e într-o stare greșită pentru operațiunea respectivă. (ex: a da 'Approve' pe un cont 'INACTIV', în loc de 'PENDING'). |
| `ForbiddenOperationException` | `403 Forbidden` | Securitate business + validări de business blocate: utilizatorul nu e "owner" pe entitate (nu e profesorul cursului/documentului, nici admin — via `CursOwnershipValidator`), duplicat de adresă e-mail, tentativă de a cere rolul ADMIN la completare profil, acces la o resursă a altui utilizator etc. **Înlocuiește fosta `AccesInterzisException`**, consolidată aici. |
| `ResursaNegasitaException` | `404 Not Found` | O resursă nu a putut fi găsită (ex. utilizator, curs, document, încercare de quiz), folosită în servicii precum `AdminUserService`, `CompleteProfileService`, `ConversatieService`, `StudentQuizService`, `CursService` (parțial). **Înlocuiește fosta `UserNotFoundException`**. Fără `@ResponseStatus` propriu — mapare doar prin `GlobalExceptionHandler`. |
| `ResourceConflictException` | `409 Conflict` | Bază abstractă (fără `@ResponseStatus` propriu) pentru toate conflictele de mai jos — un singur handler în `GlobalExceptionHandler` le acoperă pe toate patru. |
| `SaptamanaConcurentaException` (extinde `ResourceConflictException`) | `409 Conflict` | Concurrency: Mai mulți useri randează insert pe aceeași săptămână auto-incrementată. |
| `DocumentDuplicatException` (extinde `ResourceConflictException`) | `409 Conflict` | Un fișier cu același hash SHA-256 a fost deja încărcat în săptămâna respectivă (previne duplicatele bit-by-bit). |
| `IncercareQuizFinalizataException` (extinde `ResourceConflictException`) | `409 Conflict` | Un quiz aflat în starea `GENERATA` a fost deja trimis spre finalizare anterior. Previne trimiterea de rezultate multiple pentru același quiz. |
| `EmailDuplicatException` (extinde `ResourceConflictException`) | `409 Conflict` | Email deja folosit de alt cont — la înregistrare, completare profil sau schimbare email. Adăugată în runda de consolidare a excepțiilor (409 în loc de fostul 403). |
| `MaxUploadSizeExceededException` | `413 Payload Too Large` | Fișier prea mare pentru MinIO (max configurat în prop = 50MB). |
| `TooManyRequestsException` | `429 Too Many Requests` | Rate Limiter (`RateLimiterService`) lovit — chei separate pentru chat/quiz/flashcards student (`"student-aky:" + studentId`) vs. chat persistat (`"conversatie:" + userId`). |
| `AccessDeniedException` (Spring Security, nu excepție custom) | `403 Forbidden` | Delegare directă din Spring Security (ex. `@PreAuthorize` picat), ca să nu cadă pe handler-ul generic 500. |
| `HttpRequestMethodNotSupportedException` | `405 Method Not Allowed` | Metodă HTTP nepermisă pentru endpoint. |

**Notă**: `KeycloakConflictException` a fost eliminată în runda de consolidare — orice eroare de la Keycloak (inclusiv conflicte) e acum mapată uniform la `KeycloakIntegrationException` (502).

### Erori Server / Erori Integrate (5xx)
| Clasa Excepției | HTTP Status | Când este aruncată |
| :--- | :--- | :--- |
| `KeycloakIntegrationException` | `502 Bad Gateway` | Orice eroare la comunicarea cu Keycloak (network, 4xx/5xx de la Keycloak, inclusiv fostele conflicte). |
| `MinioIntegrationException` | `502 Bad Gateway` | O eroare neprinsă, problemă conexiune IO cu S3/MinIO. |
| `RagChatException` | `502 Bad Gateway` | Serverul FastAPI RAG răspunde cu eroare sau cedează (timeout/401 desincronizare secret). |
| *Orice excepție generală (`Exception`)* | `500 Internal Error` | Fallback universal de siguranță în handler. |

## Catalogul complet al claselor din `exception/`

`DocumentDuplicatException`, `EmailDuplicatException`, `ForbiddenOperationException`, `GlobalExceptionHandler`, `IncercareQuizFinalizataException`, `InvalidUserStateException`, `KeycloakIntegrationException`, `MinioIntegrationException`, `RagChatException`, `ResourceConflictException`, `ResursaNegasitaException`, `SaptamanaConcurentaException`, `TooManyRequestsException`.
