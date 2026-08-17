# Flux 7: Quiz și Flashcards

> Actor: **Student**.
> Referințe tehnice complete: `../services.md` §9.2 și §9.4, `../api-controllers.md` §7.2 și §7.3, `../README-ENTITATI.md` §3.9, `../QUIZ_SI_FLASHCARDS_DOCUMENTATIE.md`.

---

## Quiz — generare, susținere, corectare

### De ce e nevoie de o entitate persistată (`IncercareQuiz`) și nu doar de un răspuns efemer

Spre deosebire de flashcards (simplu Q&A afișat și uitat), un quiz trebuie **evaluat** și **istoricizat** — studentul vrea să-și vadă scorul mai târziu, iar profesorul/adminul ar putea avea nevoie de dovada că testul a fost susținut o singură dată, corect. De aceea quiz-ul e un ciclu de viață în două stări (`GENERATA → FINALIZATA`), nu un simplu request-response.

### Pasul 1 — Generare

`POST /api/student/cursuri/{cursId}/quiz/generate`, body `QuizGenerateRequestDto (documentId, nrIntrebari)`.

**Cum:** `StudentQuizService.genereazaQuiz`:
1. Verifică rate limit (cheie comună cu chat+flashcards, vezi flux 6).
2. Calculează `maxSaptamanaParcursa` real (aceeași funcție ca la chat student, flux 5) — deci quiz-ul e generat **doar** din materia deja parcursă, niciodată din săptămâni viitoare.
3. Apelează RAG (`ragChatService.genereazaQuiz`, implicit 5 întrebări, dificultate "MEDIU" dacă nu se specifică altfel).
4. Salvează un rând nou `IncercareQuiz` cu `status=GENERATA`, conținând întrebările+opțiunile în `detaliiJson`.

**De ce generarea e limitată la progres (la fel ca la chat):** un quiz generat din materie neparcursă ar fi pedagogic incoerent — ar testa studentul pe ceva ce sistemul însuși nu l-a lăsat să învețe încă.

### Pasul 2 — Finalizare (corectare)

`POST /api/student/quiz/{incercareId}/finalizeaza`, body `FinalizeazaQuizRequestDto (raspunsuri: [{index, raspunsStudent}])`.

**Cum:** `StudentQuizService.finalizeazaQuiz`:
1. `findByIdForUpdate` — **lock pesimist** pe rândul `IncercareQuiz`. Motivul: previne finalizarea concurentă dublă (dublu-click, sau două tab-uri deschise pe același quiz trimițând simultan). Fără lock, ambele request-uri ar putea trece verificarea de stare înainte ca vreunul să apuce să scrie `FINALIZATA`, rezultând scor suprascris/inconsistent.
2. Dacă starea e deja `FINALIZATA`, respinge cu `IncercareQuizFinalizataException` (409) — un quiz nu poate fi retrimis.
3. Cere exact `nrIntrebari` răspunsuri, cu index unic și în intervalul valid.
4. Deleagă corectitudinea fiecărei întrebări către `QuizGradingService.isQuizAnswerCorrect` (vezi mai jos).
5. Actualizează `IncercareQuiz`: `status=FINALIZATA`, `scor` = numărul de răspunsuri corecte.

**Detaliu important și ireversibil:** câmpul `detaliiJson` e **suprascris** la finalizare cu feedback-ul complet (răspunsurile date + corectitudine + explicații) — structura originală de generare (întrebările "curate", fără răspunsurile studentului) **nu mai e recuperabilă** după acest punct. E o decizie deliberată de simplitate a schemei (un singur câmp JSON, nu două paralele), acceptând acest cost.

### `QuizGradingService` — de ce e o clasă separată, fără dependențe Spring

