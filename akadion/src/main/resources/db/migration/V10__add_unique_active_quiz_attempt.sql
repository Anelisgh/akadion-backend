-- Curățăm încercările GENERATA duplicate rămase din date mai vechi (dinainte de introducerea
-- acestei constrângeri) — păstrăm doar cea mai recentă per student+curs, ca indexul unic de mai
-- jos să poată fi creat. Sunt încercări abandonate, niciodată finalizate; FINALIZATA nu e atinsă.
DELETE FROM incercari_quiz
WHERE status = 'GENERATA'
  AND id NOT IN (
      SELECT DISTINCT ON (id_student, id_curs) id
      FROM incercari_quiz
      WHERE status = 'GENERATA'
      ORDER BY id_student, id_curs, created_at DESC
  );

-- O singură încercare de quiz GENERATA (nefinalizată) per student+curs la un moment dat,
-- ca să prevenim dublarea accidentală (dublu-click/retry) care ar declanșa 2 apeluri RAG.
-- Încercările FINALIZATA nu sunt afectate de acest index (partial index).
CREATE UNIQUE INDEX uk_incercari_quiz_activa_per_student_curs
    ON incercari_quiz (id_student, id_curs)
    WHERE status = 'GENERATA';
