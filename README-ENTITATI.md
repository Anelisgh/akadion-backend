# 📊 Akadion — Documentație Entități JPA & Sistem de Audit

Această documentație detaliază structura completă a bazei de date a proiectului **Akadion**, incluzând tipurile de date Java, coloanele corespunzătoare din PostgreSQL, relațiile, constrângerile de unicitate, indecșii și sistemul automat de audit.

---

## 🛡️ Sistemul de Audit (JPA Auditing)

Proiectul folosește **Spring Data JPA Auditing** pentru a înregistra automat cine și când a creat sau modificat fiecare înregistrare din baza de date.

### 1. Clasa de Bază: `BaseAuditableEntity`
Toate entitățile de business extind clasa abstractă [BaseAuditableEntity](file:///C:/Users/Aneliss/Desktop/proiect-sts/proiect/src/main/java/com/example/akadion/entity/BaseAuditableEntity.java). Aceasta este marcată cu `@MappedSuperclass` și înregistrează automat auditul prin `@EntityListeners(AuditingEntityListener.class)`.

| Câmp Java | Tip Dată Java | Coloană DB | Tip Dată DB | Adnotare JPA / Comportament |
| :--- | :--- | :--- | :--- | :--- |
| `createdBy` | `String` | `created_by` | `VARCHAR(36)` | `@CreatedBy` — Id-ul Keycloak al creatorului (la insert, updatable = false) |
| `createdAt` | `OffsetDateTime` | `created_at` | `TIMESTAMP WITH TIME ZONE` | `@CreatedDate` — Data și ora creării (la insert, updatable = false) |
| `updatedBy` | `String` | `updated_by` | `VARCHAR(36)` | `@LastModifiedBy` — Id-ul Keycloak al ultimului editor |
| `updatedAt` | `OffsetDateTime` | `updated_at` | `TIMESTAMP WITH TIME ZONE` | `@LastModifiedDate` — Data și ora ultimei modificări |

### 2. Furnizorul de Auditor: `AuditConfig`
Mecanismul de rezolvare a utilizatorului curent este implementat în [AuditConfig](file:///C:/Users/Aneliss/Desktop/proiect-sts/proiect/src/main/java/com/example/akadion/config/AuditConfig.java) printr-un bean `AuditorAware<String>`. Acesta extrage din contextul de securitate (`SecurityContextHolder`):
1. **OidcUser** (autentificare browser/BFF) -> Returnează `subject` (UUID-ul Keycloak).
2. **Jwt** (apeluri REST API directe) -> Returnează claim-ul `sub` (UUID-ul Keycloak).
3. **String** (alte scenarii de securitate) -> Returnează string-ul direct.
4. **Fallback** -> Returnează `"system"` (de exemplu, la rularea seed-urilor automate de date în startup).

---

## 🗃️ Detalii Entități & Tabela de Mapare

### 1. User (`app_user`)
Reprezintă utilizatorii platformei (Studenți, Profesori sau Administratori). Tabela se numește `app_user` deoarece `user` este cuvânt rezervat în PostgreSQL.
* **Clasă Java**: [User.java](file:///C:/Users/Aneliss/Desktop/proiect-sts/proiect/src/main/java/com/example/akadion/entity/User.java) (extinde `BaseAuditableEntity`)

#### Câmpuri & Tipurile de Date:
| Câmp Java | Tip Dată Java | Coloană DB | Tip Dată DB | Constrângeri / Relații |
| :--- | :--- | :--- | :--- | :--- |
| `id` | `Long` | `id` | `BIGSERIAL` | `@Id`, `@GeneratedValue(IDENTITY)` |
| `idKeycloak` | `String` | `id_keycloak` | `VARCHAR(36)` | `NOT NULL`, `UNIQUE` (UUID-ul nativ din Keycloak) |
| `mail` | `String` | `mail` | `VARCHAR(100)` | `NOT NULL`, `UNIQUE` |
| `nume` | `String` | `nume` | `VARCHAR(100)` | `NULLABLE` (devenit obligatoriu doar după Complete-Profile) |
| `prenume` | `String` | `prenume` | `VARCHAR(100)` | `NULLABLE` (devenit obligatoriu doar după Complete-Profile) |
| `facultate` | `String` | `facultate` | `VARCHAR(100)` | `NULLABLE` |
| `stareCont` | `StareCont` | `id_stare_cont` | `BIGINT` | `@ManyToOne`, `NOT NULL` (Relație cu `stari_cont`) |
| `rol` | `Rol` | `id_rol` | `BIGINT` | `@ManyToOne`, `NULLABLE` (Relație cu `roluri`, setat la Complete-Profile) |
| `nrRespingeri` | `Integer` | `nr_respingeri` | `INTEGER` | `NOT NULL` (Implicit `0`, contorizează respingerile administrative) |

#### Constrângeri de Unicitate (Unique Constraints):
* `uk_app_user_id_keycloak` pe coloana `id_keycloak`
* `uk_app_user_mail` pe coloana `mail`

#### Indecși:
* `idx_app_user_id_keycloak` pe `id_keycloak` (optimizare căutare la login/filtre)
* `idx_app_user_mail` pe `mail`
* `idx_app_user_rol` pe `id_rol`
* `idx_app_user_stare_cont` pe `id_stare_cont`

---

### 2. Rol (`roluri`)
Nomenclatorul de roluri.
* **Clasă Java**: [Rol.java](file:///C:/Users/Aneliss/Desktop/proiect-sts/proiect/src/main/java/com/example/akadion/entity/Rol.java) (nu este auditată)

| Câmp Java | Tip Dată Java | Coloană DB | Tip Dată DB | Constrângeri |
| :--- | :--- | :--- | :--- | :--- |
| `id` | `Long` | `id` | `BIGSERIAL` | `@Id`, `@GeneratedValue(IDENTITY)` |
| `denumire` | `String` | `denumire` | `VARCHAR(50)` | `NOT NULL`, `UNIQUE` (Valori: `'ADMIN'`, `'PROFESOR'`, `'STUDENT'`) |

---

### 3. StareCont (`stari_cont`)
Nomenclatorul stărilor posibile ale unui cont de utilizator.
* **Clasă Java**: [StareCont.java](file:///C:/Users/Aneliss/Desktop/proiect-sts/proiect/src/main/java/com/example/akadion/entity/StareCont.java) (nu este auditată)

| Câmp Java | Tip Dată Java | Coloană DB | Tip Dată DB | Constrângeri |
| :--- | :--- | :--- | :--- | :--- |
| `id` | `Long` | `id` | `BIGSERIAL` | `@Id`, `@GeneratedValue(IDENTITY)` |
| `denumire` | `String` | `denumire` | `VARCHAR(20)` | `NOT NULL`, `UNIQUE` (Valori: `'INCOMPLET'`, `'PENDING'`, `'ACTIV'`, `'RESPINS'`, `'INACTIV'`) |

---

### 4. Curs (`cursuri`)
Cursurile create de profesori pe platformă.
* **Clasă Java**: [Curs.java](file:///C:/Users/Aneliss/Desktop/proiect-sts/proiect/src/main/java/com/example/akadion/entity/Curs.java) (extinde `BaseAuditableEntity`)

| Câmp Java | Tip Dată Java | Coloană DB | Tip Dată DB | Constrângeri / Relații |
| :--- | :--- | :--- | :--- | :--- |
| `id` | `Long` | `id` | `BIGSERIAL` | `@Id`, `@GeneratedValue(IDENTITY)` |
| `profesor` | `User` | `id_profesor` | `BIGINT` | `@ManyToOne`, `NOT NULL` (Relație cu `app_user`) |
| `denumire` | `String` | `denumire` | `VARCHAR(150)` | `NOT NULL` |
| `descriere` | `String` | `descriere` | `VARCHAR(1000)` | `NULLABLE` |
| `dataInceput` | `LocalDate` | `data_inceput` | `DATE` | `NOT NULL` |
| `dataSfarsit` | `LocalDate` | `data_sfarsit` | `DATE` | `NOT NULL` |
| `activ` | `Boolean` | `activ` | `BOOLEAN` | `NOT NULL` (Implicit `true`) |

#### Indecși:
* `idx_cursuri_profesor` pe `id_profesor`

---

### 5. UserCurs (`user_cursuri`)
Tabela de legătură Many-to-Many între Studenți și Cursuri (înrolări).
* **Clasă Java**: [UserCurs.java](file:///C:/Users/Aneliss/Desktop/proiect-sts/proiect/src/main/java/com/example/akadion/entity/UserCurs.java) (extinde `BaseAuditableEntity`)

| Câmp Java | Tip Dată Java | Coloană DB | Tip Dată DB | Constrângeri / Relații |
| :--- | :--- | :--- | :--- | :--- |
| `id` | `Long` | `id` | `BIGSERIAL` | `@Id`, `@GeneratedValue(IDENTITY)` |
| `student` | `User` | `id_student` | `BIGINT` | `@ManyToOne`, `NOT NULL` (Relație cu `app_user`) |
| `curs` | `Curs` | `id_curs` | `BIGINT` | `@ManyToOne`, `NOT NULL` (Relație cu `cursuri`) |
| `activ` | `Boolean` | `activ` | `BOOLEAN` | `NOT NULL` (Implicit `true`, dezactivat în caz de renunțare/exmatriculare) |

#### Constrângeri de Unicitate:
* `uk_user_cursuri_student_curs` pe perechea `(id_student, id_curs)` - previne înrolarea dublă a unui student la același curs.

#### Indecși:
* `idx_user_cursuri_student` pe `id_student`
* `idx_user_cursuri_curs` pe `id_curs`

---

### 6. Saptamana (`saptamani`)
Structura săptămânală a unui curs.
* **Clasă Java**: [Saptamana.java](file:///C:/Users/Aneliss/Desktop/proiect-sts/proiect/src/main/java/com/example/akadion/entity/Saptamana.java) (extinde `BaseAuditableEntity`)

| Câmp Java | Tip Dată Java | Coloană DB | Tip Dată DB | Constrângeri / Relații |
| :--- | :--- | :--- | :--- | :--- |
| `id` | `Long` | `id` | `BIGSERIAL` | `@Id`, `@GeneratedValue(IDENTITY)` |
| `curs` | `Curs` | `id_curs` | `BIGINT` | `@ManyToOne`, `NOT NULL` (Relație cu `cursuri`) |
| `nrSaptamana` | `Integer` | `nr_saptamana` | `INTEGER` | `NOT NULL` |
| `descriere` | `String` | `descriere` | `VARCHAR(500)` | `NULLABLE` |

#### Indecși:
* `idx_saptamani_curs` pe `id_curs`

---

### 7. Document (`documente`)
Materiale didactice (pdf, zip, docx) încărcate în cloud MinIO, atașate unei săptămâni.
* **Clasă Java**: [Document.java](file:///C:/Users/Aneliss/Desktop/proiect-sts/proiect/src/main/java/com/example/akadion/entity/Document.java) (extinde `BaseAuditableEntity`)

| Câmp Java | Tip Dată Java | Coloană DB | Tip Dată DB | Constrângeri / Relații |
| :--- | :--- | :--- | :--- | :--- |
| `id` | `Long` | `id` | `BIGSERIAL` | `@Id`, `@GeneratedValue(IDENTITY)` |
| `saptamana` | `Saptamana` | `id_saptamana` | `BIGINT` | `@ManyToOne`, `NOT NULL` (Relație cu `saptamani`) |
| `titlu` | `String` | `titlu` | `VARCHAR(255)` | `NOT NULL` |
| `pathMinio` | `String` | `path_minio` | `VARCHAR(512)` | `NOT NULL` (Calea unică a fișierului în bucket-ul MinIO) |
| `statusIndex` | `DocumentStatusIndex` | `status_index` | `VARCHAR(20)` | `NOT NULL`, `@Enumerated(EnumType.STRING)` (Enum: `PRELUAT`, `TRIMIS`, `ERONAT`) |
| `activ` | `Boolean` | `activ` | `BOOLEAN` | `NOT NULL` (Implicit `true`) |

#### Indecși:
* `idx_documente_saptamana` pe `id_saptamana`

---

### 8. Parcurs (`parcursuri`)
Progresul individual al studenților pe săptămâni (marcare săptămână finalizată/parcursă).
* **Clasă Java**: [Parcurs.java](file:///C:/Users/Aneliss/Desktop/proiect-sts/proiect/src/main/java/com/example/akadion/entity/Parcurs.java) (extinde `BaseAuditableEntity`)

| Câmp Java | Tip Dată Java | Coloană DB | Tip Dată DB | Constrângeri / Relații |
| :--- | :--- | :--- | :--- | :--- |
| `id` | `Long` | `id` | `BIGSERIAL` | `@Id`, `@GeneratedValue(IDENTITY)` |
| `userCurs` | `UserCurs` | `id_user_curs` | `BIGINT` | `@ManyToOne`, `NOT NULL` (Relație cu legătura student-curs din `user_cursuri`) |
| `saptamana` | `Saptamana` | `id_saptamana` | `BIGINT` | `@ManyToOne`, `NOT NULL` (Relație cu `saptamani`) |

#### Indecși:
* `idx_parcursuri_user_curs` pe `id_user_curs`
* `idx_parcursuri_saptamana` pe `id_saptamana`