Extrasă din `StudentQuizService` special pentru testabilitate izolată — normalizarea datelor de quiz venite din RAG (un `Map<String,Object>` cu structură nesigură, pentru că vine dintr-un sistem extern, nu dintr-un contract Java strict tipizat) e logică pură, fără nevoie de context Spring/DB. `isQuizAnswerCorrect` face verificarea în 3 pași, tolerant la variații de format din partea RAG:
1. Compară litera aleasă de student cu litera corectă.
2. Dacă nu se potrivesc direct, compară valoarea aleasă cu valoarea corectă.
3. Fallback: caută litera care corespunde valorii corecte și o compară cu litera aleasă.

Fiecare pas normalizează case și spații — o dovadă că răspunsul de la Gemini (prin RAG) nu vine garantat 100% în același format de fiecare dată, iar codul e defensiv față de asta.

### Istoric

`GET /api/student/quiz/istoric?cursId=&page=&size=` — listează doar încercările **finalizate**. `GET /api/student/quiz/istoric/{id}` — detalii complete (întrebări, răspunsuri date, corectitudine).

### Ștergere

`DELETE /api/student/quiz/{incercareId}` — studentul își poate șterge propriile încercări, indiferent de status (`GENERATA` sau `FINALIZATA`). Verifică ownership (`ForbiddenOperationException` dacă nu-i aparține).

---

## Flashcards

**Ce se întâmplă:** Studentul generează un set de fișe Întrebare/Răspuns dintr-un document (sau din toate documentele accesibile).

**Cum:** `POST /api/student/cursuri/{cursId}/flashcards/generate`, body `FlashcardGenerateRequestDto (documentId opțional, nrFlashcards 1-20, implicit 5)` → `StudentAkyService.genereazaFlashcards`:
1. Dacă `documentId` e specificat, validează: documentul e activ, aparține cursului, și `nrSaptamana` ≤ `maxSaptamana` (aceeași regulă de progres).
2. Aplică rate limiting comun (aceeași cheie ca la chat+quiz).
3. Apelează RAG, returnează lista brută (`List<Map<String,Object>>`, serializată direct de Spring ca array JSON).

**De ce flashcards NU sunt persistate (spre deosebire de quiz):** Nu există un "scor" sau o corectitudine de urmărit — sunt un instrument de studiu momentan (repetiție spațiată manuală, în capul studentului), nu un test evaluabil. Deci nu are sens o entitate DB dedicată; e un simplu request-response, similar chat-ului în timp real (flux 6), nu ciclului `GENERATA→FINALIZATA` al quiz-ului.

---

## Diagramă (ciclul de viață al unui quiz)

```
POST /quiz/generate {documentId?, nrIntrebari}
        │
        ▼
rate limit + maxSaptamanaParcursa (progres real student)
        │
        ▼
RAG genereazaQuiz(...) ──> întrebări + opțiuni
        │
        ▼
IncercareQuiz(status=GENERATA, detaliiJson={întrebări curate})
        │
        │  studentul răspunde offline, apoi:
        ▼
POST /quiz/{id}/finalizeaza {raspunsuri[]}
        │
        ▼
findByIdForUpdate (lock pesimist — anti dublă finalizare)
        │
   ┌────┴────┐
GENERATA   deja FINALIZATA
   │            │
   ▼            ▼
QuizGradingService.isQuizAnswerCorrect (per întrebare)
   │
   ▼
IncercareQuiz(status=FINALIZATA, scor=N, detaliiJson={SUPRASCRIS cu feedback})
```

---

## Ce poate merge prost

| Situație | Ce se întâmplă | Cod |
|---|---|---|
| Finalizare pe quiz deja `FINALIZATA` | `IncercareQuizFinalizataException` | 409 |
| Finalizare dublă simultană (dublu-click) | Lock pesimist serializează — a doua cerere vede deja `FINALIZATA` | 409 |
| Număr de răspunsuri diferit de `nrIntrebari` | Validare respinsă | 400 |
| Generare quiz/flashcards din document dintr-o săptămână neparcursă | Blocat la validare (`documentId` explicit) | 400/403 |
| Peste 10 cereri/minut (comun cu chat) | `TooManyRequestsException` | 429 |
| Ștergere quiz care nu aparține studentului curent | `ForbiddenOperationException` | 403 |
