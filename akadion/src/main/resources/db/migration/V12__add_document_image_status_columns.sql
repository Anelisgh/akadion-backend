-- Contorizeaza rezultatul job-ului async de captioning imagini (embedder_service),
-- primit prin callback-ul PATCH /api/rag/documents/image-status.
ALTER TABLE documente ADD COLUMN imagini_indexate INTEGER;
ALTER TABLE documente ADD COLUMN imagini_esuate INTEGER;
