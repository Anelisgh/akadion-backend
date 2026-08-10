# Documentație Tehnică: Integrare Quiz & Flashcards Smart Aky

Acest document descrie arhitectura, modificările efectuate și fluxul end-to-end pentru modulul de **Quiz** și **Flashcards** pe stiva AKADION (Spring Boot, React Frontend și Python RAG Service).

---

## 1. Arhitectură și Flux End-to-End

```
[ Frontend (React / AkyChatWidget) ]
             │
             │ HTTP POST /api/student/cursuri/{cursId}/flashcards/generate
             ▼
[ Monolit Spring Boot (StudentController -> StudentCursService) ]
             │  (Verificare acces student, săptămână maximă parcursă, ratelimit)
             │
             │ HTTP POST /flashcards/generate (cu secret BasicAuth)
             ▼
[ Serviciul Python RAG (llm-response / FastAPI) ]
             │  (Extragere contexte relevante din Vector DB Qdrant, construire prompt)
             │
             │ API Call (Gemini 2.5 Flash / JSON Mode)
             ▼
[ Google Gemini LLM ]
```

---

## 2. Fișiere Modificate și Relaționarea lor

### A. Backend Monolit (Java / Spring Boot)

1. **`FlashcardGenerateRequestDto.java`** (`backend/proiect/src/main/java/com/example/akadion/dto/`)
   - **Ce face**: DTO (Record Java) care încapsulează parametrii opționali trimiși de frontend: `documentId` (opțional, pentru limitarea la un singur fișier) și `nrFlashcards` (implicit 5, maxim 20).
   - **De ce**: Oferă o structură strongly-typed pentru validarea datelor de intrare.

2. **`StudentController.java`** (`backend/proiect/src/main/java/com/example/akadion/controller/`)
   - **Ce face**: Expune endpoint-ul securizat `@PostMapping("/cursuri/{cursId}/flashcards/generate")`.
   - **De ce**: Permite studenților autentificați să solicite generarea de flashcards pentru un curs la care sunt înrolați.

3. **`StudentCursService.java`** (`backend/proiect/src/main/java/com/example/akadion/service/`)
   - **Ce face**:
     - `determinaSaptamanaParcursaMax()`: Calculează săptămâna maximă permisă pentru student pe baza parcursului său.
     - `genereazaFlashcards()`: Validează că studentul este activ înrolat, că documentul selectat aparține cursului și nu depășește săptămâna parcursă, apoi delegă către `RagChatService`.
   - **De ce**: Asigură securitatea la nivel de domeniu (un student nu poate genera fișe/quiz-uri din săptămâni viitoare pe care nu le-a deblocat încă).

4. **`RagChatService.java`** (`backend/proiect/src/main/java/com/example/akadion/service/`)
   - **Ce face**:
     - `genereazaFlashcards()`: Realizează apelul HTTP REST către serviciul Python RAG pe endpoint-ul `/flashcards/generate`.
     - `genereazaQuiz()`: S-au adăugat null-checks pentru payload-ul trimis către RAG (`maxSaptamana`, `documentId`, `nrIntrebari`).
   - **De ce**: Intermediază comunicarea securizată între monolitul Spring și microserviciul Python RAG.

---

### B. Microserviciul Python RAG (`rag/llm-response/`)

1. **`models.py`**
   - **Ce face**: Definește modelele Pydantic:
     - `FlashcardGenerateRequest`: Parametrii cererii (`cursId`, `maxSaptamana`, `documentId`, `nrFlashcards`).
     - `FlashcardItem`: Formatul de răspuns (`fata`: concept/întrebare, `verso`: definiție/răspuns).
   - **De ce**: Asigură validarea payload-ului JSON primit de la Spring Boot și serializarea corectă a răspunsului.

2. **`prompt_builder.py`**
   - **Ce face**: Definește funcția `construieste_prompt_flashcards(...)`.
   - **De ce**: Generează instrucțiunile stricte (Grounding & JSON Schema) pentru modelul LLM, forțând returnarea unui tablou JSON cu exact numărul de fișe solicitate bazate exclusiv pe textele din cursurile parcurse.

3. **`main.py`**
   - **Ce face**:
     - Expune endpoint-ul `@app.post("/flashcards/generate")`.
     - Adaugă funcția `_normalizeaza_flashcards(...)` care procesează răspunsul brut de la LLM în obiecte `FlashcardItem`.
   - **De ce**: Pune la dispoziție serviciul propriu-zis de generare AI pentru flashcards.

---

### C. Frontend (React / Tailwind / Lucide Icons)

1. **`conversatii.js`** (`frontend/src/lib/`)
   - **Ce face**: Adaugă funcția `genereazaFlashcards(cursId, documentId, nrFlashcards)` care apelează API-ul monolitului și adaptează `genereazaQuiz`.
   - **De ce**: Încheagă comunicarea dintre componentele React și API-ul Spring.

2. **`AkyChatWidget.jsx`** (`frontend/src/components/chat/`)
   - **Ce face**:
     - Integrarea interfeței Flashcards în panoul lateral cu efect 3D Flip (rotire card la apăsare între `fata` și `verso`).
     - Păstrarea butonului original de Quiz (`"Aky e gata de examen. Tu? QUIZ"`) și adăugarea noului buton `"Flashcards"`.
     - Suport pentru ambele moduri de studiu (Quiz în-chat / Quiz panou lateral și Flashcards panou lateral).
   - **De ce**: Oferă studentului o experiență interactivă și modernă de memorare activă și autoevaluare.

---

## 3. Rezumatul Modificărilor și Rationale-ul lor

| Componentă | Modificare efectuată | Rationale (De ce?) |
|---|---|---|
| **Spring DTO** | `FlashcardGenerateRequestDto.java` | Mapping curat al parametrilor cererii de flashcards. |
| **Spring Controller** | `POST /cursuri/{cursId}/flashcards/generate` | Punct de acces REST securizat pentru studenți. |
| **Spring Service** | Metoda `genereazaFlashcards` + fix week calculation | Control al accesului pe bază de drepturi și progres. |
| **Spring RAG Client** | Client RestClient pentru `/flashcards/generate` | Comunicare monolit -> microserviciu RAG. |
| **Python RAG** | Pydantic Models, System Prompt, FastAPI endpoint | Generare propriu-zisă de flashcards cu Gemini LLM. |
| **Frontend UI** | Interfață 3D Flip Card Flashcards + Buton Dual Header | Experiență UI/UX fluidă de studiu. |

---
*Documentație generată automat pentru repository-ul Akadion.*
