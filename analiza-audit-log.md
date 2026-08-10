# Analiza Tehnică: Sistemul de Audit Log

Acest document sumarizează concluziile analizei tehnice efectuate asupra planului de implementare a tabelei de `audit_log`. Rolul acestui document este de a justifica arhitectura aleasă și modificările față de planul inițial, protejând aplicația de bug-uri tranzacționale.

## 1. Problema Tranzacțiilor (Riscul Major Evitat)

Codul de business are rute cu particularități tranzacționale. Serviciul de audit (`AuditLogService`) va fi marcat cu `@Transactional(propagation = Propagation.MANDATORY)`. Asta înseamnă că **refuză să funcționeze dacă nu este apelat din interiorul unei tranzacții deja deschise**, prevenind astfel logurile orfane.

Din cauza acestei reguli stricte de siguranță, am descoperit două probleme majore în planul inițial pe care le vom rezolva astfel:
- **La Login (Crearea Contului):** Codul care salvează noul utilizator era în `CustomAuthenticationSuccessHandler`, un filtru de securitate non-tranzacțional. **Soluția:** Vom muta logica de creare într-o funcție `@Transactional` (ex. `inregistreazaUserNou`), pe care handler-ul doar o va apela.
- **La Upload Documente:** `DocumentService.adaugaDocument` nu este o tranzacție globală, deoarece face un apel HTTP lung către sistemul de AI (RAG). **Soluția:** Dacă apelam auditul direct la final, aplicația ar fi crăpat. Vom crea funcții mici "wrapper" (`@Transactional`) pentru salvarea finală a documentului împreună cu logul de audit.

## 2. Decizia de Business la RAG (Upload-ul contează, nu AI-ul)

Sistemul RAG (Chatbot/Inteligența Artificială) funcționează de tip *best-effort*. Dacă un profesor încarcă un curs (PDF), iar PDF-ul ajunge fizic în stocare (MinIO) și e salvat în baza de date locală, **operațiunea a reușit pentru studenți**. Chiar dacă modulul RAG returnează eroare la indexare (status `ERONAT`), documentul există și poate fi citit.
- **Soluția implementată:** Audit-ul (`UPLOAD`, `INLOCUIRE`, `STERGERE`) va fi înregistrat obligatoriu **indiferent de statusul de răspuns de la RAG**, pe baza succesului local.

## 3. Ștergerile în Cascadă (Principiul KISS Păstrat)

Planul original prevedea logarea strictă a acțiunii "sursă" și evitarea inundării bazei de date cu zeci de log-uri inutile generate de cascade (ex. dezactivarea unui profesor dezactivează 10 cursuri).
- **Verificare cod:** Codul nostru actual ocolește (face bypass) funcțiile publice la cascade. De exemplu, ștergerea unei săptămâni apelează `documentRepository.deleteAll()` direct, nu metoda auditată de soft-delete.
- **Concluzie:** Această decizie KISS funcționează perfect și natural, fără a necesita cod suplimentar de separare. Nu vom genera log-uri individuale la acțiuni cascadate, ci vom păstra doar logul pentru "Dezactivare Profesor" sau "Ștergere Săptămână".

## 4. Maparea Numelor de Utilizatori (Nevoia de un Batch Lookup nou)

Planul presupunea că avem deja o funcție care ia ID-urile lungi din Keycloak (ex. `a1b2-c3d4...`) și le transformă în numele reale ("Ion Popescu").
- **Verificare cod:** Această funcție NU a existat niciodată în cod. Cele 5 rute existente de audit afișau mereu ID-ul brut.
- **Soluția implementată:** Vom scrie de la zero interogarea `List<User> findByIdKeycloakIn(List<String> ids)` direct în `UserRepository`, pentru ca pagina nouă de Admin să poată afișa numele persoanelor în istoric într-un mod eficient (fără zeci de interogări pe baza de date per pagină).

## 5. Optimizarea Tabelei de Audit

Migrarea SQL va crea tabela `audit_log` folosind `JSONB` pentru reținerea "pozei" datelor (înainte și după modificare). 
- Pentru a asigura performanța pe termen lung la mii de înregistrări, am adăugat și un index specific pe coloana `utilizator`, altfel orice filtrare din interfața de admin pe un anumit utilizator ar fi încetinit enorm serverul.
