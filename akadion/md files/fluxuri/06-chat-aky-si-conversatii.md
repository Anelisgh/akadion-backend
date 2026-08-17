# Flux 6: Chat cu Aky (AI) — Persistat și în Timp Real

> Actori: **Student** (ambele moduri) + **Profesor** (doar mod persistat).
> Referințe tehnice complete: `../services.md` §9.3 și §11, `../api-controllers.md` §7.3 și §8, `../business-rules.md` §5, `../contract-rag.md`, `../istoric-chat-rag-docs.md`.

---

## De ce există DOUĂ moduri de chat diferite

Aplicația are **două servicii de chat complet separate**, care ambele vorbesc cu același backend AI extern (RAG/FastAPI + Gemini), dar diferă fundamental:

| | Chat **persistat** (`ConversatieService`) | Chat **în timp real** student (`StudentAkyService`) |
|---|---|---|
| Cine îl folosește | Student ȘI Profesor | Doar Student |
| Salvat în DB? | Da — `Conversatie` + `MesajChat`, istoric complet | Nu — răspunsul se afișează și dispare, nimic persistat |
| `maxSaptamanaParcursa` trimis la RAG | `100` implicit (practic fără limitare) | Valoarea reală, calculată din progresul studentului |
| Endpoint | `/api/conversatii/...` | `/api/student/cursuri/{cursId}/chat` |
| Rate limit (cheie) | `"conversatie:" + userId` | `"student-aky:" + studentId` (comun cu quiz + flashcards) |

**De ce diferă `maxSaptamanaParcursa`:** Când un **profesor** întreabă ceva (fie prin chat-ul propriu din `CursProfesorController`, fie prin conversații persistate), el e proprietarul întregului conținut al cursului — nu are sens să-l limitezi la "ce a parcurs", pentru că el nu "parcurge" cursul, îl predă. La fel, chat-ul persistat (folosit de ambele roluri pentru istoric pe termen lung) trimite implicit `100` — practic fără limitare de progres, indiferent cine îl folosește. **Doar chat-ul rapid, în timp real, al studentului** aplică limitarea reală de progres (`determinaSaptamanaParcursaMax`, vezi flux 5) — pentru că acolo vrei să previi ca un student din săptămâna 2 să primească răspunsuri bazate pe materia săptămânii 9, ceea ce ar submina scopul pedagogic al progresului secvențial.

**De ce persistat rămâne nelimitat chiar și pentru student:** aceasta e o nuanță reală în design — conversațiile persistate din `ConversatieService` nu recalculează limita per-mesaj (rămân pe overload-ul cu 3 argumente al `RagChatService.intreabaAky`, implicit 100). Practic, limitarea strictă de progres se aplică doar fluxului rapid "Aky" din widget-ul de curs, nu și modulului de conversații istorice.

---

## Chat persistat (`ConversatieService`) — orchestrare în 3 pași

### De ce în 3 pași și nu într-o singură tranzacție

Un apel HTTP către serviciul RAG extern poate dura câteva secunde (generare LLM). Dacă am ține o tranzacție DB deschisă pe toată durata acelui apel, am bloca o conexiune din connection pool pentru fiecare chat activ simultan — la trafic mai mare, ai epuiza pool-ul de conexiuni DB și ai bloca toată aplicația, nu doar chat-ul. Soluția: separă explicit partea „DB" de partea „rețea externă lentă".

**Pas 1 — Salvare întrebare (`@Transactional`):**
Mesajul utilizatorului se salvează imediat cu `rol=UTILIZATOR`, `areRaspuns=false`. Dacă e prima întrebare a unei conversații noi, se creează concomitent entitatea `Conversatie` (titlul = primele ~40 de caractere ale întrebării). Tranzacția se închide aici — mesajul e deja vizibil în istoric chiar dacă restul flow-ului eșuează.

**Pas 2 — Apelul RAG (fără `@Transactional`):**
Se apelează `RagChatService.intreabaAky`. Nicio conexiune DB nu stă rezervată în acest timp.

**Pas 3 — Salvare răspuns (`@Transactional`):**
Dacă RAG a răspuns cu succes, se salvează un `MesajChat` nou cu `rol=ASISTENT`, și se marchează mesajul original al utilizatorului `areRaspuns=true`. Dacă RAG a eșuat (eroare, timeout), **nu se salvează nimic nou** — mesajul utilizatorului rămâne cu `areRaspuns=false`.

### Mecanismul de retry — de ce nu se duplică întrebarea

