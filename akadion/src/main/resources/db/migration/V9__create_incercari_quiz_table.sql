CREATE TABLE incercari_quiz (
    id BIGSERIAL PRIMARY KEY,
    id_student BIGINT NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    id_curs BIGINT NOT NULL REFERENCES cursuri(id) ON DELETE CASCADE,
    id_document BIGINT REFERENCES documente(id) ON DELETE SET NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('GENERATA', 'FINALIZATA')),
    nr_intrebari INT NOT NULL,
    scor INT,
    detalii_json JSONB NOT NULL,
    created_by VARCHAR(36),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by VARCHAR(36),
    updated_at TIMESTAMPTZ
);

CREATE INDEX idx_incercari_quiz_student_status ON incercari_quiz (id_student, status, created_at DESC);
CREATE INDEX idx_incercari_quiz_curs_status ON incercari_quiz (id_curs, status, created_at DESC);
