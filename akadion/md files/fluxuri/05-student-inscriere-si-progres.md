# Flux 5: Înscriere, Retragere și Progres Student

> Actor: **Student**.
> Referințe tehnice complete: `../services.md` §9.1, `../api-controllers.md` §7.1, `../business-rules.md` §5, `../README-ENTITATI.md` §3.5-3.6.

---

## Ideea de ansamblu

Acest flux e fundația pe care se construiesc toate celelalte funcții pentru student (chat, quiz, flashcards — fluxuri 6 și 7): **fără o înrolare activă, nu ai acces la nimic din curs**, iar **progresul bifat determină până unde AI-ul "are voie" să te ajute**. `StudentCursService` e nucleul care rămâne cu această responsabilitate după despărțirea din 2026-08-12/13 (vezi `../services.md` §9), și expune calculul de progres și celorlalte două servicii (quiz, chat) tocmai ca acest calcul să nu se dubleze în trei locuri.

---

## Descoperire și înscriere

**Ce se întâmplă:** Studentul răsfoiește lista de cursuri disponibile și se înscrie la unul.

**Cum:**
- `GET /api/student/cursuri/disponibile` → listează cursurile `activ=true` la care studentul **nu** e deja înrolat activ.
- `POST /api/student/cursuri/{cursId}/inscriere` → `StudentCursService.inscriereCurs`.

**Logica de "upsert" la înscriere — de ce nu e un simplu INSERT:** Tabela `UserCurs` are o constrângere unică pe `(studentId, cursId)` — un student nu poate avea două rânduri de înrolare pentru același curs, indiferent câte ori se înscrie/retrage. Așa că `inscriereCurs` caută întâi dacă există deja un istoric (chiar inactiv, de la o retragere anterioară): dacă da, doar reactivează (`activ=true`) rândul existent; dacă nu, inserează unul nou.

**De ce contează asta:** Dacă un student s-a retras dintr-un curs și apoi se reînscrie luni mai târziu, progresul lui vechi (bifele din `Parcurs`, legate de `UserCurs` prin foreign key) **rămâne intact** — pentru că e același rând `UserCurs`, doar reactivat, nu unul nou. Dacă am fi creat mereu un rând nou la înscriere, ar fi trebuit fie să încalce constrângerea unică, fie să piardă istoricul de progres la fiecare retragere.

## Retragere

`POST /api/student/cursuri/{cursId}/retragere` → setează `activ=false` pe înrolare. Nu șterge nimic — progresul rămâne în DB, latent, gata să fie reluat la o reînscriere (vezi mai sus).

---

## Progres — bifarea săptămânilor

**Ce se întâmplă:** Studentul parcurge materialele unei săptămâni și o marchează manual ca "finalizată".

**Cum:**
- `POST /api/student/saptamani/{saptamanaId}/complete` → validează întâi că studentul e înrolat activ la cursul din care face parte săptămâna, apoi inserează un rând în `Parcurs` (legătura `UserCurs` + `Saptamana`, cu constrângere unică — nu poți bifa aceeași săptămână de două ori).
- `DELETE /api/student/saptamani/{saptamanaId}/complete` → șterge rândul din `Parcurs` (de-bifare).

**De ce bifarea e manuală, nu automată la simpla vizualizare a documentului:** Aplicația nu are cum să detecteze tehnic "a citit efectiv materialul" — bifarea manuală e un act conștient al studentului, folosit apoi ca semnal de progres pentru limitarea AI-ului (secțiunea următoare). E o decizie de încredere în autoraportare, nu de tracking automat.

**Calculul procentului de progres** (afișat pe `GET /api/student/cursuri/mele`): `countSaptamaniCompletate / totalSaptamaniCurs × 100`.

---

## De ce contează progresul dincolo de UI: `determinaSaptamanaParcursaMax`

Aceasta e cea mai importantă funcție expusă de acest flux către restul aplicației. Formula: **cea mai mare săptămână bifată + 1** (minim 1), plafonată la numărul total de săptămâni ale cursului.

**De ce +1 și nu direct numărul bifat:** Dacă un student a bifat săptămâna 3 ca finalizată, înseamnă că a parcurs-o deja — logic, ar trebui să aibă acces și la materialul săptămânii 4 (pe care încă n-a bifat-o, dar probabil o parcurge acum). Plafonarea la total previne overflow dacă studentul a bifat ultima săptămână.

Această valoare e trimisă la RAG ca `maxSaptamanaParcursa` de fiecare dată când **studentul** (nu profesorul) interoghează chat-ul, generează un quiz sau flashcards — vezi flux 6 și 7 pentru cum limitează exact conținutul AI-ului la ce studentul "a dreptul" să vadă deja. E mecanismul central prin care aplicația împiedică un student din săptămâna 2 să genereze quiz-uri din materia săptămânii 9.

---

## Acces la documente

`GET /api/student/saptamani/{saptamanaId}/documente` — listează doar documentele `activ=true` (cele soft-șterse de profesor nu apar).
`GET /api/student/cursuri/{cursId}/documente-accesibile` — folosit de widget-ul de chat pentru a arăta ce surse sunt disponibile: listează documentele din săptămânile **≤ maxSaptamanaParcursa**, aceeași regulă de plafonare ca mai sus.

---

## Diagramă

```
Student vede "Cursuri disponibile" (activ=true, fără înrolare activă)
        │ POST /inscriere
        ▼
UserCurs există deja (inactiv, din trecut)?
   ├─ da  ──> reactivează (activ=true), progresul vechi din Parcurs rămâne valabil
   └─ nu  ──> inserează UserCurs nou

Student bifează săptămâna N ca finalizată
        │ POST /saptamani/{id}/complete
        ▼
Parcurs: nou rând (UserCurs, Saptamana=N)
        │
        ▼
determinaSaptamanaParcursaMax = max(N bifat) + 1, plafonat la total
        │
        ▼
folosit ca "maxSaptamanaParcursa" trimis la RAG în chat/quiz/flashcards student
```

---

## Ce poate merge prost

| Situație | Ce se întâmplă | Cod |
|---|---|---|
| Student încearcă să se înscrie la un curs deja înscris activ | `IllegalArgumentException` | 400 |
| Student încearcă să se înscrie la curs `activ=false` | `IllegalArgumentException` | 400 |
| Bifare progres pe curs la care nu e înrolat | Blocat (validare ownership înrolare) | 403/400 |
| Bifare aceeași săptămână de două ori | Constrângere unică pe `Parcurs` respectă doar prima | 409/no-op după caz |
