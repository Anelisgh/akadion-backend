# Flux 4: Gestionarea Cursului — Curs, Săptămâni, Documente și Pipeline RAG

> Actor principal: **Profesor** (owner al cursului). Adminul are acces read-only peste tot ce urmează (vezi flux 2).
> Referințe tehnice complete: `../services.md` §3-4-8, `../api-controllers.md` §3-4-6, `../business-rules.md` §2-3-4, `../contract-rag.md`, `../istoric-chat-rag-docs.md` (Partea 1).

---

## Ideea de ansamblu

Un curs e o ierarhie strictă: **Curs → Săptămâni → Documente**. Fiecare nivel există doar în contextul părintelui lui, iar structura asta cronologică (săptămâni numerotate secvențial) e ceea ce permite mai târziu limitarea progresivă a AI-ului la ce a "parcurs" un student (vezi flux 5 și 6) — un student din săptămâna 3 nu poate genera quiz-uri sau întreba Aky despre materia din săptămâna 7.

Toate operațiunile de scriere pe curs/săptămână/document verifică **ownership**: profesorul trimis în request trebuie să coincidă cu `curs.profesor.id`, altfel `ForbiddenOperationException` (403). Această verificare e centralizată în `CursOwnershipValidator` (extrasă dintr-un cod inițial duplicat în 3 servicii diferite) — adminul e "by-passat" doar pe operațiunile read-only.

---

## Curs — creare, editare, activare/dezactivare

**Creare:** `POST /api/profesor/cursuri` (doar `PROFESOR`) cu `CursRequestDto (denumire, descriere, dataInceput)`. Cursul pornește `activ=true`, fără `dataSfarsit` (nu există încă săptămâni din care s-o calculeze).

**Editare:** `PUT /api/profesor/cursuri/{id}`. Dacă `dataInceput` s-a schimbat, se recalculează automat `dataSfarsit`.

**De ce `dataSfarsit` nu se introduce niciodată manual:** E derivată strict din formula `dataInceput + (numărul curent de săptămâni × 7 zile) - 1 zi`. Motivul: dacă profesorul ar putea introduce orice dată de final, ea s-ar putea desincroniza de conținutul real al cursului (ex: curs cu 10 săptămâni dar `dataSfarsit` setată pentru doar 3). Recalcularea automată garantează consistență — se declanșează la orice adăugare/ștergere de săptămână, sau la modificarea datei de început.

**Dezactivare (`PATCH /{id}/dezactiveaza`):** Cursul trece `activ=false`, și **toate înrolările active ale studenților** trec și ele `activ=false` (cascadă). Motivul cascadei: un curs dezactivat nu mai trebuie să apară în lista "cursurile mele" a niciunui student, dar istoricul de progres (`Parcurs`) rămâne intact — dacă profesorul redeschide cursul, datele vechi nu s-au pierdut, doar accesul curent.

**Activare (`PATCH /{id}/activeaza`):** Doar cursul devine public din nou. **Înrolările NU se reactivează automat** — studenții trebuie să se reînscrie conștient. Aceeași filozofie ca la reactivarea unui profesor (flux 2): nu presupune automat că totul poate reporni identic ca înainte.

---

## Săptămâni — auto-numerotare și ștergere restrictivă

**Adăugare:** `POST /api/profesor/cursuri/{cursId}/saptamani` cu `SaptamanaRequestDto (descriere)`. **Numărul săptămânii nu vine din frontend** — se calculează pe server: cauți maximul curent pe acel curs, aduni 1. Adăugarea declanșează automat recalcularea `dataSfarsit` a cursului.

**De ce numerotarea e strict server-side:** Dacă frontend-ul ar trimite numărul, ar putea apărea găuri sau duplicate în secvență (ex: două tab-uri deschise simultan trimit ambele "săptămâna 5"). Server-ul garantează secvențialitate. Există și o excepție dedicată pentru race condition (`SaptamanaConcurentaException`, 409) dacă doi profesori/tab-uri reușesc totuși să lovească simultan constrângerea unică `(id_curs, nr_saptamana)`.

**Editare:** `PUT /api/profesor/saptamani/{id}` — modifică **strict descrierea**. Numărul săptămânii e imuabil odată creat.

**Ștergere — cea mai strictă regulă din toată aplicația:** `DELETE /api/profesor/saptamani/{id}` e permisă **doar pe ultima săptămână** din cronologia cursului (`IllegalArgumentException` altfel). De ce: dacă ai putea șterge o săptămână din mijloc, numerotarea (1, 2, 3, 4...) ar rămâne cu o gaură (1, 2, 4) sau ar necesita re-numerotarea tuturor săptămânilor de după — ceea ce ar sparge orice referință externă la ele (progresul deja bifat de studenți, quiz-urile deja generate din documentele acelei săptămâni, referințele din chat). Permițând ștergerea doar "de la coadă", secvența rămâne mereu curată.

Ștergerea ultimei săptămâni e un **hard delete în cascadă**, nu un soft-delete (spre deosebire de aproape orice altceva în aplicație):
1. Șterge toate bifele de progres (`Parcurs`) ale studenților pe acea săptămână.
2. Șterge documentele asociate — fizic din MinIO, din DB, **și** din indexul vectorial RAG (`DELETE /ingest/{id}`, best-effort).
3. Șterge rândul `Saptamana`.
4. Recalculează `dataSfarsit` a cursului.

**De ce hard-delete aici și nu soft-delete ca peste tot altundeva:** O săptămână ștearsă greșit prin soft-delete ar continua să "consume" un număr din secvență și ar complica orice logică de recalculare — iar fiindcă poate fi ștearsă doar dacă e goală de sens pentru progres viitor (e ultima), nu există motiv să păstrăm o "urmă" a ei.

