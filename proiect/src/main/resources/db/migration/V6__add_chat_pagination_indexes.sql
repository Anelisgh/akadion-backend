CREATE INDEX idx_conversatii_user_updated ON conversatii(id_user, updated_at DESC);
CREATE INDEX idx_conversatii_user_curs_updated ON conversatii(id_user, id_curs, updated_at DESC);
CREATE INDEX idx_mesaje_chat_conversatie_id ON mesaje_chat(id_conversatie, id DESC);
