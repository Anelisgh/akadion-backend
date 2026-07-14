# Frontend Auth Implementation

## Etapa 6 — 🤖 AGENT: Frontend React (Vite)

### 6.0 `frontend/vite.config.js` — Proxy Vite
Configurarea proxy-ului în Vite pentru a redirecționa apelurile API și paginile de autentificare OAuth2 către backend, evitând problemele de CORS în dezvoltare locală:
```javascript
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8081',
        changeOrigin: true,
      },
      '/oauth2': {
        target: 'http://localhost:8081',
        changeOrigin: true,
      },
      '/login': {
        target: 'http://localhost:8081',
        changeOrigin: true,
      },
      '/logout': {
        target: 'http://localhost:8081',
        changeOrigin: true,
      },
    },
  },
});
```

### 6.1 `frontend/src/api/axiosInstance.js` — Instanță Axios configurată pentru CSRF
Vom folosi o instanță Axios configurată special pentru a transmite corect cookie-urile de sesiune și token-ul CSRF:
```javascript
import axios from 'axios';

const axiosInstance = axios.create({
  baseURL: '',
  withCredentials: true, // Trimite automat session cookie-urile BFF
  xsrfCookieName: 'XSRF-TOKEN', // Numele cookie-ului scris de Spring Boot
  xsrfHeaderName: 'X-XSRF-TOKEN', // Numele header-ului asteptat de Spring Boot
});

export default axiosInstance;
```

### 6.2 `frontend/src/context/AuthContext.jsx`
La montare, `GET /api/auth/me`. Expune și `stareCont` din răspuns (nu doar `user`/`loading`), ca rutele să știe ce pagină să arate.

### 6.3 `frontend/src/pages/RegisterPage.jsx`
⚠️ **Nu mai are formular** — devine o pagină simplă, descrierea aplicației (textul cald despre alegerea liberă a cursurilor) + un buton "Creează cont", care face:
```js
window.location.href = '/oauth2/authorization/keycloak-register';
```
(URL relativ, proxy-ul Vite îl trimite spre backend; navigare completă de browser, nu fetch — la fel ca la login)

### 6.4 `frontend/src/pages/LoginPage.jsx` — Pagina de Login
Butonul de login redirectează browser-ul la nivel global către backend pentru a iniția fluxul Authorization Code OAuth2:
```javascript
import React from 'react';

const LoginPage = () => {
  const handleLogin = () => {
    // Redirecționare completă către BFF Endpoint
    window.location.href = '/oauth2/authorization/keycloak';
  };

  return (
    <div className="login-container">
      <h2>Conectare</h2>
      <button onClick={handleLogin}>Autentificare cu Keycloak</button>
    </div>
  );
};

export default LoginPage;
```

### 6.5 `frontend/src/pages/CompleteProfilePage.jsx` (nou)
Formular: `nume`, `prenume`, `facultate`, `rolDorit`. La submit: `POST /api/auth/complete-profile`. Succes → redirect către `/asteptare-aprobare`.

> [!IMPORTANT]
> **Tratarea Erorilor de Validare (REST API):**
> Dacă utilizatorul introduce date invalide sau trimite formularul gol, backend-ul va returna **HTTP 400 Bad Request** cu un obiect JSON ce conține erorile specifice fiecărui input.
> 
> React trebuie să citească obiectul `campuri` din răspuns (disponibil în `error.response.data.campuri`) și să afișeze mesajele de eroare sub fiecare câmp în parte.
> 
> *Exemplu de tratare a erorii în React:*
> ```javascript
> const [validationErrors, setValidationErrors] = useState({});
> 
> const handleSubmit = async (e) => {
>   e.preventDefault();
>   try {
>     await axiosInstance.post('/api/auth/complete-profile', formData);
>     navigate('/asteptare-aprobare');
>   } catch (error) {
>     if (error.response && error.response.status === 400 && error.response.data.campuri) {
>       // Salvăm erorile pe câmpuri ca să le afișăm sub input-uri
>       setValidationErrors(error.response.data.campuri);
>     } else {
>       alert("Eroare la salvare: " + (error.response?.data?.eroare || error.message));
>     }
>   }
> };
> ```
> *(Pentru detalii complete despre formatul JSON al erorilor returnate de backend, vezi secțiunea **Contractul de Erori REST API (Exception Handling)** din `auth_backend_keycloak.md`)*

