# Prompt: Istoric Quiz-uri + Sistem de Notare (Backend) — v3

> Această versiune (v3) înlocuiește v2 și încorporează îmbunătățirea securității sanitizării răspunsului de generare (prevenirea scurgerii răspunsurilor corecte în DevTools), precum și blocarea pesimistă (`PESSIMISTIC_WRITE`) la finalizare pentru conservarea auditării JPA automate și prevenirea staleness-ului în cache-ul Hibernate L1.

## Context

Vrem un istoric persistent al încercărilor de quiz, cu notă per încercare, calculată server-side (nu de încredere din client, pentru că adminul vede aceste note). Fără agregare pe săptămână/curs. Profesorul nu vede notele. Adminul le vede.

---

## Fapte confirmate din cod & Reguli de Securitate

1. `QuizGenerateRequestDto.documentId` este `Long` **singular** (nu listă) — end-to-end, de la DTO și Service până la payload-ul trimis către RAG FastAPI.
2. **SANITIZAREA RĂSPUNSULUI LA GENERARE (CRITIC PENTRU SECURITATE)**: 
   - La `POST /quiz/generate`, backend-ul primește de la RAG întrebările complete cu `raspuns_corect` și `explicatie`.
   - Backend-ul persistă versiunea completă în DB (`detaliiJson`).
   - Backend-ul returnează către frontend o **proiecție sanitizată** (`QuizGenerateResponseDto`), care conține **DOAR `index`, `intrebare` și `optiuni`** (fără `raspuns_corect` și fără `explicatie`). Astfel, niciun utilizator nu poate afla răspunsurile corecte deschizând Network Tab în browser DevTools!
   - Explicațiile și marcarea răspunsurilor corecte/greșite sunt returnate clientului **DOAR la finalizare** (`POST /finalizeaza`).
3. Rate limiter-ul (`checkRateLimit`, `StudentCursService`) e un `ConcurrentHashMap` în memorie, comun chat + quiz generate (10/min). Endpoint-ul nou `finalizeaza` NU intră în acest rate limiter (nu apelează RAG).
4. Ultima migrare Flyway existentă: `V8__create_audit_log_table.sql` → următoarea migrare trebuie să fie `V9__create_incercari_quiz_table.sql`.
5. `BaseAuditableEntity` se extinde direct (vezi `Saptamana.java` ca model).
6. **Tabela de utilizatori se numește `app_user`**, nu `utilizatori`. FK-ul trebuie să refere `app_user(id)`.
7. `AkyChatWidget.jsx` trebuie actualizat structural și funcțional:
   - Nu mai citește local `question?.raspuns_corect` la generare.
   - Prelucrează răspunsul sanitizat de generare `{ incercareId, intrebari }`.
   - Trimite răspunsurile la `/finalizeaza` și randează feedback-ul (rezultatul + explicațiile) primit de la backend în răspunsul de finalizare.

## Decizii de business & Arhitectură

- Flow în 2 faze cu sanitizare strictă la generare.
- Fără limită de reîncercări; fiecare încercare finalizată rămâne un rând separat în istoric.
- Fără notă agregată pe săptămână/curs (v1 din acest ticket).
- **Prevenirea dublu-submit la finalizare: Lock Pesimist în DB (`PESSIMISTIC_WRITE`)**.
  - `SELECT ... FOR UPDATE` blochează rândul la citire în tranzacția de finalizare.
  - Verificare `status == GENERATA` în codul Java.
  - Modificare câmpuri pe entitate și salvare prin `repository.save(incercare)`.
  - **Avantaje**: 1) Previne 100% race condition la dublu-click, 2) Nu necesită coloană nouă `@Version`, 3) Păstrează auditarea JPA automată (`updatedAt` și `updatedBy` populate automat prin `@EntityListeners(AuditingEntityListener.class)`), 4) Păstrează cache-ul L1 Hibernate 100% consistent.
- **Întrebări necompletate (unanswered)**: Un `raspunsStudent` trimis ca `null` sau spații albe nu aruncă eroare, ci este marcat silențios ca fiind incorect (`este_corect = false`).
- **Reverificarea înrolării**: La finalizare se reverifică înrolarea activă a studentului la curs.
- **Ownership check**: 404 dacă `incercareId` nu există, 403 dacă există dar nu aparține studentului curent.

---

## 1. Model de date

### Entitate nouă: `IncercareQuiz` (tabelă `incercari_quiz`)

Extinde `BaseAuditableEntity`.

