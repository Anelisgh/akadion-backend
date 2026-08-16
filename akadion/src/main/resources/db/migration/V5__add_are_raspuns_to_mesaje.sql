ALTER TABLE mesaje_chat ADD COLUMN are_raspuns BOOLEAN NOT NULL DEFAULT FALSE;

-- Mesajele ASISTENT nu au nevoie de flag de eroare -> tratează-le ca "răspuns" implicit
UPDATE mesaje_chat SET are_raspuns = TRUE WHERE rol = 'ASISTENT';

-- Mesajele UTILIZATOR care AU deja un răspuns după ele in aceeași conversație -> marchează-le true
UPDATE mesaje_chat m
SET are_raspuns = TRUE
WHERE m.rol = 'UTILIZATOR'
  AND EXISTS (
      SELECT 1 FROM mesaje_chat r
      WHERE r.id_conversatie = m.id_conversatie
        AND r.rol = 'ASISTENT'
        AND r.created_at > m.created_at
  );