### 6.6 Pagini de stare (noi, simple)
- `AsteptareAprobarePage.jsx` — mesaj "cererea ta e în curs de analiză"
- `CerereRespinsaPage.jsx` — mesaj + buton "Editează și retrimite" → duce înapoi la `CompleteProfilePage` (pre-completată dacă vreți, opțional)
- `ContDezactivatPage.jsx` — mesaj, fără acțiune posibilă

### 6.7 `frontend/src/components/ProtectedRoute.jsx`
Actualizat: verifică nu doar `user !== null`, ci și `stareCont === 'ACTIV'` pentru rutele aplicației normale; redirecționează altfel către pagina corespunzătoare stării (vezi §3.1 pentru maparea completă).

✅ **Verificare Etapa 6**: flux complet de la zero — `/register` → Keycloak (temă custom) → `/complete-profile` → `/asteptare-aprobare` → (admin acceptă) → login din nou → aplicația normală.

---

## Etapa 7 — 🤖 AGENT: Logout + panou admin

### 7.1 Componenta Logout
Deoarece `/logout` este protejat prin CSRF în Spring Security, delogarea nu se poate face prin simplă accesare a unui link de tip `GET`. Trebuie făcut un request de tip `POST` care să conțină header-ul CSRF:
```javascript
import React from 'react';
import axiosInstance from '../api/axiosInstance';

const LogoutButton = () => {
  const handleLogout = async () => {
    try {
      // POST către BFF logout endpoint. Axios va include automat X-XSRF-TOKEN
      await axiosInstance.post('/logout');
      // Redirecționare la login
      window.location.href = '/login';
    } catch (error) {
      console.error('Eroare la logout:', error);
    }
  };

  return <button onClick={handleLogout}>Deconectare</button>;
};
```

### 7.2 Panou Admin: Lista Cereri (`AdminPendingPage.jsx`)
Actualizăm codul paginii pentru a folosi proprietatea directă `nrRespingeri` primită din DTO:

> [!TIP]
> **Filtrare și extragere utilizatori (Backend + Frontend):**
> Endpoint-ul `/api/admin/users` suportă acum parametrul de query `stare` pentru a aduce exact datele de care ai nevoie:
> * `GET /api/admin/users?stare=ALL` -> Returnează absolut TOȚI utilizatorii din bază.
> * `GET /api/admin/users?stare=ACTIV` -> Doar utilizatorii activați.
> * `GET /api/admin/users?stare=PENDING` -> Doar cei în așteptare de aprobare.
> * `GET /api/admin/users?stare=INACTIV` -> Cei dezactivați.
> * `GET /api/admin/users?stare=RESPINS` -> Cei refuzați.
> 
> Fiecare obiect de tip utilizator returnat de backend conține acum și câmpul `stare` (ex: `stare: "ACTIV"`). 
> Căutările după nume, e-mail sau facultate se pot face foarte ușor direct în frontend, filtrând în timp real (in-memory) lista descărcată din backend!

Exemplu de cod React pentru afișare și filtrare în funcție de starea selectată:

