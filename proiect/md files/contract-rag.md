# Contract de Integrare Backend - RAG (FastAPI)

Acest document definește contractul de integrare și regulile de comunicare între backend-ul aplicației (Java / Spring Boot) și serviciul RAG (Python / FastAPI). 

---

## 1. Contextele de Integrare & Fluxul de Lucru

1. **MinIO (Stocare S3)**:
   - Toate fișierele încărcate de profesori sunt salvate în MinIO de către backend înainte de a fi trimise la RAG.
   - **Bucket**: `course-documents`
   - **Convenție Cheie (Path)**:  
     `curs-{cursId}/saptamana-{saptamanaId}/{uuid-random}-{nume-fisier-original}`
   - RAG va descărca direct fișierele din MinIO folosind această cheie pentru a le citi, extrage textul, procesa și genera embeddings.

2. **Proprietatea Tranzacțională (Best-effort)**:
   - Sincronizarea cu RAG se face în mod **best-effort**. Backend-ul nu va bloca fluxurile locale de bază de date sau de MinIO dacă API-ul RAG este temporar indisponibil.
   - Dacă RAG returnează eroare sau este offline, documentul se salvează cu succes în DB și MinIO, având starea `statusIndex = ERONAT`. Profesorul poate reîncerca sincronizarea ulterior printr-un apel manual.

---

## 2. Endpoint-uri API (RAG FastAPI)

### A. Adăugare și Actualizare Document (UPSERT)
* **Metodă**: `POST`
* **Cale**: `/ingest`
* **Content-Type**: `application/json`

#### Payload Request:
```json
{
  "documentId": 123,
  "cursId": 45,
  "saptamanaId": 12,
  "profesorId": 7,
  "titlu": "Curs 3 - Introducere in ORM",
  "pathMinio": "curs-45/saptamana-12/a1b2c3d4-curs3.pdf",
  "extensie": "pdf",
  "cursDenumire": "Baze de date avansate",
  "nrSaptamana": 3
}
```

> **Notă privind câmpul `extensie`**: Valoarea este extrasă și trimisă de **backend** (Spring Boot), nu de RAG.
> Backend-ul o deduce din `pathMinio` (ultimul segment după `.`, ex: `"curs-45/saptamana-12/a1b2c3d4-curs3.pdf"` → `"pdf"`).
> RAG-ul va primi întotdeauna un string lowercase non-null (în cazul extrem în care extensia lipsește, va primi `""`).

#### Reguli de comportament RAG:
* **Suprascriere atomică (UPSERT)**: Dacă `documentId` există deja în baza de date vectorială (cazul în care profesorul înlocuiește fișierul sau retrimite documentul), serviciul RAG **trebuie să șteargă automat și atomic toți vectorii/embeddings anteriori asociați cu acest `documentId`** înainte de a-i genera pe cei noi.
* Nu este necesar un apel explicit `/delete` din backend înaintea unui re-upload. Acest design previne stările inconsistente unde ștergerea reușește, dar upload-ul nou eșuează.

#### Răspunsuri:
* **`2xx` (Succes)**: RAG a preluat documentul pentru procesare. În backend, starea documentului va fi marcată ca `TRIMIS`.
* **Orice alt cod (4xx, 5xx, timeout)**: În backend, starea documentului devine `ERONAT`.

---

### B. Ștergere Document (DELETE)
* **Metodă**: `DELETE`
* **Cale**: `/ingest/{documentId}`

#### Reguli de comportament RAG:
* Elimină complet și definitiv din baza de date vectorială toate embeddings/vectorii asociați cu ID-ul documentului transmis în cale.
* Acest endpoint este apelat de backend în următoarele cazuri:
  1. Soft-delete-ul unui document (`ACTIV = false`).
  2. Ștergerea ultimei săptămâni dintr-un curs (care șterge în cascadă toate documentele asociate).
* **Robustitate**: Backend-ul apelează acest endpoint ca fiind *best-effort*. Dacă apelul eșuează sau dă timeout, backend-ul continuă execuția locală (fișierele/înregistrările locale sunt șterse cu succes din DB și MinIO).

---

### C. Interogare Chatbot (Generare Răspuns)
* **Metodă**: `POST`
* **Cale**: `/chat` (sau `/query`)
* **Content-Type**: `application/json`

Acest endpoint este apelat de backend atunci când un utilizator (student sau profesor) adresează o întrebare chatbot-ului dintr-un anumit curs.

#### Payload Request:
```json
{
  "studentId": 99,
  "cursId": 45,
  "maxSaptamanaParcursa": 4,
  "intrebare": "Ce este un EntityManager?",
  "istoricConversatie": [
    {"role": "user", "content": "Salut, am o intrebare despre ORM."},
    {"role": "assistant", "content": "Salut! Cu ce te pot ajuta legat de ORM?"}
  ]
}
```

> **Notă privind `maxSaptamanaParcursa`**: Backend-ul **trimite** acest câmp către RAG (adăugat 2026-08-12/13, `RagChatService.intreabaAky(userId, cursId, request, maxSaptamanaParcursa)`). Valoarea reflectă contextul apelantului:
> - **Chat student în timp real** (`StudentAkyController` → `StudentAkyService`) — valoare reală, calculată din progresul studentului (`StudentCursService.determinaSaptamanaParcursaMax`): limitează RAG-ul la documentele din săptămânile deja parcurse.
> - **Chat profesor** (`CursProfesorController`) și **chat persistat** (`ConversatieService`, atât pentru studenți cât și pentru profesori) — folosesc overload-ul cu 3 argumente, care trimite implicit `100` (`RagChatService.DEFAULT_MAX_SAPTAMANA_PROFESOR`), practic fără limitare de progres.
> **Notă privind `studentId`**: Cheia JSON trimisă efectiv către RAG a rămas `studentId`, deși parametrul Java se numește `userId` (poate fi ID-ul unui student sau al unui profesor — chatbot-ul poate fi interogat de ambii). Nu confunda numele parametrului din cod cu numele câmpului din payload.

#### Reguli de comportament RAG:
- **Filtrare pe Context**: RAG-ul aplică un filtru pe baza de date vectorială folosind `cursId`.
- **Istoric**: RAG-ul ia în considerare vectorul `istoricConversatie` pentru a menține contextul discuției curente.

#### Răspunsuri:
- **`200 OK`**: Returnează răspunsul generat de LLM.

```json
{
  "raspuns": "Un EntityManager este o interfață...",
  "surseFolosite": [123, 124]
}
```
*(Notă: `surseFolosite` este o listă opțională de `documentId`-uri sau obiecte `{documentId, numeFisier}` din care a extras informația, utilă pentru afișarea referințelor studentului).*

---


## 3. Stări Document pe Backend (`statusIndex`)

Fiecare document are o stare în baza de date locală reflectând stadiul lui în raport cu RAG:
1. `PRELUAT`: Documentul a fost salvat local și în MinIO, urmând să fie trimis la RAG.
2. `TRIMIS`: Apelul către `POST /ingest` a răspuns cu succes, iar documentul este acum indexat de RAG.
3. `ERONAT`: Apelul către `POST /ingest` a eșuat. Documentul rămâne disponibil studenților pentru descărcare, dar chatbot-ul nu va ști de el până când profesorul nu folosește butonul "Retry".
