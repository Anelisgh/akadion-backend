# Jurnalul de Audit (Audit Log)

> Pentru povestea completă a fluxului (cine declanșează, ce se întâmplă pas cu pas, ce poate merge prost), vezi [`fluxuri/02-administrare-utilizatori-si-audit.md`](fluxuri/02-administrare-utilizatori-si-audit.md), secțiunea "Partea C — Audit Log". Acest document e referința tehnică completă: schema exactă, codul care scrie/citește, și motivația din spatele fiecărei decizii de design.

---

## 1. Ce este și de ce există

Audit Log-ul e jurnalul care reține **orice acțiune administrativă sau sensibilă** din aplicație: cine a aprobat/respins/dezactivat un cont, cine a creat/modificat/șters un curs, o săptămână sau un document. Scopul lui e să poată răspunde oricând, verificabil, la trei întrebări: **cine** a făcut o modificare, **când**, și **ce anume s-a schimbat** (valoarea dinainte și valoarea de după).

Fără acest jurnal, o dispută de tipul "cine mi-a dezactivat cursul?" sau "de ce contul meu a fost respins de două ori?" n-ar avea un răspuns verificabil în sistem — ar rămâne la nivel de "cred că" sau "cineva mi-a zis".

---

## 2. Unde se stochează (tabela `audit_log`, entitatea `AuditLog`)

Fișier: `admin/entity/AuditLog.java`.

| Coloană | Tip | Descriere |
|---|---|---|
| `id` | `Long` | PK, autoincrement |
| `nume_tabel` | Enum `NumeTabelAudit` | Ce entitate de business a fost afectată |
| `id_inregistrare` | `Long` | ID-ul rândului afectat din acea entitate |
| `operatie` | Enum `OperatieAudit` | Ce tip de acțiune s-a întâmplat |
| `utilizator` | `String` (max 36) | ID-ul Keycloak (`sub`) al autorului, sau `"system"` |
| `valori_vechi` | `jsonb`, nullable | Starea câmpurilor relevante *înainte* de modificare |
| `valori_noi` | `jsonb`, nullable | Starea câmpurilor relevante *după* modificare |
| `creat_la` | `OffsetDateTime` | Timestamp, setat automat, niciodată editabil ulterior |

**De ce `valori_vechi`/`valori_noi` sunt JSON și nu coloane fixe:** fiecare tip de acțiune schimbă câmpuri diferite (o aprobare schimbă `stare`, o editare de curs schimbă `denumire`/`descriere`, o schimbare de email schimbă `mail`). O structură JSON liberă evită să ai zeci de coloane goale pentru majoritatea rândurilor.

**De ce entitatea nu are `@Data`/`toString()` generat automat:** `valori_vechi`/`valori_noi` conțin frecvent date cu caracter personal (nume, email, facultate). Un `println(auditLog)` scăpat accidental într-un log de debug ar scurge aceste date direct în consolele serverului. Fără `toString()` automat, riscul dispare structural, nu doar prin disciplină de cod.

### Ce tipuri de entități/acțiuni apar (enumurile)

`nume_tabel` (`NumeTabelAudit`): `APP_USER`, `CURS`, `DOCUMENT`, `SAPTAMANA`, `USER_CURS`.

`operatie` (`OperatieAudit`): `CREARE`, `EDITARE`, `STERGERE`, `UPLOAD`, `INLOCUIRE`, `ACTIVARE`, `DEZACTIVARE`, `APROBARE`, `RESPINGERE`, `REACTIVARE`, `CREARE_CONT`, `EDITARE_PROFIL`, `SCHIMBARE_EMAIL`, `COMPLETARE_PROFIL`, `INSCRIERE`.

---

## 3. Cum se scrie un rând nou

Fișier: `admin/service/AuditLogService.java`, metoda `inregistreaza(...)`.

