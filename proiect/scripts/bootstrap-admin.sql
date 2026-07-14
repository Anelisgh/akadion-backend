-- bootstrap-admin.sql
-- Rulează O SINGURĂ DATĂ, după Etapa 0 (creat admin în Keycloak) și Etapa 1 (tabele + seed ROL/STARE_CONT).
-- Înlocuiește <UUID_KEYCLOAK> și <EMAIL_ADMIN> cu valorile tale.
-- NU comite acest fișier cu valori reale completate — păstrează șablonul cu placeholder-uri.
--
-- Exemplu rulare:
--   psql -d akadion -f bootstrap-admin.sql
--
-- Verifică înainte că seed-ul a rulat (trebuie să existe rânduri în 'roluri' și 'stari_cont').

INSERT INTO app_user (id_keycloak, id_stare_cont, id_rol, nume, prenume, mail, facultate)
SELECT '<UUID_KEYCLOAK>',
       (SELECT id FROM stari_cont WHERE denumire = 'ACTIV'),
       (SELECT id FROM roluri WHERE denumire = 'ADMIN'),
       'Admin', 'Principal', '<EMAIL_ADMIN>', NULL
WHERE NOT EXISTS (SELECT 1 FROM app_user WHERE mail = '<EMAIL_ADMIN>');