Dacă pasul 2 eșuează, utilizatorul vede un mesaj marcat vizual ca "fără răspuns" (⚠️ în UI, pe baza `areRaspuns=false`). Are un buton "Reîncearcă". Apăsarea lui apelează `POST /api/conversatii/mesaje/{mesajId}/retry`, care reia pașii 2-3 **pentru mesajul deja existent** — nu creează un mesaj nou de întrebare. Motivul: fără acest mecanism, fiecare retry ar dubla întrebarea în istoric, poluând conversația și confuzând contextul trimis la RAG la mesajele viitoare (care include istoricul).

### Ștergere conversație

`DELETE /api/conversatii/{id}` — soft-delete (`activ=false`). Istoricul rămâne în DB (nu se pierde definitiv, util pentru audit/recuperare), dar dispare din listele utilizatorului.

---

## Chat în timp real (`StudentAkyService`) — mai simplu, dar cu grounding strict

**Ce se întâmplă:** Studentul scrie o întrebare în widget-ul de curs, primește răspuns direct, fără să rămână salvat.

**Cum:**
1. `POST /api/student/cursuri/{cursId}/chat`, body `AkyChatRequestDto (intrebare, istoricConversatie)` — istoricul e ținut **pe frontend** (în memoria sesiunii de chat curente), nu în DB.
2. Verifică rate limit (cheie comună cu quiz+flashcards, vezi mai jos).
3. Calculează `maxSaptamanaParcursa` real din progresul studentului (`StudentCursService.determinaSaptamanaParcursaMax`, flux 5).
4. Apelează RAG cu acest plafon — RAG filtrează contextul vectorial doar la documentele din săptămânile ≤ plafon.
5. Returnează direct `AkyChatResponseDto (raspuns, surseFolosite)`.

**De ce nu e persistat:** E gândit ca un asistent rapid de studiu, "de moment" — nu ca istoric oficial. Dacă studentul vrea o conversație salvată permanent, folosește modul persistat (`/api/conversatii/...`), disponibil ambelor roluri.

---

## Rate limiting — chei partajate, nu globale

`RateLimiterService`: fereastră glisantă in-memory (`ConcurrentHashMap<String, Deque<Instant>>`), limită **10 cereri/minut per cheie**.

- `"student-aky:" + studentId` — **partajată** între chat în timp real, generare quiz, și generare flashcards. Un student care epuizează limita generând quiz-uri repetat nu mai poate nici întreba Aky, nici genera flashcards, timp de restul ferestrei de 1 minut — sunt considerate aceeași "resursă" (apeluri costisitoare către Gemini).
- `"conversatie:" + userId` — separată, doar pentru chat-ul persistat.

**De ce sunt separate exact așa (și nu, de exemplu, o singură limită globală per user):** chat-ul persistat e folosit și de profesori (care n-ar trebui limitați de activitatea de quiz a studenților, activitate care nici nu există pentru ei), iar în cadrul contului de student, cele trei funcții (chat rapid, quiz, flashcards) sunt considerate aceeași categorie de "cost AI" — grupate intenționat sub o singură limită, ca să nu poată ocoli rate limiting-ul comutând între ele.

---

## Diagramă (chat persistat, mesaj nou)

```
POST /conversatii/{id}/mesaje  {intrebare}
        │
        ▼
PAS 1 (@Transactional): salvează MesajChat(rol=UTILIZATOR, areRaspuns=false)
        │  ← tranzacție închisă aici
        ▼
PAS 2 (fără tranzacție): RagChatService.intreabaAky(...) [poate dura secunde]
        │
   ┌────┴─────┐
 succes      eșec/timeout
   │            │
   ▼            ▼
PAS 3: salvează        rămâne areRaspuns=false
MesajChat(ASISTENT)      → user vede ⚠️, poate apăsa "Reîncearcă"
+ marchează               → POST /mesaje/{id}/retry reia PAS 2-3
areRaspuns=true            fără mesaj nou de întrebare
```

---

## Ce poate merge prost

| Situație | Ce se întâmplă | Cod |
|---|---|---|
| Peste 10 cereri/minut pe aceeași cheie | `TooManyRequestsException` | 429 |
| RAG jos/timeout la un mesaj nou | Mesajul rămâne `areRaspuns=false`, recuperabil prin retry | 200 (parțial) apoi retry disponibil |
| Retry pe mesaj care nu aparține userului curent | `ForbiddenOperationException` | 403 |
| Student întreabă despre un curs la care nu e înrolat activ | Acces blocat | 403 |
| Student generează chat despre materia unei săptămâni neparcurse | RAG filtrează automat contextul la `maxSaptamanaParcursa` — nu vede acel conținut, nu e o eroare explicită | 200 (răspuns limitat la context disponibil) |