Orice serviciu care execută o acțiune ce trebuie jurnalizată apelează această metodă la finalul operațiunii. Exemplu real, din `AdminUserService.approveUser`, la aprobarea unui cont `PENDING`:

```java
auditLogService.inregistreaza(
        NumeTabelAudit.APP_USER,
        user.getId(),
        OperatieAudit.APROBARE,
        Map.of("stare", "PENDING"),   // valori vechi
        Map.of("stare", "ACTIV")      // valori noi
);
```

Rezultă un rând care spune, fără ambiguitate: entitatea `APP_USER` cu id-ul lui `user`, operația `APROBARE`, starea a trecut din `PENDING` în `ACTIV`, la ora X, făcută de utilizatorul Y.

### De ce metoda cere obligatoriu o tranzacție deja deschisă

`inregistreaza` e marcată `@Transactional(propagation = Propagation.MANDATORY)` — dacă e apelată din afara unei tranzacții active, aruncă eroare în loc să scrie oricum. Motivul: un log de audit scris **fără** ca operațiunea de bază să fi reușit efectiv (de exemplu dacă restul metodei face rollback după acel apel) ar fi un log mincinos — ar spune că ceva s-a întâmplat, deși de fapt tranzacția a fost anulată. Legând scrierea de audit de aceeași tranzacție ca operațiunea propriu-zisă, cele două reușesc sau eșuează împreună, mereu.

Această regulă are o consecință practică vizibilă în cod: acolo unde operațiunea de bază implică un apel de rețea lung (upload document → apel către RAG, sau dezactivare cont → apel către Keycloak), scrierea în audit se face într-o metodă `@Transactional` scurtă, separată, care acoperă *doar* partea locală de bază de date — apelul extern rămâne în afara ei (vezi §5 mai jos, și flux 4/2 pentru detalii pe fiecare caz).

---

## 4. Cine e "autorul" — și de ce nu poate fi falsificat din frontend

```java
String utilizator = auditorProvider.getCurrentAuditor().orElse("system");
```

`auditorProvider` citește direct din `SecurityContext` — contextul de securitate al sesiunii curente pe server, populat la autentificare pe baza tokenului validat de Keycloak. **Nu ia niciodată numele autorului din ce trimite requestul HTTP** (body, header sau parametru). Practic, dacă cineva ar încerca să manipuleze o cerere din browser ca să pretindă că acțiunea a fost făcută de altcineva, backend-ul ar ignora complet acea pretenție — se uită strict la identitatea validată prin tokenul de sesiune al celui care a făcut efectiv request-ul.

Dacă acțiunea are loc fără un utilizator autentificat (de exemplu la pornirea aplicației, când `DataSeeder` populează nomenclatoarele), autorul înregistrat e `"system"`.

Acesta e același mecanism folosit peste tot în aplicație pentru `createdBy`/`updatedBy` (vezi `configurari.md` §2.1, `AuditConfig`) — audit log-ul reutilizează exact aceeași sursă de adevăr, nu una separată.

---

## 5. Cum se citește (afișarea din dashboard-ul de admin)

`GET /api/admin/audit-log?page=0&size=20` → `AdminController` → `AuditLogService.getAuditLog(Pageable)`.

Pe lângă simpla paginare (`findAllByOrderByCreatedAtDesc`), metoda rezolvă și un detaliu de UX: în tabelă e stocat doar ID-ul Keycloak al autorului (un UUID ilizibil), dar dashboard-ul trebuie să afișeze un nume ("Ion Popescu"), nu un ID brut. În loc să facă o interogare separată pentru fiecare rând din pagină (ceea ce ar încetini semnificativ afișarea unei pagini cu 20 de rânduri — 20 de interogări suplimentare), metoda colectează întâi toate ID-urile Keycloak distincte din pagina curentă, apoi face **o singură interogare batch** (`userRepository.findByIdKeycloakIn(listaIds)`) care le rezolvă pe toate deodată. Rândurile cu autor `"system"` sunt afișate direct ca "Sistem", fără interogare.

