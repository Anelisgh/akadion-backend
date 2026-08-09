# Documentație: Entități JPA și Structura Bazei de Date

Această documentație detaliază structura completă a bazei de date a proiectului **Akadion**, incluzând entitățile, câmpurile, relațiile, constrângerile de unicitate, indecșii și sistemul automat de audit.

---

## 1. Sistemul de Audit (JPA Auditing)
Toate entitățile de business (cu excepția nomenclatoarelor) extind clasa abstractă `BaseAuditableEntity`. Aceasta înregistrează automat:
- `createdBy` (String, max 36) - ID-ul Keycloak al autorului.
- `createdAt` (OffsetDateTime) - Data/ora creării.
- `updatedBy` (String, max 36) - ID-ul Keycloak al ultimului editor.
- `updatedAt` (OffsetDateTime) - Data/ora ultimei modificări.
*Valorile sunt preluate automat din SecurityContext (prin `AuditConfig`) la fiecare operațiune JPA de insert/update.*

---

## 2. Entități Nomenclator

### 2.1. Rol (`roluri`)
Reprezintă nomenclatorul de roluri din aplicație.
- **id**: `Long` (PK)
- **denumire**: `String` (UK, max 50). Valori posibile: `'ADMIN'`, `'PROFESOR'`, `'STUDENT'`.

### 2.2. StareCont (`stari_cont`)
Reprezintă starea în care se află contul unui utilizator în workflow-ul de aprobare/banare.
- **id**: `Long` (PK)
- **denumire**: `String` (UK, max 20). Valori posibile: `'INCOMPLET'`, `'PENDING'`, `'ACTIV'`, `'INACTIV'`, `'RESPINS'`.

---

## 3. Entități Principale (Business)

### 3.1. User (`app_user`)
- **Scop:** Utilizatorii platformei. Tabela folosește numele `app_user` din cauza rezervării cuvântului "user" în PostgreSQL.
- **Câmpuri:**
  - `id`: `Long` (PK)
  - `idKeycloak`: `String` (UK, max 36) - Identificatorul absolut din Keycloak (`sub`).
  - `mail`: `String` (UK, max 100).
  - `nume`: `String` (Nullable inițial).
  - `prenume`: `String` (Nullable inițial).
  - `facultate`: `String` (Nullable inițial).
  - `nrRespingeri`: `Integer` (Default 0). Contorizează de câte ori a fost respins un cont de un admin.
- **Relații:**
  - `stareCont`: `@ManyToOne` (Către `StareCont`, NOT NULL).
  - `rol`: `@ManyToOne` (Către `Rol`, Nullable în faza INCOMPLET).
- **Indecși:** `id_keycloak`, `mail`, `id_rol`, `id_stare_cont`.

### 3.2. Curs (`cursuri`)
- **Scop:** Cursurile deținute de un profesor.
- **Câmpuri:**
  - `id`: `Long` (PK)
  - `denumire`: `String` (NOT NULL, max 150)
  - `descriere`: `String` (max 1000)
  - `dataInceput`: `LocalDate` (Nullable).
  - `dataSfarsit`: `LocalDate` (Nullable). Calculată exclusiv automat în funcție de săptămâni.
  - `activ`: `Boolean` (NOT NULL, default true). Flag pentru soft delete / curs inactiv.
- **Relații:**
  - `profesor`: `@ManyToOne` (Către `User`, NOT NULL).
- **Indecși:** `id_profesor`.

### 3.3. Saptamana (`saptamani`)
- **Scop:** Organizarea cronologică (module/săptămâni) a unui curs.
- **Câmpuri:**
  - `id`: `Long` (PK)
  - `nrSaptamana`: `Integer` (NOT NULL). Autogenerat (+1 la maximul per curs).
  - `descriere`: `String` (max 500).
- **Relații:**
  - `curs`: `@ManyToOne` (Către `Curs`, NOT NULL).
- **Indecși/Constrângeri:** 
  - Unique Constraint: `(id_curs, nr_saptamana)`.
  - Index: `id_curs`.

### 3.4. Document (`documente`)
- **Scop:** Reprezintă materialele didactice urcate de profesori în fiecare săptămână.
- **Câmpuri:**
  - `id`: `Long` (PK)
  - `titlu`: `String` (NOT NULL).
  - `pathMinio`: `String` (NOT NULL, max 512). Calea completă din bucket-ul MinIO `course-documents`.
  - `statusIndex`: `DocumentStatusIndex` (Enum: `PRELUAT`, `TRIMIS`, `ERONAT`). Starea interacțiunii documentului cu sistemul RAG de la FastAPI.
  - `activ`: `Boolean` (NOT NULL, default true).
- **Relații:**
  - `saptamana`: `@ManyToOne` (Către `Saptamana`, NOT NULL).
- **Indecși:** `id_saptamana`.

### 3.5. UserCurs (`user_cursuri`)
- **Scop:** Tabelă de joncțiune ce marchează înrolarea unui student la un anumit curs.
- **Câmpuri:**
  - `id`: `Long` (PK)
  - `activ`: `Boolean` (NOT NULL, default true). Permite retragerea studentului sau dezactivarea forțată păstrând istoricul.
- **Relații:**
  - `student`: `@ManyToOne` (Către `User`, NOT NULL).
  - `curs`: `@ManyToOne` (Către `Curs`, NOT NULL).
- **Indecși/Constrângeri:** 
  - Unique Constraint: `(id_student, id_curs)`. Un student nu poate avea două instanțe de înrolare per curs.

### 3.6. Parcurs (`parcursuri`)
- **Scop:** Urmărirea (tracking) progresului. Marchează faptul că un student a terminat materialele dintr-o anumită săptămână.
- **Câmpuri:**
  - `id`: `Long` (PK)
- **Relații:**
  - `userCurs`: `@ManyToOne` (Către `UserCurs`, NOT NULL).
  - `saptamana`: `@ManyToOne` (Către `Saptamana`, NOT NULL).
- **Indecși/Constrângeri:**
  - Unique Constraint: `(id_user_curs, id_saptamana)`. Previne bifarea de mai multe ori a aceleiași săptămâni.