```javascript
import React, { useEffect, useState } from 'react';
import axiosInstance from '../api/axiosInstance';

const AdminPendingPage = () => {
  const [utilizatori, setUtilizatori] = useState([]);
  const [filtruStare, setFiltruStare] = useState('PENDING'); // PENDING implicit, poate fi schimbat de admin la ALL, ACTIV, etc.

  useEffect(() => {
    // Apelăm API-ul trimițând starea selectată (PENDING, ACTIV, INACTIV, RESPINS, ALL)
    axiosInstance.get(`/api/admin/users?stare=${filtruStare}`)
      .then(res => setUtilizatori(res.data))
      .catch(err => console.error(err));
  }, [filtruStare]); // Se re-apelează când adminul schimbă filtrul din interfață


  const [cautare, setCautare] = useState('');

  const handleAction = async (id, action) => {
    try {
      await axiosInstance.post(`/api/admin/users/${id}/${action}`);
      // După acțiune, reîncărcăm lista ca să avem stările actualizate din DB
      const res = await axiosInstance.get(`/api/admin/users?stare=${filtruStare}`);
      setUtilizatori(res.data);
    } catch (err) {
      alert('Eroare la procesarea acțiunii: ' + err.message);
    }
  };

  // Filtrare suplimentară în frontend (in-memory) după Nume/Prenume/Email/Facultate
  const utilizatoriFiltrati = utilizatori.filter(user => {
    const textCautat = cautare.toLowerCase();
    return (
      user.nume?.toLowerCase().includes(textCautat) ||
      user.prenume?.toLowerCase().includes(textCautat) ||
      user.mail?.toLowerCase().includes(textCautat) ||
      user.facultate?.toLowerCase().includes(textCautat)
    );
  });

  return (
    <div style={{ padding: '20px' }}>
      <h2>Administrare Utilizatori</h2>

      {/* Controlere de Filtrare și Căutare */}
      <div style={{ display: 'flex', gap: '20px', marginBottom: '20px' }}>
        <div>
          <label>Filtrează după Stare: </label>
          <select value={filtruStare} onChange={e => setFiltruStare(e.target.value)}>
            <option value="ALL">Toți utilizatorii</option>
            <option value="PENDING">În așteptare (Pending)</option>
            <option value="ACTIV">Activi</option>
            <option value="INACTIV">Dezactivați (Inactivi)</option>
            <option value="RESPINS">Respinși</option>
          </select>
        </div>

        <div>
          <label>Caută după Nume/Email: </label>
          <input 
            type="text" 
            placeholder="Introduceți text..." 
            value={cautare} 
            onChange={e => setCautare(e.target.value)} 
          />
        </div>
      </div>

      {/* Tabel Date */}
      <table border="1" cellPadding="10" style={{ width: '100%', borderCollapse: 'collapse' }}>
        <thead>
          <tr style={{ backgroundColor: '#f2f2f2' }}>
            <th>Nume Complet</th>
            <th>Email</th>
            <th>Facultate</th>
            <th>Rol</th>
            <th>Stare Cont</th>
            <th>Respingeri anterioare</th>
            <th>Acțiuni</th>
          </tr>
        </thead>
        <tbody>
          {utilizatoriFiltrati.map(user => (
            <tr key={user.id}>
              <td>{user.prenume} {user.nume}</td>
              <td>{user.mail}</td>
              <td>{user.facultate || '-'}</td>
              <td>{user.rolDorit || '-'}</td>
              <td>
                <span style={{ 
                  fontWeight: 'bold', 
                  color: user.stare === 'ACTIV' ? 'green' : user.stare === 'PENDING' ? 'orange' : 'red' 
                }}>
                  {user.stare}
                </span>
              </td>
              <td>{user.nrRespingeriAnterioare}</td>
              <td>
                {/* Afișăm butoane diferite în funcție de starea utilizatorului curent */}
                {user.stare === 'PENDING' && (
                  <>
                    <button onClick={() => handleAction(user.id, 'accept')} style={{ marginRight: '5px' }}>Acceptă</button>
                    <button onClick={() => handleAction(user.id, 'reject')} style={{ backgroundColor: '#ffcccc' }}>Respinge</button>
                  </>
                )}
                {user.stare === 'ACTIV' && (
                  <button onClick={() => handleAction(user.id, 'deactivate')} style={{ backgroundColor: '#ff9999' }}>Dezactivează</button>
                )}
                {user.stare === 'INACTIV' && (
                  <button onClick={() => handleAction(user.id, 'activate')} style={{ backgroundColor: '#ccffcc' }}>Activează la loc</button>
                )}
                {user.stare === 'RESPINS' && (
                  <span style={{ fontSize: '12px', color: 'gray' }}>Așteaptă corectarea datelor</span>
                )}
                {user.stare === 'INCOMPLET' && (
                  <span style={{ fontSize: '12px', color: 'gray' }}>Profil necompletat încă</span>
                )}
              </td>
            </tr>
          ))}
          {utilizatoriFiltrati.length === 0 && (
            <tr>
              <td colSpan="7" style={{ textAlign: 'center' }}>Nu s-au găsit utilizatori.</td>
            </tr>
          )}
        </tbody>
      </table>
    </div>
  );
};
```

---
