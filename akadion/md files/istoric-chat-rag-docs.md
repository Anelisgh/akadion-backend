# Documentație: Integrarea Completă Backend - RAG

Acest document descrie la nivel arhitectural și tehnic **toate fluxurile de integrare** dintre backend-ul Spring Boot și serviciul RAG (FastAPI), acoperind atât indexarea documentelor (Ingest Pipeline), cât și interogarea chatbot-ului (Chat Pipeline).

---

## Partea 1: Pipeline-ul de Indexare a Documentelor (Ingest)

Ori de câte ori un profesor adaugă, modifică sau șterge un document de curs (ex: un PDF cu suport de curs), backend-ul trebuie să informeze RAG-ul pentru ca acesta să extragă textul și să genereze embeddings.

### 1. Serviciile implicate
* **`DocumentService`**: Orchestrează procesul complet de validare, salvare în MinIO, salvare în DB și apelare RAG.
* **`RagIngestService`**: Serviciul dedicat comunicării HTTP non-blocante cu RAG pentru ingestie (`POST /ingest` și `DELETE /ingest/{id}`).

### 2. State Machine-ul Documentelor
Deoarece RAG-ul (sau o etapă de procesare NLP intensă) ar putea da fail, sincronizarea se face **best-effort**. Documentul are un câmp `statusIndex` (`DocumentStatusIndex` enum) cu 3 stări:
1. **`PRELUAT`**: Document salvat cu succes în baza de date și în MinIO.
2. **`TRIMIS`**: RAG a răspuns cu HTTP 200 la `POST /ingest`. Documentul e indexat și pregătit pentru chatbot.
3. **`ERONAT`**: RAG a dat eroare, timeout sau e offline. Documentul rămâne disponibil studenților pentru descărcare, dar Aky nu "știe" de el.
   * *Mecanism de compensare:* Profesorul are la dispoziție un endpoint `POST /api/profesor/documente/{id}/retry-ingest` care apelează forțat din nou RAG-ul pentru un document `ERONAT`.

### 3. Payload-ul de Ingest
Pentru ca RAG-ul să știe de unde să descarce fișierul, backend-ul îi trimite direct cheia din bucket-ul MinIO și o serie de metadate folosite de AI pentru context:
```json
{
  "documentId": 105,
  "cursId": 2,
  "saptamanaId": 5,
  "profesorId": 14,
  "titlu": "Curs 3 - OOP",
  "pathMinio": "curs-2/saptamana-5/uuid-fisier.pdf",
  "extensie": "pdf",
  "cursDenumire": "Programare Orientata pe Obiecte",
  "nrSaptamana": 3
}
```
*Notă tehnică:* Extensia este dedusă de backend din `pathMinio` și trimisă ca un string curat (ex: `"pdf"`, `"docx"`). Dacă profesorul șterge un document (soft-delete), se apelează `DELETE /ingest/{id}`.

---

## Partea 2: Pipeline-ul de Chat (Istoric și Interogare Aky)

Chatbot-ul este accesibil atât **studenților** înrolați activ, cât și **profesorilor** (pe cursurile proprii). Backend-ul menține istoricul conversațiilor în propriul PostgreSQL și trimite doar ultimele mesaje către RAG ca un "scurt context".

### 1. Schema Bazei de Date
* **`conversatii`**: Reține metadatele unei sesiuni de chat (fără constrângeri unice per user/curs, permițând sesiuni infinite).
* **`mesaje_chat`**: Reține mesajul, sursele (CSV) și un enum simplu `RolMesaj` (`UTILIZATOR` sau `ASISTENT`).

### 2. Logica de Orchestrare (ConversatieService)
Trimiterea unei întrebări noi (`POST /api/conversatii/{id}/mesaje`) e împărțită în **3 pași** de design critici, meniți să prevină blocarea bazei de date (Connection Pool Exhaustion):

1. **Pas 1 - Salvare Întrebare (`@Transactional`)**
   * Verifică autorizarea și aplică Rate Limiting.
   * Salvează mesajul utilizatorului în baza de date cu `are_raspuns = false`.

2. **Pas 2 - Apelul către RAG (Fără `@Transactional`)**
   * Metoda apelează `RagChatService.intreabaAky`. Această metodă nu blochează o tranzacție locală DB pe durata timeout-ului HTTP.

3. **Pas 3 - Salvare Răspuns (`@Transactional`)**
   * Preia răspunsul RAG, îl stochează sub rolul `ASISTENT`, și setează `are_raspuns = true` pe mesajul utilizatorului.

- Funcția cheie care creează contextul este `obtineIstoric`, care extrage doar mesajele conversației respective, asigurându-se că îi aparține utilizatorului curent.

### Protecția de Retry și `are_raspuns`

Coloana `are_raspuns` din tabelul `mesaje_chat` există special pentru a gestiona corect situațiile în care RAG-ul eșuează (HTTP 502, timeout), fără a polua UI-ul și fără a pierde întrebarea utilizatorului:

1. **La salvarea inițială (Pasul 1)**: Când un `UTILIZATOR` trimite un mesaj, acesta este salvat cu `are_raspuns = false`.
2. **Dacă Pasul 2 (RAG) eșuează**: Tranzacția inițială a fost deja finalizată. Mesajul rămâne în baza de date cu `are_raspuns = false`. Frontend-ul știe acum în mod explicit că acest mesaj nu a primit un răspuns din cauza unei erori tehnice (și poate afișa un ⚠️).
3. **Dacă Pasul 2 (RAG) reușește**: La Pasul 3 (salvarea răspunsului ASISTENT-ului), se actualizează mesajul utilizatorului la `are_raspuns = true`.

**Cum funcționează Retry-ul?**
Avem un endpoint dedicat: `POST /api/conversatii/mesaje/{mesajId}/retry`.
Când utilizatorul apasă pe "Reîncearcă" pentru un mesaj eșuat, frontend-ul nu creează un mesaj nou. Se apelează acest endpoint care:
- Verifică ownership-ul mesajului (siguranța că aparține userului curent).
- Verifică rate limit-ul user-ului și accesul activ la curs.
- Apelează RAG-ul direct, folosind contextul conversației existente.
- Salvează mesajul asistentului și marchează mesajul vechi cu `are_raspuns = true`.

Această arhitectură elimină ambiguitatea din baza de date și oferă o metodă curată și sigură de retry.

---

### 3. Chatbot-ul e comun studenților și profesorilor — o singură nuanță de nume

Chatbot-ul poate fi interogat atât de studenți, cât și de profesori — parametrul Java din `RagChatService.intreabaAky` se numește `userId` tocmai pentru că poate fi ID-ul oricăruia dintre cei doi. **Cheia JSON trimisă efectiv către RAG rămâne totuși `"studentId"`** — o denumire moștenită din payload, care nu reflectă exact semantica parametrului Java; nu confunda una cu cealaltă. Vezi `contract-rag.md` pentru payload-ul exact.

Payload-ul include și `maxSaptamanaParcursa`: calculat real pentru chat-ul rapid al studentului (limitează AI-ul la materia deja parcursă, vezi `services.md` §9.1 și §9.3), dar trimis implicit ca `100` pentru chat-ul profesorului și pentru chat-ul persistat (`ConversatieService`, folosit de ambele roluri) — practic fără limitare de progres în aceste două cazuri. Motivația completă a acestei diferențe: `fluxuri/06-chat-aky-si-conversatii.md`.