---

## Documente — upload, înlocuire, ștergere, retry

### Upload (`POST /api/profesor/saptamani/{saptamanaId}/documente`, multipart)

1. Validare extensie: doar `pdf`, `docx`, `pptx`, `zip` (altfel `IllegalArgumentException`, 400).
2. Fișierul se calculează hash SHA-256 — dacă un fișier identic (bit-cu-bit) a fost deja urcat în aceeași săptămână, se respinge cu `DocumentDuplicatException` (409). Previne duplicate accidentale (ex: dublu-click pe "Upload").
3. Upload fizic în MinIO, la o cale deterministă: `curs-{cursId}/saptamana-{saptId}/{uuid}-{numeFisier}`.
4. Salvare `Document` în DB cu `statusIndex = PRELUAT`.
5. Apel **sincron** către RAG (`POST /ingest`), cu metadate complete (titlu, cale MinIO, extensie dedusă din cale, denumirea cursului, numărul săptămânii — vezi payload complet în `../contract-rag.md`).
6. Dacă RAG răspunde cu succes → `statusIndex = TRIMIS`. Dacă eșuează (eroare, timeout, offline) → `statusIndex = ERONAT`.

**De ce documentul rămâne salvat chiar dacă RAG eșuează:** Principiul central al integrării RAG, valabil peste tot în aplicație — **RAG e best-effort, nu tranzacțional**. Studentul tot poate descărca/vizualiza fișierul (funcție "materiale de curs" e independentă de AI). Doar chatbot-ul/quiz-ul/flashcards-urile "nu știu" de acel document până la reușita indexării. Asta decuplează disponibilitatea materialelor de disponibilitatea serviciului AI extern (Python/FastAPI), care poate fi mai fragil sau mai lent.

### Retry (`POST /api/profesor/documente/{id}/retry-ingest`)

Dacă un document a rămas `ERONAT`, profesorul poate retrimite manual cererea de indexare. Excepție dacă documentul e deja `TRIMIS` (nu are sens să re-indexezi ce a mers deja).

### Înlocuire fișier (`PUT /api/profesor/documente/{id}`, multipart, `file` opțional)

Dacă se trimite un fișier nou: upload noul fișier în MinIO → șterge vechiul din MinIO → retrimite la RAG (care face UPSERT — vezi mai jos) → actualizează titlul dacă s-a schimbat.

**Important despre UPSERT-ul din RAG:** contractul cu RAG (`../contract-rag.md` secțiunea A) specifică explicit că RAG **trebuie** să șteargă atomic vectorii vechi ai unui `documentId` înainte de a genera cei noi, la orice apel `POST /ingest` cu un ID deja existent. Backend-ul **nu** trebuie să apeleze DELETE explicit înaintea unui re-upload — decizie de design care evită o stare intermediară inconsistentă (unde ștergerea reușește dar noul upload eșuează, lăsând documentul fără nicio versiune indexată).

### Ștergere document (`DELETE /api/profesor/documente/{id}`) — soft-delete, spre deosebire de săptămâni

Setează `activ=false` în DB. **Nu se șterge fizic din MinIO** (rămâne arhivat — util pentru audit/recuperare). Se apelează totuși `DELETE /ingest/{id}` către RAG (best-effort) — vectorii chiar se șterg din indexul de căutare, ca asistentul AI să nu mai "știe" despre un document retras oficial, chiar dacă fișierul brut mai există în storage.

**De ce fișierul rămâne în MinIO dar dispare din RAG:** Sunt priorități diferite. Retragerea din vizibilitate/AI trebuie să fie imediată și completă (integritate academică — un document retras nu ar trebui să mai influențeze răspunsurile AI). Ștergerea fizică din storage e ireversibilă și costă — se face doar la ștergerea întregii săptămâni (hard-delete, vezi mai sus), nu la fiecare document individual.

---

## Diagramă (upload document)

```
POST /saptamani/{id}/documente (multipart: file, titlu)
        │
        ▼
validare extensie ── invalidă ──> 400
        │ ok
        ▼
hash SHA-256 ── duplicat în săptămână ──> 409 DocumentDuplicatException
        │ ok
        ▼
MinIO: upload fizic ──eroare──> 502 MinioIntegrationException (rollback: nimic salvat în DB)
        │ ok
        ▼
DB: salvează Document (statusIndex=PRELUAT)
        │
        ▼
RAG: POST /ingest (sincron, best-effort)
   ├─ succes ──> DB: statusIndex=TRIMIS
   └─ eșec/timeout ──> DB: statusIndex=ERONAT   (fișierul rămâne descărcabil oricum)
```

---

## Ce poate merge prost

| Situație | Ce se întâmplă | Cod |
|---|---|---|
| Extensie fișier nepermisă | `IllegalArgumentException` | 400 |
| Fișier identic deja urcat în săptămâna aceea | `DocumentDuplicatException` | 409 |
| MinIO indisponibil la upload | `MinioIntegrationException`, nimic salvat în DB | 502 |
| RAG indisponibil la upload | Document salvat cu `statusIndex=ERONAT`, disponibil pentru download | 200 (cu status intern eronat) |
| Profesor încearcă să șteargă o săptămână care nu e ultima | `IllegalArgumentException` | 400 |
| Doi profesori/tab-uri adaugă simultan o săptămână | `SaptamanaConcurentaException` | 409 |
| Profesor editează cursul altui profesor | `ForbiddenOperationException` (via `CursOwnershipValidator`) | 403 |
| Retry-ingest pe document deja `TRIMIS` | Excepție — nu are rost să re-indexezi | 400/409 |
