CREATE TABLE audit_log (
    id BIGSERIAL PRIMARY KEY,
    nume_tabel VARCHAR(50) NOT NULL,
    id_inregistrare BIGINT NOT NULL,
    operatie VARCHAR(30) NOT NULL,
    utilizator VARCHAR(36),
    valori_vechi JSONB,
    valori_noi JSONB,
    creat_la TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_audit_log_tabel_inregistrare ON audit_log (nume_tabel, id_inregistrare);
CREATE INDEX idx_audit_log_creat_la ON audit_log (creat_la DESC);
CREATE INDEX idx_audit_log_utilizator ON audit_log (utilizator);