| Câmp | Tip | Note |
|---|---|---|
| `id` | `Long` (PK) | |
| `student` | `@ManyToOne` → `User`, NOT NULL | `app_user(id)` FK, `ON DELETE CASCADE` |
| `curs` | `@ManyToOne` → `Curs`, NOT NULL | `cursuri(id)` FK, `ON DELETE CASCADE` |
| `document` | `@ManyToOne` → `Document`, nullable | `documente(id)` FK, `ON DELETE SET NULL` |
| `status` | Enum `StatusIncercareQuiz` (`GENERATA`, `FINALIZATA`), NOT NULL | |
| `nrIntrebari` | `Integer`, NOT NULL | |
| `scor` | `Integer`, nullable | Populat doar la finalizare. |
| `detaliiJson` | `String`, mapat cu `@JdbcTypeCode(SqlTypes.JSON)` (Hibernate 6 / Spring Boot 3), coloană `JSONB`, NOT NULL | Structura internă `snake_case`: `[{index, intrebare, optiuni, raspuns_corect, explicatie}]` la generare. La finalizare se adaugă `raspuns_student` și `este_corect` per intrare. |

Indecși compuși:
- `(id_student, status, created_at DESC)`
- `(id_curs, status, created_at DESC)`

### Enum nou: `StatusIncercareQuiz`
`GENERATA`, `FINALIZATA`.

### Migrare `V9__create_incercari_quiz_table.sql`

```sql
CREATE TABLE incercari_quiz (
    id BIGSERIAL PRIMARY KEY,
    id_student BIGINT NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    id_curs BIGINT NOT NULL REFERENCES cursuri(id) ON DELETE CASCADE,
    id_document BIGINT REFERENCES documente(id) ON DELETE SET NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('GENERATA', 'FINALIZATA')),
    nr_intrebari INT NOT NULL,
    scor INT,
    detalii_json JSONB NOT NULL,
    created_by VARCHAR(36),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by VARCHAR(36),
    updated_at TIMESTAMPTZ
);

CREATE INDEX idx_incercari_quiz_student_status ON incercari_quiz (id_student, status, created_at DESC);
CREATE INDEX idx_incercari_quiz_curs_status ON incercari_quiz (id_curs, status, created_at DESC);
```

---

## 2. Flow-ul în 2 faze

### Faza 1 — Generare (modifică endpoint-ul existent)
`POST /api/student/cursuri/{cursId}/quiz/generate`

- Validările existente (înrolare, `maxSaptamanaParcursa`, rate limiting) rămân neschimbate, la fel și apelul către RAG — **extern, în afara tranzacției DB**.
- După ce RAG răspunde cu succes: salvează un rând nou `IncercareQuiz` (`status = GENERATA`), într-o metodă separată, scurtă, `@Transactional`. `detaliiJson` stochează întrebările complete primite de la RAG (`raspuns_corect`, `explicatie`).
- Răspunsul către frontend devine `QuizGenerateResponseDto { incercareId, intrebari }`, unde `intrebari` conține `List<QuizQuestionProjectionDto>` (**DOAR `index`, `intrebare`, `optiuni`** — **fără `raspuns_corect` și fără `explicatie`**).
- Dacă RAG eșuează: comportament identic cu acum, niciun rând nou.

### Faza 2 — Finalizare (endpoint nou)
`POST /api/student/quiz/{incercareId}/finalizeaza`

**Request:** `FinalizeazaQuizRequestDto { raspunsuri: List<RaspunsIntrebareDto{ index, raspunsStudent }> }`

**Pași, în ordine (executați în metoda `@Transactional`):**
1. Caută `IncercareQuiz` cu lock pesimist:
   ```java
   @Lock(LockModeType.PESSIMISTIC_WRITE)
   @Query("SELECT i FROM IncercareQuiz i WHERE i.id = :id")
   Optional<IncercareQuiz> findByIdForUpdate(@Param("id") Long id);
   ```
   Dacă nu există → `ResursaNegasitaException` (404).