---

## 6. Decizii de design care merită explicate

### Cascadele nu generează loguri individuale

Când o acțiune declanșează efecte în lanț — de exemplu ștergerea ultimei săptămâni a unui curs șterge automat și toate documentele ei asociate (vezi flux 4) — jurnalul înregistrează **o singură** intrare pentru acțiunea sursă ("Ștergere Săptămână"), nu câte una pentru fiecare document șters în cascadă. Codul reflectă asta direct: cascada apelează metodele de acces la date (`documentRepository.deleteAll(...)`, `parcursRepository.deleteAll(...)`) direct, ocolind intenționat metodele individuale auditate care ar fi scris un log per document. Exemplu real, din `SaptamanaService.stergeSaptamanaSiAuditeaza`:

```java
parcursRepository.deleteAll(parcursRepository.findBySaptamanaId(saptamana.getId()));
documentRepository.deleteAll(documente);
saptamanaRepository.delete(saptamana);
// ...
auditLogService.inregistreaza(
        NumeTabelAudit.SAPTAMANA, saptamana.getId(), OperatieAudit.STERGERE,
        Map.of("nrSaptamana", saptamana.getNrSaptamana(), "nrDocumenteAsociate", documente.size()),
        null
);
```

Numărul de documente șterse e păstrat ca detaliu în `valori_vechi` (`nrDocumenteAsociate`), dar fără să genereze rânduri separate pentru fiecare. Rezultatul: jurnalul rămâne citibil — o acțiune de admin apare ca o singură linie clară, nu ca o rafală de zeci de rânduri tehnice identice.

### Auditul reflectă succesul local, independent de răspunsul RAG

Upload-ul unui document e considerat "reușit" — și e auditat ca atare — pe baza faptului că fișierul a ajuns fizic în MinIO și rândul a fost salvat în baza de date, **indiferent** dacă serviciul extern RAG a răspuns cu succes sau eroare la indexare. E consecința directă a principiului "RAG e best-effort" (vezi `contract-rag.md` și flux 4): starea de sincronizare cu RAG (`statusIndex`) e informație separată, urmărită pe fiecare document, nu o condiție pentru ca acțiunea de upload să fie considerată validă în jurnal.

### Index dedicat pe coloana `utilizator`

Filtrarea din interfața de admin după un anumit utilizator (ex: "arată-mi tot ce a făcut profesorul X") e o operațiune frecventă în timp, pe măsură ce jurnalul crește la mii/zeci de mii de rânduri. Fără index pe această coloană, fiecare astfel de filtrare ar necesita o scanare completă a tabelei.

---

## 7. Fișiere implicate

| Fișier | Rol |
|---|---|
| `admin/entity/AuditLog.java` | Entitatea JPA / schema tabelei `audit_log` |
| `admin/entity/NumeTabelAudit.java` | Enum — ce entitate a fost afectată |
| `admin/entity/OperatieAudit.java` | Enum — ce tip de acțiune |
| `admin/repository/AuditLogRepository.java` | Interogări (`findAllByOrderByCreatedAtDesc`) |
| `admin/service/AuditLogService.java` | Scriere (`inregistreaza`) + citire paginată (`getAuditLog`) |
| `admin/controller/AdminController.java` | Expune `GET /api/admin/audit-log` către frontend |
| `config/AuditConfig.java` | Sursa comună de "cine e utilizatorul curent" (`AuditorAware`), reutilizată și de `createdBy`/`updatedBy` pe toate entitățile |

Apelanți curenți ai `auditLogService.inregistreaza(...)`: `AdminUserService` (aprobare/respingere/dezactivare/reactivare cont), `CompleteProfileService` (completare profil), `UserProfileService` (editare profil/schimbare email), `CursService`, `SaptamanaService`, `DocumentService` (creare/editare/ștergere curs, săptămână, document).
