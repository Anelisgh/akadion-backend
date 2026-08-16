# 📖 Jurnalul de Acțiuni (Audit Log) din Backend-ul Akadion

Sistemul de **Audit Log** (sau Jurnalizarea Acțiunilor) din aplicația ta funcționează ca o "Cutie Neagră" a unui avion. Scopul lui este să rețină **absolut orice modificare** importantă adusă datelor, pentru a putea răspunde la întrebările: *Cine a făcut modificarea? Când? Ce s-a șters? Ce valoare era înainte?*

Iată o explicație pas cu pas a modului în care funcționează acest sistem în Backend:

---

## 1. Unde se stochează datele? (Entitatea `AuditLog.java`)
Fișierul pe care l-ai arătat (`AuditLog.java`) reprezintă structura tabelului `audit_log` din baza de date. El conține următoarele coloane esențiale:
- **`nume_tabel`**: Ce tabel a fost modificat? (ex: *curs*, *utilizator*, *document*)
- **`id_inregistrare`**: ID-ul exact al rândului modificat.
- **`operatie`**: Ce s-a întâmplat? (*CREATE* - adăugare, *UPDATE* - modificare, *DELETE* - ștergere)
- **`valori_vechi` & `valori_noi`**: Acestea sunt coloane speciale de tip `JSONB`. Dacă un admin schimbă titlul unui curs din "Matematică" în "Algebră", aici se va salva exact diferența pentru a o putea vizualiza ulterior!
- **`creat_la`**: Data și ora exactă, setată automat de sistem.
- **`utilizator`**: ID-ul persoanei care a stat la tastatură.

## 2. Cum se scrie în acest Jurnal? (`AuditLogService.java`)
Ori de câte ori în Backend se salvează ceva critic, se apelează metoda `inregistreaza(...)` din `AuditLogService`. 

De exemplu, dacă Radu a scris un serviciu care șterge un Curs, la finalul acelui serviciu el a pus un apel către Jurnal:
*"Hei Jurnal, scrie că tocmai s-a șters Cursul 5, avea titlul X, iar valorile noi sunt GOL"*.

## 3. Cum asigurăm Securitatea Jurnalului? (Fără falsificări!)
Aici revine discuția de mai devreme! Cum știe `AuditLogService` cine a fost cu adevărat persoana care a șters cursul? De unde ia ID-ul de `utilizator`?

Dacă ne uităm în `AuditLogService.java`, vedem linia asta de cod:
```java
String utilizator = auditorProvider.getCurrentAuditor().orElse("system");
```

**Acesta este scutul de securitate:** 
- `auditorProvider` este o piesă din Backend care se uită **direct în memoria securizată a serverului** (Contextul de Securitate) pentru a vedea ce Token de logare are persoana respectivă.
- Asta înseamnă că Jurnalul **NU** ia numele utilizatorului din ceea ce îi trimite interfața web (Frontend-ul)! 
- Dacă un hacker încearcă să modifice din browser cererea și să zică *"Scrie în Jurnal că Adminul a făcut asta!"*, Backend-ul îl va ignora complet. Backend-ul se va uita la Token-ul hackerului și va scrie forțat în Jurnal: *"Hacker-ul (ID 99) a făcut asta!"*.
- Dacă acțiunea a fost făcută de aplicație automat, noaptea, fără ca un om să fie logat, se va trece utilizatorul ca fiind `"system"`.

## 4. De ce `AuditLog.java` nu folosea adnotarea `@Data`?
Când ai deschis fișierul, ai văzut că am eliminat din el adnotarea `@Data` (despre care spuneam că era un risc în Raportul de Calitate a Codului).
Adnotarea `@Data` obligă Spring să poată tipări conținutul întregului obiect în consolă sub formă de text (`toString()`). Deoarece `valori_vechi` și `valori_noi` conțin adesea absolut tot JSON-ul unui curs sau al unui profil de om (poate chiar cu număr de telefon sau email), dacă un programator dădea `print(auditLog)`, datele sensibile din GDPR s-ar fi scurs neintenționat în consolele serverului.

Fără `@Data`, entitatea Jurnalului își face treaba strict pentru a trimite datele către Frontend (pentru afișarea unui ecran cu Istoric), fără să riște scurgerea lor prin fișierele de log ale serverului!

---

**Pe scurt:** Tabela de Jurnalizare (Audit Log) este o uneltă strictă care prinde absolut toate modificările de date și folosește Securitatea Centralizată a Backend-ului pentru a se asigura că nimeni nu poate ascunde cine a modificat un fișier!
