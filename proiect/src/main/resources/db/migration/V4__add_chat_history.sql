CREATE TABLE conversatii (
    id BIGSERIAL PRIMARY KEY,
    id_user BIGINT NOT NULL REFERENCES app_user(id),
    id_curs BIGINT NOT NULL REFERENCES cursuri(id),
    titlu VARCHAR(150),
    activ BOOLEAN NOT NULL DEFAULT TRUE,
    created_by VARCHAR(36),
    created_at TIMESTAMPTZ NOT NULL,
    updated_by VARCHAR(36),
    updated_at TIMESTAMPTZ
);
CREATE INDEX idx_conversatii_user_curs ON conversatii(id_user, id_curs);

CREATE TABLE mesaje_chat (
    id BIGSERIAL PRIMARY KEY,
    id_conversatie BIGINT NOT NULL REFERENCES conversatii(id),
    rol VARCHAR(20) NOT NULL,
    continut TEXT NOT NULL,
    surse_folosite TEXT,
    created_by VARCHAR(36),
    created_at TIMESTAMPTZ NOT NULL,
    updated_by VARCHAR(36),
    updated_at TIMESTAMPTZ
);
CREATE INDEX idx_mesaje_chat_conversatie_created ON mesaje_chat(id_conversatie, created_at DESC);