2. Verifică ownership (`incercare.getStudent().getId().equals(user.getId())`) → dacă diferă, `AccesInterzisException` (403).
3. Reverifică înrolarea activă a studentului la `incercare.getCurs()`.
4. Verifică `incercare.getStatus() == StatusIncercareQuiz.GENERATA` → dacă este deja `FINALIZATA`, aruncă `IncercareQuizFinalizataException` (409 Conflict).
5. Validează `raspunsuri`: exact `nrIntrebari` elemente, indecși unici, toți în intervalul `[0, nrIntrebari - 1]` → altfel `IllegalArgumentException` (400).
6. Calculează scorul și feedback-ul detaliat:
   - Pentru fiecare întrebare din `detaliiJson`:
     - Prelucrează `raspunsStudent` din DTO.
     - `boolean esteCorect = raspunsStudent != null && !raspunsStudent.isBlank() && raspunsStudent.trim().equalsIgnoreCase(raspunsCorect);`
     - Adaugă `raspuns_student` și `este_corect` în obiectul JSON al întrebării.
     - Dacă `esteCorect`, incrementează `scor`.
7. Actualizează entitatea: `incercare.setStatus(StatusIncercareQuiz.FINALIZATA)`, `incercare.setScor(scor)`, `incercare.setDetaliiJson(noulJson)`.
8. Apelează `repository.save(incercare)` — Spring Data JPA populează automat `updatedAt` și `updatedBy`.
9. Întoarce `QuizFinalizatResponseDto { incercareId, scor, nrIntrebari, procentaj, detalii }` (unde `detalii` conține feedback-ul complet per întrebare cu explicațiile).

### Încercări abandonate
Rândurile rămase `GENERATA` se filtrează prin `status = FINALIZATA` la interogări.

---

## 3. Endpoint-uri de citire

### Student
- `GET /api/student/quiz/istoric?cursId={opțional}&page=&size=` — doar `FINALIZATA`, doar ale studentului curent, paginat. Rezumat: `incercareId, cursDenumire, documentTitlu (nullable), scor, nrIntrebari, createdAt`.
- `GET /api/student/quiz/istoric/{incercareId}` — detaliu complet, inclusiv `detaliiJson` cu explicațiile. Verificări ownership (404/403).

### Admin — nou în `AdminController`
- `GET /api/admin/cursuri/{cursId}/quiz-note?page=&size=` — **paginat**, doar `FINALIZATA`. DTO: `studentNume, studentPrenume, scor, nrIntrebari, createdAt`. `@PreAuthorize("hasRole('ADMIN')")`.

### Profesor
Niciun endpoint nou.

---

## 4. Excepții noi

| Excepție | Status | Când |
|---|---|---|
| `IncercareQuizFinalizataException` | `409 Conflict` | La `finalizeaza`, dacă statusul este deja `FINALIZATA` |

Handler în `GlobalExceptionHandler`:
```java
@ExceptionHandler(IncercareQuizFinalizataException.class)
@ResponseStatus(HttpStatus.CONFLICT)
public Map<String, Object> handleQuizFinalizat(IncercareQuizFinalizataException ex) {
    return Map.of("status", HttpStatus.CONFLICT.value(), "eroare", ex.getMessage());
}
```

---

## 5. Criterii de acceptare

- [ ] Generarea creează un rând `IncercareQuiz` (`GENERATA`) doar dacă RAG răspunde cu succes.
- [ ] Răspunsul HTTP de la `POST /quiz/generate` este **sanitizat** și NU conține `raspuns_corect` sau `explicatie`.
- [ ] Un quiz `GENERATA` nu apare în istoricul studentului nici în notele adminului.
- [ ] Finalizarea calculează scorul server-side din răspunsurile corecte stocate în DB.
- [ ] Două cereri de `finalizeaza` trimise concurent sunt serializate prin `PESSIMISTIC_WRITE`; prima finalizează quiz-ul, a doua primește `409 Conflict`.
- [ ] Întrebările necompletate (`raspunsStudent` null/blank) sunt evaluate ca fiind incorecte (`este_corect = false`), fără eroare 500.
- [ ] Un student nu poate finaliza sau vizualiza încercarea altui student (403), și nici o încercare inexistentă (404).
- [ ] Finalizarea eșuează dacă studentul nu mai e înrolat activ la curs (403).
- [ ] Auditarea JPA (`updatedAt`/`updatedBy`) funcționează automat la finalizare.
- [ ] Profesorul nu are acces la niciun endpoint nou legat de note.
- [ ] Rate limiter-ul existent nu e afectat de `finalizeaza`.
- [ ] `GET /api/admin/cursuri/{cursId}/quiz-note` e paginat.
- [ ] `AkyChatWidget.jsx` este actualizat să preia DTO-ul sanitizat la generare și să randeze rezultatele/explicațiile primite de la `/finalizeaza`.
- [ ] Migrarea `V9__create_incercari_quiz_table.sql` folosește `REFERENCES app_user(id)`.