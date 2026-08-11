# Documentație Tehnică: Integrare Quiz & Flashcards Smart Aky

Acest document descrie arhitectura, modificările efectuate, regulile de securitate și fluxul end-to-end pentru modulele de **Quiz** și **Flashcards** pe stiva AKADION (Spring Boot, React Frontend și Python RAG Service).

---

## 1. Arhitectură și Flux End-to-End

```
[ Frontend (React / AkyChatWidget) ]
             │
             │ HTTP POST /api/student/cursuri/{cursId}/flashcards/generate
             ▼
[ Monolit Spring Boot (StudentController -> StudentCursService) ]
             │  (Verificare rol STUDENT, cont ACTIV, înrolare, săptămână parcursă, rate limiting)
             │
             │ HTTP POST /flashcards/generate (cu secret BasicAuth)
             ▼
[ Serviciul Python RAG (llm-response / FastAPI) ]
             │  (Căutare contexte în Vector DB Qdrant, construire prompt cu Grounding)
             │
             │ API Call (Gemini Flash / JSON Mode)
             ▼
[ Google Gemini LLM ]
```

---

## 2. Structura Datelor (JSON Schemas & Payloads)

### Cerere Frontend -> Monolit Spring Boot:
`POST /api/student/cursuri/{cursId}/flashcards/generate`
```json
{
  "documentId": 12,
  "nrFlashcards": 5
}
```
*(Câmpurile `documentId` și `nrFlashcards` sunt opționale; dacă `documentId` este `null`, se folosesc toate documentele accesibile).*

### Cerere Monolit -> Serviciu Python RAG:
`POST http://llm-response:8000/flashcards/generate`
```json
{
  "cursId": 1,
  "maxSaptamana": 3,
  "documentId": 12,
  "nrFlashcards": 5
}
```

### Răspuns Serviciu Python RAG / Monolit -> Frontend (Flashcards):
```json
[
  {
    "fata": "Ce reprezintă încapsularea în POO?",
    "verso": "Mecanismul de ascundere a datelor interne ale unei clase și de restricționare a accesului direct prin metode publice (getters/setters)."
  }
]
```

### Răspuns Serviciu Python RAG / Monolit -> Frontend (Quiz):
```json
[
  {
    "intrebare": "Care dintre următoarele afirmații despre moștenire este adevărată?",
    "optiuni": {
      "A": "O clasă derivată moștenește membrii privați.",
      "B": "O clasă poate moșteni direct mai multe clase în Java.",
      "C": "Moștenirea permite reutilizarea codului și polimorfismul.",
      "D": "Clasa Object nu este clasa rădăcină în Java."
    },
    "raspuns_corect": "C",
    "explicatie": "În Java, moștenirea simplă permite unei clase să refolosească atributele și metodele publice/protected ale clasei părinte."
  }
]
```

---

## 3. Fișiere Modificate și Relaționarea lor

### A. Backend Monolit (Java / Spring Boot)

1. **`FlashcardGenerateRequestDto.java`** (`backend/proiect/src/main/java/com/example/akadion/dto/`)
   - **Ce face**: DTO (Record Java) care încapsulează parametrii: `documentId` (opțional) și `nrFlashcards` (1 - 20).

2. **`StudentController.java`** (`backend/proiect/src/main/java/com/example/akadion/controller/`)
   - **Ce face**: Expune endpoint-ul securizat `@PostMapping("/cursuri/{cursId}/flashcards/generate")` protejat de `@PreAuthorize("hasRole('STUDENT')")`.

3. **`StudentCursService.java`** (`backend/proiect/src/main/java/com/example/akadion/service/`)
   - **Ce face**:
     - `determinaSaptamanaParcursaMax()`: Calculează săptămâna maximă parcursă de student pe baza intrărilor din `parcursRepository`.
     - `genereazaFlashcards()`: Validează că:
       1. Studentul are cont local `ACTIV` și înrolare activă la curs.
       2. Numărul de fișe este între 1 și 20.
       3. Dacă se specifică un `documentId`, verifică că fișierul este activ, aparține cursului și că `nrSaptamana` <= `maxSaptamana`.
       4. Aplică rate-limiting-ul local (`checkRateLimit`).

4. **`RagChatService.java`** (`backend/proiect/src/main/java/com/example/akadion/service/`)
   - **Ce face**:
     - `genereazaFlashcards()`: Execută cererea HTTP POST către microserviciul Python RAG.
     - `genereazaQuiz()`: Actualizate null-check-urile pentru payload (`maxSaptamana`, `documentId`, `nrIntrebari`).

---

### B. Microserviciul Python RAG (`rag/llm-response/`)

1. **`models.py`**
   - Modele Pydantic: `FlashcardGenerateRequest` (`cursId`, `maxSaptamanaParcursa`, `maxSaptamana`, `documentId`, `nrFlashcards`) și `FlashcardItem` (`fata`, `verso`).

2. **`prompt_builder.py`**
   - Funcția `construieste_prompt_flashcards(...)`: Construiește prompt-ul de sistem cu instrucțiuni stricte de Grounding (fără halucinații, răspuns strict JSON cu cheile `"fata"` și `"verso"`).

3. **`main.py`**
   - Endpoint-ul `@app.post("/flashcards/generate")` și funcția `_normalizeaza_flashcards(...)` care extrage și curăță răspunsul JSON returnat de Gemini.

---

### C. Frontend (React / Tailwind / Lucide Icons)

1. **`conversatii.js`** (`frontend/src/lib/`)
   - Funcția `genereazaFlashcards(cursId, documentId, nrFlashcards)` pentru apelarea API-ului Spring.

2. **`AkyChatWidget.jsx`** (`frontend/src/components/chat/`)
   - Stări noi React: `rightPanelMode` (`null` | `'quiz'` | `'flashcards'`), `flashcardQuestions`, `currentFlashcardIndex`, `isFlashcardFlipped`, `isFlashcardsLoading`, etc.
   - Componentă UI 3D Flip Card: Folosește `perspective: 1000px`, `transformStyle: preserve-3d` și `transform: rotateY(180deg)` pentru rotire fluidă între concept (față) și răspuns (verso).
   - Buton dual în header: Butonul de Quiz cu textul original (`"Aky e gata de examen. Tu? QUIZ"`) și butonul de `"Flashcards"`.

---

## 4. Regulile de Securitate și Limitări (Business Rules)

- **Access Control**: Generarea de flashcards și quiz-uri este permisă exclusiv studenților înrolați activ la cursul respectiv.
- **Bounding Context (Grounding)**: Un student poate genera fișe/întrebări doar din documente ce aparțin săptămânilor deja finalizate sau accesibile de el (`nrSaptamana <= maxSaptamanaParcursa`).
- **Rate Limiting**: Cererile sunt limitate per utilizator (sliding window 10 cereri / minut) pentru prevenirea abuzului de tokeni Gemini.

---
*Documentație completă pentru repository-ul Akadion.*

